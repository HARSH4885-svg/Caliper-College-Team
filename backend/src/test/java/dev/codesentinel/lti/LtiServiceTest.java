package dev.codesentinel.lti;

import dev.codesentinel.auth.JwtService;
import dev.codesentinel.auth.Principal;
import dev.codesentinel.auth.UserRepository;
import dev.codesentinel.common.ApiException;
import dev.codesentinel.common.AppProperties;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LtiServiceTest {

    private InMemoryPlatformRepository platformRepository;
    private InMemoryUserRepository userRepository;
    private JwtService jwtService;
    private LtiService ltiService;

    private KeyPair platformKeyPair;
    private LtiPlatform testPlatform;

    static class InMemoryPlatformRepository extends LtiPlatformRepository {
        Map<String, LtiPlatform> platformsByIssuer = new HashMap<>();
        Map<String, String> nonces = new HashMap<>(); // nonce -> state
        Map<String, Long> courseMappings = new HashMap<>(); // pid:ctx -> cid
        Map<String, Long> assignmentMappings = new HashMap<>(); // pid:rl -> aid

        public InMemoryPlatformRepository() {
            super(null);
        }

        @Override
        public Optional<LtiPlatform> findByIssuer(String issuer) {
            return Optional.ofNullable(platformsByIssuer.get(issuer));
        }

        @Override
        public void saveNonce(String nonce, String state, Instant expiresAt) {
            nonces.put(nonce, state);
        }

        @Override
        public boolean validateAndConsumeNonce(String nonce, String state) {
            String savedState = nonces.remove(nonce);
            return savedState != null && savedState.equals(state);
        }

        @Override
        public Optional<Long> findMappedCourse(long platformId, String contextId) {
            return Optional.ofNullable(courseMappings.get(platformId + ":" + contextId));
        }

        @Override
        public Optional<Long> findMappedAssignment(long platformId, String resourceLinkId) {
            return Optional.ofNullable(assignmentMappings.get(platformId + ":" + resourceLinkId));
        }

        @Override
        public void mapResourceLink(long platformId, String resourceLinkId, long assignmentId, String lineitemUrl) {
            assignmentMappings.put(platformId + ":" + resourceLinkId, assignmentId);
        }
    }

    static class InMemoryUserRepository extends UserRepository {
        Map<String, UserRow> usersByEmail = new HashMap<>();
        long nextId = 100L;

        public InMemoryUserRepository() {
            super(null);
        }

        @Override
        public Optional<UserRow> findByEmail(String email) {
            return Optional.ofNullable(usersByEmail.get(email.toLowerCase()));
        }

        @Override
        public long insert(String email, String name, String hash, String role, boolean synthetic) {
            long id = nextId++;
            usersByEmail.put(email.toLowerCase(), new UserRow(id, email, name, hash, role, true));
            return id;
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        platformRepository = new InMemoryPlatformRepository();
        userRepository = new InMemoryUserRepository();

        AppProperties.Jwt jwtProps = new AppProperties.Jwt("caliper-in-memory-test-secret-key-at-least-32-chars", 60);
        AppProperties props = new AppProperties(
                jwtProps,
                "http://localhost:5173",
                true, false, "", 30,
                new AppProperties.Storage("local", "./storage"),
                new AppProperties.Bootstrap("", ""),
                new AppProperties.Worker(false, 1500, 10, 2),
                new AppProperties.Plag(10, 5, 0.30, 0.80),
                new AppProperties.Judge("docker", "", "docker")
        );
        jwtService = new JwtService(props);

        ltiService = new LtiService(platformRepository, userRepository, jwtService, new BCryptPasswordEncoder());

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        platformKeyPair = kpg.generateKeyPair();

        testPlatform = new LtiPlatform(
                1L,
                "Canvas Production LMS",
                "https://canvas.instructure.com",
                "caliper-client-100",
                "deployment-xyz",
                "https://canvas.instructure.com/api/lti/authorize_redirect",
                "https://canvas.instructure.com/login/oauth2/token",
                "https://canvas.instructure.com/api/lti/security/jwks",
                Instant.now()
        );
        platformRepository.platformsByIssuer.put(testPlatform.issuer(), testPlatform);

        ltiService.registerTestPublicKey(testPlatform, "canvas-key-1", platformKeyPair.getPublic());
    }

    @Test
    @DisplayName("Builds standards-compliant OIDC login initiation redirect URL and persists nonce")
    void testBuildLoginRedirectUrl() {
        String redirect = ltiService.buildLoginRedirectUrl(
                "https://canvas.instructure.com",
                "user_999",
                "https://caliper.example.com/api/lti/launch",
                "hint_456",
                "caliper-client-100",
                null
        );

        assertNotNull(redirect);
        assertTrue(redirect.startsWith("https://canvas.instructure.com/api/lti/authorize_redirect?"));
        assertTrue(redirect.contains("response_type=id_token"));
        assertTrue(redirect.contains("scope=openid"));
        assertTrue(redirect.contains("response_mode=form_post"));
        assertTrue(redirect.contains("client_id=caliper-client-100"));
        assertTrue(redirect.contains("login_hint=user_999"));
        assertTrue(redirect.contains("lti_message_hint=hint_456"));
        assertTrue(redirect.contains("state="));
        assertTrue(redirect.contains("nonce="));

        assertEquals(1, platformRepository.nonces.size());
    }

    @Test
    @DisplayName("Successfully validates LTI 1.3 launch token, provisions student and resolves mappings")
    void testSuccessfulLaunchStudent() {
        platformRepository.nonces.put("nonce-valid-1", "state-valid-1");
        platformRepository.courseMappings.put("1:canvas-course-101", 15L);
        platformRepository.assignmentMappings.put("1:canvas-link-202", 77L);

        userRepository.usersByEmail.put("student@university.edu", new UserRepository.UserRow(
                40L, "student@university.edu", "Student Alice", "pass", "STUDENT", true
        ));

        String jwt = Jwts.builder()
                .header().keyId("canvas-key-1").and()
                .issuer("https://canvas.instructure.com")
                .audience().add("caliper-client-100").and()
                .subject("canvas-sub-42")
                .claim("email", "student@university.edu")
                .claim("name", "Student Alice")
                .claim("nonce", "nonce-valid-1")
                .claim(LtiService.CLAIM_MESSAGE_TYPE, "LtiResourceLinkRequest")
                .claim(LtiService.CLAIM_VERSION, "1.3.0")
                .claim(LtiService.CLAIM_DEPLOYMENT_ID, "deployment-xyz")
                .claim(LtiService.CLAIM_ROLES, List.of("http://purl.imsglobal.org/vocab/lis/v2/membership#Learner"))
                .claim(LtiService.CLAIM_CONTEXT, Map.of("id", "canvas-course-101"))
                .claim(LtiService.CLAIM_RESOURCE_LINK, Map.of("id", "canvas-link-202"))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(platformKeyPair.getPrivate())
                .compact();

        LtiService.LaunchResult result = ltiService.handleLaunch(jwt, "state-valid-1");

        assertNotNull(result);
        assertNotNull(result.caliperToken());
        assertEquals("student@university.edu", result.principal().email());
        assertEquals("STUDENT", result.principal().role());
        assertEquals(Optional.of(15L), result.courseId());
        assertEquals(Optional.of(77L), result.assignmentId());
        assertEquals("/assignments/77/code", result.targetPath());

        // Nonce must be consumed
        assertFalse(platformRepository.nonces.containsKey("nonce-valid-1"));
    }

    @Test
    @DisplayName("Successfully maps LMS Instructor role to Caliper INSTRUCTOR")
    void testSuccessfulLaunchInstructorRoleMapping() {
        platformRepository.nonces.put("nonce-inst-1", "state-inst-1");

        String jwt = Jwts.builder()
                .header().keyId("canvas-key-1").and()
                .issuer("https://canvas.instructure.com")
                .audience().add("caliper-client-100").and()
                .subject("canvas-sub-99")
                .claim("email", "prof@university.edu")
                .claim("name", "Prof. Turing")
                .claim("nonce", "nonce-inst-1")
                .claim(LtiService.CLAIM_MESSAGE_TYPE, "LtiResourceLinkRequest")
                .claim(LtiService.CLAIM_VERSION, "1.3.0")
                .claim(LtiService.CLAIM_DEPLOYMENT_ID, "deployment-xyz")
                .claim(LtiService.CLAIM_ROLES, List.of("http://purl.imsglobal.org/vocab/lis/v2/membership#Instructor"))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(platformKeyPair.getPrivate())
                .compact();

        LtiService.LaunchResult result = ltiService.handleLaunch(jwt, "state-inst-1");

        assertNotNull(result);
        assertEquals("INSTRUCTOR", result.principal().role());
        assertTrue(result.principal().instructor());
    }

    @Test
    @DisplayName("Rejects launch and prevents replay attack when nonce was already consumed")
    void testReplayAttackPrevention() {
        // Do not put nonce in repository; simulates already consumed or invalid state/nonce
        String jwt = Jwts.builder()
                .header().keyId("canvas-key-1").and()
                .issuer("https://canvas.instructure.com")
                .claim("nonce", "nonce-replayed")
                .signWith(platformKeyPair.getPrivate())
                .compact();

        ApiException ex = assertThrows(ApiException.class, () -> ltiService.handleLaunch(jwt, "state-123"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.status());
        assertTrue(ex.getMessage().contains("Replay attack prevented"));
    }

    @Test
    @DisplayName("Rejects launch when cryptographic signature does not match platform key")
    void testInvalidCryptographicSignature() throws Exception {
        platformRepository.nonces.put("nonce-fake", "state-fake");

        KeyPairGenerator fakeKpg = KeyPairGenerator.getInstance("RSA");
        fakeKpg.initialize(2048);
        KeyPair attackerKeyPair = fakeKpg.generateKeyPair();

        String jwt = Jwts.builder()
                .header().keyId("canvas-key-1").and()
                .issuer("https://canvas.instructure.com")
                .audience().add("caliper-client-100").and()
                .subject("attacker")
                .claim("email", "attacker@evil.com")
                .claim("nonce", "nonce-fake")
                .claim(LtiService.CLAIM_MESSAGE_TYPE, "LtiResourceLinkRequest")
                .claim(LtiService.CLAIM_VERSION, "1.3.0")
                .claim(LtiService.CLAIM_ROLES, List.of("Instructor"))
                .signWith(attackerKeyPair.getPrivate())
                .compact();

        ApiException ex = assertThrows(ApiException.class, () -> ltiService.handleLaunch(jwt, "state-fake"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.status());
        assertTrue(ex.getMessage().contains("Cryptographic verification"));
    }

    @Test
    @DisplayName("Rejects launch when client_id / audience does not match configured platform")
    void testAudienceMismatch() {
        platformRepository.nonces.put("nonce-aud", "state-aud");

        String jwt = Jwts.builder()
                .header().keyId("canvas-key-1").and()
                .issuer("https://canvas.instructure.com")
                .audience().add("unauthorized-client-id").and()
                .subject("user-1")
                .claim("nonce", "nonce-aud")
                .claim(LtiService.CLAIM_MESSAGE_TYPE, "LtiResourceLinkRequest")
                .claim(LtiService.CLAIM_VERSION, "1.3.0")
                .claim(LtiService.CLAIM_ROLES, List.of("Learner"))
                .signWith(platformKeyPair.getPrivate())
                .compact();

        ApiException ex = assertThrows(ApiException.class, () -> ltiService.handleLaunch(jwt, "state-aud"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.status());
        assertTrue(ex.getMessage().contains("audience does not match"));
    }

    @Test
    @DisplayName("KeyService generates valid RSA keypair and exports public JWKS correctly")
    void testLtiKeyServiceJwksExport() {
        LtiKeyService keyService = new LtiKeyService();
        assertNotNull(keyService.getPrivateKey());
        assertNotNull(keyService.getPublicKey());

        Map<String, Object> jwks = keyService.getJwks();
        assertNotNull(jwks);
        assertTrue(jwks.containsKey("keys"));
        List<?> keysList = (List<?>) jwks.get("keys");
        assertEquals(1, keysList.size());

        Map<?, ?> keyEntry = (Map<?, ?>) keysList.get(0);
        assertEquals("RSA", keyEntry.get("kty"));
        assertEquals("RS256", keyEntry.get("alg"));
        assertEquals("sig", keyEntry.get("use"));
        assertEquals(LtiKeyService.KEY_ID, keyEntry.get("kid"));
        assertNotNull(keyEntry.get("n"));
        assertNotNull(keyEntry.get("e"));
    }
}
