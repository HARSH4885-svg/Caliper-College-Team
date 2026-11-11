package dev.codesentinel.lti;

import dev.codesentinel.auth.CurrentUser;
import dev.codesentinel.auth.Principal;
import dev.codesentinel.common.ApiException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lti")
public class LtiController {

    private final LtiService ltiService;
    private final LtiKeyService keyService;
    private final LtiPlatformRepository platformRepository;
    private final LtiGradePassbackService passbackService;

    public LtiController(LtiService ltiService, LtiKeyService keyService,
                         LtiPlatformRepository platformRepository, LtiGradePassbackService passbackService) {
        this.ltiService = ltiService;
        this.keyService = keyService;
        this.platformRepository = platformRepository;
        this.passbackService = passbackService;
    }

    /** Public JWKS endpoint exposing Caliper's RSA public keys for LMS verification. */
    @GetMapping("/jwks")
    public Map<String, Object> jwks() {
        return keyService.getJwks();
    }

    /** OIDC Login Initiation endpoint called by LMS via GET or POST. */
    @RequestMapping(value = "/login_initiation", method = {RequestMethod.GET, RequestMethod.POST})
    public void loginInitiation(
            @RequestParam(name = "iss") String issuer,
            @RequestParam(name = "login_hint", required = false) String loginHint,
            @RequestParam(name = "target_link_uri", required = false) String targetLinkUri,
            @RequestParam(name = "lti_message_hint", required = false) String ltiMessageHint,
            @RequestParam(name = "client_id", required = false) String clientId,
            HttpServletResponse response) throws IOException {

        String callbackUrl = (targetLinkUri != null && !targetLinkUri.isBlank())
                ? targetLinkUri
                : "/api/lti/launch";

        String redirectUrl = ltiService.buildLoginRedirectUrl(issuer, loginHint, targetLinkUri, ltiMessageHint, clientId, callbackUrl);
        response.sendRedirect(redirectUrl);
    }

    /** LTI 1.3 Launch endpoint called by LMS via HTTP POST form submission with id_token & state. */
    @PostMapping("/launch")
    public void launch(
            @RequestParam("id_token") String idToken,
            @RequestParam("state") String state,
            HttpServletResponse response) throws IOException {

        LtiService.LaunchResult result = ltiService.handleLaunch(idToken, state);

        // Redirect user into Caliper SPA with single-use session token and landing path
        String destination = "/#token=" + result.caliperToken() + "&redirect=" + result.targetPath();
        response.sendRedirect(destination);
    }

    /** Admin/Instructor API to list registered LTI 1.3 platforms (e.g. Canvas). */
    @GetMapping("/platforms")
    public List<LtiPlatform> listPlatforms() {
        CurrentUser.instructor();
        return platformRepository.findAll();
    }

    public record PlatformRequest(
            String name,
            String issuer,
            String clientId,
            String deploymentId,
            String authUrl,
            String tokenUrl,
            String jwksUrl
    ) {}

    /** Admin/Instructor API to register or update an LTI 1.3 platform. */
    @PostMapping("/platforms")
    public LtiPlatform registerPlatform(@RequestBody PlatformRequest req) {
        CurrentUser.instructor();
        if (req.name() == null || req.issuer() == null || req.clientId() == null ||
                req.authUrl() == null || req.tokenUrl() == null || req.jwksUrl() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Missing required LTI platform configuration fields.");
        }
        LtiPlatform platform = new LtiPlatform(
                null,
                req.name().trim(),
                req.issuer().trim(),
                req.clientId().trim(),
                req.deploymentId() != null ? req.deploymentId().trim() : "",
                req.authUrl().trim(),
                req.tokenUrl().trim(),
                req.jwksUrl().trim(),
                null
        );
        return platformRepository.save(platform);
    }

    public record CourseMappingRequest(String contextId, long courseId) {}

    @PostMapping("/platforms/{id}/mappings/course")
    public ResponseEntity<Void> mapCourse(@PathVariable long id, @RequestBody CourseMappingRequest req) {
        CurrentUser.instructor();
        platformRepository.mapCourse(id, req.contextId(), req.courseId());
        return ResponseEntity.noContent().build();
    }

    public record AssignmentMappingRequest(String resourceLinkId, long assignmentId, String lineitemUrl) {}

    @PostMapping("/platforms/{id}/mappings/assignment")
    public ResponseEntity<Void> mapAssignment(@PathVariable long id, @RequestBody AssignmentMappingRequest req) {
        CurrentUser.instructor();
        platformRepository.mapResourceLink(id, req.resourceLinkId(), req.assignmentId(), req.lineitemUrl());
        return ResponseEntity.noContent().build();
    }

    public record GradePassbackRequest(long platformId, String lineitemUrl, String lmsUserId, double scoreGiven, double scoreMaximum, String comment) {}

    @PostMapping("/grade_passback")
    public LtiGradePassbackService.PassbackResult manualGradePassback(@RequestBody GradePassbackRequest req) {
        CurrentUser.instructor();
        return passbackService.publishGrade(req.platformId(), req.lineitemUrl(), req.lmsUserId(), req.scoreGiven(), req.scoreMaximum(), req.comment());
    }
}
