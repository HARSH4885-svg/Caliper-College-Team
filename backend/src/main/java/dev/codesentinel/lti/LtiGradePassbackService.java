package dev.codesentinel.lti;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * 1EdTech LTI 1.3 Advantage - Assignment and Grade Services (AGS) Grade Passback.
 * Authenticates via OAuth 2.0 client_credentials grant using private_key_jwt client assertion,
 * and submits standardized grade/score payloads to the LMS line item endpoint.
 */
@Service
public class LtiGradePassbackService {

    private static final Logger log = LoggerFactory.getLogger(LtiGradePassbackService.class);
    public static final String SCOPE_SCORE = "https://purl.imsglobal.org/spec/lti-ags/scope/score";

    private final LtiPlatformRepository platformRepository;
    private final LtiKeyService keyService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    public LtiGradePassbackService(LtiPlatformRepository platformRepository, LtiKeyService keyService) {
        this.platformRepository = platformRepository;
        this.keyService = keyService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public record PassbackResult(
            boolean success,
            int statusCode,
            String message
    ) {}

    /**
     * Publishes a grade for a submission to the LMS via LTI-AGS.
     */
    public PassbackResult publishGrade(long platformId, String lineitemUrl, String lmsUserId,
                                       double scoreGiven, double scoreMaximum, String comment) {
        LtiPlatform platform = platformRepository.findById(platformId)
                .orElse(null);
        if (platform == null) {
            return new PassbackResult(false, 404, "Platform not found: " + platformId);
        }

        try {
            // 1. Obtain OAuth 2.0 Bearer Token via client_credentials & private_key_jwt
            String accessToken = obtainAccessToken(platform);

            // 2. Prepare score publish URL (if lineitemUrl is "https://canvas.example.com/api/lti/courses/1/line_items/2",
            // scores endpoint is lineitemUrl + "/scores" unless already ending in "/scores")
            String scoresUrl = lineitemUrl.endsWith("/scores") ? lineitemUrl : lineitemUrl + "/scores";

            // 3. Construct LTI 1.3 AGS Score payload
            Map<String, Object> payload = Map.of(
                    "userId", lmsUserId,
                    "scoreGiven", scoreGiven,
                    "scoreMaximum", scoreMaximum,
                    "comment", comment != null ? comment : "",
                    "activityProgress", "Completed",
                    "gradingProgress", "FullyGraded",
                    "timestamp", Instant.now().toString()
            );
            String jsonBody = objectMapper.writeValueAsString(payload);

            // 4. POST to LMS AGS endpoint
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(scoresUrl))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/vnd.ims.lis.v1.score+json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                log.info("LTI AGS Grade passback succeeded for user {} on lineitem {}: HTTP {}", lmsUserId, scoresUrl, resp.statusCode());
                return new PassbackResult(true, resp.statusCode(), "Grade published successfully.");
            } else {
                log.warn("LTI AGS Grade passback failed: HTTP {} - {}", resp.statusCode(), resp.body());
                return new PassbackResult(false, resp.statusCode(), "LMS returned HTTP " + resp.statusCode() + ": " + resp.body());
            }
        } catch (Exception e) {
            log.error("Failed to publish LTI grade passback to {}", lineitemUrl, e);
            return new PassbackResult(false, 500, "Error publishing grade: " + e.getMessage());
        }
    }

    /**
     * Builds and signs client_assertion JWT, then exchanges it for an OAuth2 bearer token.
     */
    public String obtainAccessToken(LtiPlatform platform) throws Exception {
        Instant now = Instant.now();
        String clientAssertion = Jwts.builder()
                .header()
                .keyId(LtiKeyService.KEY_ID)
                .and()
                .issuer(platform.clientId())
                .subject(platform.clientId())
                .audience().add(platform.tokenUrl()).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(5))))
                .id(UUID.randomUUID().toString())
                .signWith(keyService.getPrivateKey())
                .compact();

        String body = "grant_type=" + URLEncoder.encode("client_credentials", StandardCharsets.UTF_8)
                + "&client_assertion_type=" + URLEncoder.encode("urn:ietf:params:oauth:client-assertion-type:jwt-bearer", StandardCharsets.UTF_8)
                + "&client_assertion=" + URLEncoder.encode(clientAssertion, StandardCharsets.UTF_8)
                + "&scope=" + URLEncoder.encode(SCOPE_SCORE, StandardCharsets.UTF_8);

        HttpRequest tokenReq = HttpRequest.newBuilder()
                .uri(URI.create(platform.tokenUrl()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> tokenResp = httpClient.send(tokenReq, HttpResponse.BodyHandlers.ofString());
        if (tokenResp.statusCode() < 200 || tokenResp.statusCode() >= 300) {
            throw new IllegalStateException("LMS token endpoint returned " + tokenResp.statusCode() + ": " + tokenResp.body());
        }

        JsonNode json = objectMapper.readTree(tokenResp.body());
        JsonNode accessTokenNode = json.path("access_token");
        if (accessTokenNode.isMissingNode() || accessTokenNode.asText().isBlank()) {
            throw new IllegalStateException("No access_token found in LMS response: " + tokenResp.body());
        }
        return accessTokenNode.asText();
    }
}
