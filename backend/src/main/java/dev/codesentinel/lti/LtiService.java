package dev.codesentinel.lti;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codesentinel.auth.JwtService;
import dev.codesentinel.auth.Principal;
import dev.codesentinel.auth.UserRepository;
import dev.codesentinel.common.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LtiService {

    public static final String CLAIM_MESSAGE_TYPE = "https://purl.imsglobal.org/spec/lti/claim/message_type";
    public static final String CLAIM_VERSION = "https://purl.imsglobal.org/spec/lti/claim/version";
    public static final String CLAIM_DEPLOYMENT_ID = "https://purl.imsglobal.org/spec/lti/claim/deployment_id";
    public static final String CLAIM_ROLES = "https://purl.imsglobal.org/spec/lti/claim/roles";
    public static final String CLAIM_CONTEXT = "https://purl.imsglobal.org/spec/lti/claim/context";
    public static final String CLAIM_RESOURCE_LINK = "https://purl.imsglobal.org/spec/lti/claim/resource_link";
    public static final String CLAIM_AGS_ENDPOINT = "https://purl.imsglobal.org/spec/lti-ags/claim/endpoint";

    private final LtiPlatformRepository platformRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Cache of platform public keys (platformId:kid -> PublicKey)
    private final Map<String, PublicKey> keyCache = new ConcurrentHashMap<>();

    public LtiService(LtiPlatformRepository platformRepository, UserRepository userRepository,
                      JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.platformRepository = platformRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * OIDC Login Initiation (1EdTech LTI 1.3 Core).
     * Builds the authorization redirect URL with state and nonce.
     */
    public String buildLoginRedirectUrl(String issuer, String loginHint, String targetLinkUri,
                                         String ltiMessageHint, String clientId, String callbackUrl) {
        if (issuer == null || issuer.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Missing required 'iss' (issuer) parameter.");
        }
        LtiPlatform platform = platformRepository.findByIssuer(issuer.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Unregistered LTI Platform: " + issuer));

        String state = UUID.randomUUID().toString();
        String nonce = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");

        // Save nonce with 15-minute TTL to guarantee replay protection
        platformRepository.saveNonce(nonce, state, Instant.now().plus(Duration.ofMinutes(15)));

        String redirectUri = callbackUrl != null && !callbackUrl.isBlank() ? callbackUrl : targetLinkUri;

        StringBuilder sb = new StringBuilder(platform.authUrl());
        sb.append(platform.authUrl().contains("?") ? "&" : "?")
                .append("response_type=id_token")
                .append("&scope=openid")
                .append("&response_mode=form_post")
                .append("&client_id=").append(urlEncode(platform.clientId()))
                .append("&redirect_uri=").append(urlEncode(redirectUri))
                .append("&login_hint=").append(urlEncode(loginHint != null ? loginHint : ""))
                .append("&state=").append(urlEncode(state))
                .append("&nonce=").append(urlEncode(nonce))
                .append("&prompt=none");

        if (ltiMessageHint != null && !ltiMessageHint.isBlank()) {
            sb.append("&lti_message_hint=").append(urlEncode(ltiMessageHint));
        }

        return sb.toString();
    }

    public record LaunchResult(
            String caliperToken,
            Principal principal,
            LtiPlatform platform,
            String targetPath,
            Optional<Long> courseId,
            Optional<Long> assignmentId
    ) {}

    /**
     * Handles LTI 1.3 Resource Link Launch message.
     * Validates cryptographic signature, state/nonce replay protection, and maps LMS identity to Caliper.
     */
    public LaunchResult handleLaunch(String idToken, String state) {
        if (idToken == null || idToken.isBlank() || state == null || state.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Missing required 'id_token' or 'state' parameter.");
        }

        // 1. Parse unverified token header and claims to extract issuer and key ID
        String[] parts = idToken.split("\\.");
        if (parts.length != 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Malformed JWT structure in LTI launch.");
        }

        JsonNode headerNode;
        JsonNode claimsNode;
        try {
            headerNode = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
            claimsNode = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot parse LTI JWT payload: " + e.getMessage());
        }

        String issuer = claimsNode.path("iss").asText("");
        String nonce = claimsNode.path("nonce").asText("");
        String kid = headerNode.path("kid").asText("");

        if (issuer.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Missing 'iss' claim in LTI token.");
        }
        if (nonce.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Missing 'nonce' claim in LTI token.");
        }

        // 2. Replay protection: Atomically validate and consume nonce
        boolean nonceValid = platformRepository.validateAndConsumeNonce(nonce, state);
        if (!nonceValid) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid, expired, or previously consumed LTI state/nonce. Replay attack prevented.");
        }

        // 3. Platform lookup
        LtiPlatform platform = platformRepository.findByIssuer(issuer)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unregistered LTI Platform: " + issuer));

        // 4. Resolve platform public key & verify signature
        PublicKey publicKey = resolvePlatformKey(platform, kid);
        Claims verifiedClaims;
        try {
            verifiedClaims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(idToken)
                    .getPayload();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Cryptographic verification of LTI launch token failed: " + e.getMessage());
        }

        // 5. Validate LTI Advantage core claims
        validateCoreClaims(verifiedClaims, platform);

        // 6. User provisioning and role mapping
        Principal principal = resolveUser(verifiedClaims, platform);
        String sessionToken = jwtService.issue(principal);

        // 7. Context & Resource Link mapping (Course & Assignment)
        Optional<Long> courseId = mapCourseContext(verifiedClaims, platform);
        Optional<Long> assignmentId = mapResourceLink(verifiedClaims, platform);

        String targetPath = "/";
        if (assignmentId.isPresent()) {
            targetPath = "/assignments/" + assignmentId.get() + (principal.instructor() ? "" : "/code");
        } else if (courseId.isPresent()) {
            targetPath = "/courses/" + courseId.get();
        }

        return new LaunchResult(sessionToken, principal, platform, targetPath, courseId, assignmentId);
    }

    private void validateCoreClaims(Claims claims, LtiPlatform platform) {
        // Audience validation
        boolean audOk = false;
        Set<String> audiences = claims.getAudience();
        if (audiences != null && audiences.contains(platform.clientId())) {
            audOk = true;
        } else {
            Object aud = claims.get("aud");
            if (aud instanceof String s && s.equals(platform.clientId())) audOk = true;
            else if (aud instanceof Collection<?> col && col.contains(platform.clientId())) audOk = true;
        }

        if (!audOk) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Token audience does not match configured client_id: " + platform.clientId());
        }

        // Message type
        String msgType = claims.get(CLAIM_MESSAGE_TYPE, String.class);
        if (!"LtiResourceLinkRequest".equals(msgType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported LTI message type: " + msgType);
        }

        // Version
        String version = claims.get(CLAIM_VERSION, String.class);
        if (!"1.3.0".equals(version)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported LTI version: " + version);
        }

        // Deployment ID
        if (platform.deploymentId() != null && !platform.deploymentId().isBlank()) {
            String dep = claims.get(CLAIM_DEPLOYMENT_ID, String.class);
            if (!platform.deploymentId().equals(dep)) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "LTI deployment ID mismatch: expected " + platform.deploymentId() + ", got " + dep);
            }
        }
    }

    private Principal resolveUser(Claims claims, LtiPlatform platform) {
        String sub = claims.getSubject();
        String email = claims.get("email", String.class);
        String name = claims.get("name", String.class);

        if (email == null || email.isBlank()) {
            email = "lti-" + sub + "@" + URI.create(platform.issuer()).getHost();
        }
        if (name == null || name.isBlank()) {
            name = "LMS User " + sub;
        }

        // Map roles
        List<?> rolesList = claims.get(CLAIM_ROLES, List.class);
        boolean isInstructor = false;
        if (rolesList != null) {
            for (Object r : rolesList) {
                String roleStr = String.valueOf(r);
                if (roleStr.endsWith("Instructor") || roleStr.endsWith("Administrator") || roleStr.contains("#Instructor") || roleStr.contains("#Administrator")) {
                    isInstructor = true;
                    break;
                }
            }
        }
        String appRole = isInstructor ? "INSTRUCTOR" : "STUDENT";

        // Find or provision user
        Optional<UserRepository.UserRow> existing = userRepository.findByEmail(email);
        long userId;
        if (existing.isPresent()) {
            userId = existing.get().id();
            // Update role if promoted in LMS
            appRole = existing.get().role();
        } else {
            String randomPassword = passwordEncoder.encode(UUID.randomUUID().toString());
            userId = userRepository.insert(email, name, randomPassword, appRole, false);
        }

        return new Principal(userId, email, name, appRole);
    }

    private Optional<Long> mapCourseContext(Claims claims, LtiPlatform platform) {
        Map<?, ?> ctx = claims.get(CLAIM_CONTEXT, Map.class);
        if (ctx == null || ctx.get("id") == null) return Optional.empty();
        String contextId = String.valueOf(ctx.get("id"));
        return platformRepository.findMappedCourse(platform.id(), contextId);
    }

    private Optional<Long> mapResourceLink(Claims claims, LtiPlatform platform) {
        Map<?, ?> rl = claims.get(CLAIM_RESOURCE_LINK, Map.class);
        if (rl == null || rl.get("id") == null) return Optional.empty();
        String resourceLinkId = String.valueOf(rl.get("id"));

        // Extract AGS line item url if present
        String lineitemUrl = null;
        Map<?, ?> ags = claims.get(CLAIM_AGS_ENDPOINT, Map.class);
        if (ags != null && ags.get("lineitem") != null) {
            lineitemUrl = String.valueOf(ags.get("lineitem"));
        }

        Optional<Long> asgId = platformRepository.findMappedAssignment(platform.id(), resourceLinkId);
        if (asgId.isPresent() && lineitemUrl != null) {
            platformRepository.mapResourceLink(platform.id(), resourceLinkId, asgId.get(), lineitemUrl);
        }
        return asgId;
    }

    public void registerTestPublicKey(LtiPlatform platform, String kid, PublicKey key) {
        keyCache.put(platform.id() + ":" + kid, key);
    }

    private PublicKey resolvePlatformKey(LtiPlatform platform, String kid) {
        String cacheKey = platform.id() + ":" + kid;
        PublicKey cached = keyCache.get(cacheKey);
        if (cached != null) return cached;

        // Fetch JWKS from platform.jwksUrl()
        try (InputStream is = URI.create(platform.jwksUrl()).toURL().openStream()) {
            JsonNode jwks = objectMapper.readTree(is);
            JsonNode keys = jwks.path("keys");
            for (JsonNode k : keys) {
                if (k.path("kid").asText("").equals(kid) || keys.size() == 1) {
                    String nStr = k.path("n").asText();
                    String eStr = k.path("e").asText();
                    BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(nStr));
                    BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(eStr));
                    KeyFactory kf = KeyFactory.getInstance("RSA");
                    PublicKey pub = kf.generatePublic(new RSAPublicKeySpec(modulus, exponent));
                    keyCache.put(cacheKey, pub);
                    return pub;
                }
            }
        } catch (Exception e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unable to retrieve platform JWKS from " + platform.jwksUrl() + ": " + e.getMessage());
        }

        throw new ApiException(HttpStatus.UNAUTHORIZED, "Key ID '" + kid + "' not found in platform JWKS.");
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
