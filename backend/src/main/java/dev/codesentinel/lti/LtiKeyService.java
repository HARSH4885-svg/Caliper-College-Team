package dev.codesentinel.lti;

import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Manages Caliper's RSA cryptographic keypair for LTI 1.3 Advantage authentication,
 * token signing, and public JWKS endpoint exposure.
 */
@Service
public class LtiKeyService {

    public static final String KEY_ID = "caliper-lti-key-1";

    private final KeyPair keyPair;

    public LtiKeyService() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            this.keyPair = kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA keypair generation not supported on this JVM", e);
        }
    }

    public LtiKeyService(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    public RSAPrivateKey getPrivateKey() {
        return (RSAPrivateKey) keyPair.getPrivate();
    }

    public RSAPublicKey getPublicKey() {
        return (RSAPublicKey) keyPair.getPublic();
    }

    /** Returns Caliper's public JSON Web Key Set (JWKS). */
    public Map<String, Object> getJwks() {
        RSAPublicKey pub = getPublicKey();
        String n = base64UrlEncodeUnsigned(pub.getModulus());
        String e = base64UrlEncodeUnsigned(pub.getPublicExponent());

        Map<String, Object> jwk = Map.of(
                "kty", "RSA",
                "alg", "RS256",
                "use", "sig",
                "kid", KEY_ID,
                "n", n,
                "e", e
        );

        return Map.of("keys", List.of(jwk));
    }

    private static String base64UrlEncodeUnsigned(BigInteger bigInt) {
        byte[] bytes = bigInt.toByteArray();
        // BigInteger may have a leading zero byte for sign representation; strip it for JWK
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] stripped = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, stripped, 0, stripped.length);
            bytes = stripped;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
