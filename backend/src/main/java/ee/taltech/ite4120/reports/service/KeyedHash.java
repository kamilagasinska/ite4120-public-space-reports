package ee.taltech.ite4120.reports.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HMAC of identifiers, so the database never holds an IP address, a device identifier
 * or a personal code. A plain hash would not do, because all personal codes can be tried.
 */
@Component
public class KeyedHash {

    private final SecretKeySpec key;

    public KeyedHash(@Value("${reports.hash-secret:}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secret.isBlank()) {
            LoggerFactory.getLogger(KeyedHash.class)
                    .warn("reports.hash-secret is not set, limits and trust restart with the application");
            bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
        }
        key = new SecretKeySpec(bytes, "HmacSHA256");
    }

    public String of(String purpose, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal((purpose + ":" + value).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
