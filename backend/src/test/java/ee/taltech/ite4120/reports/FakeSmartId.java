package ee.taltech.ite4120.reports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.util.Arrays;
import java.util.Base64;

/**
 * Imitates the Smart-ID v3 service for tests. It really signs the digest it is given
 * (RSASSA-PSS, SHA-512) with a key and a certificate made by keytool for the run.
 */
class FakeSmartId {

    static final String ISSUER = "Fake Smart-ID authority";

    enum Behaviour { OK, REFUSED, WRONG_SIGNATURE, OTHER_PERSON, RUNNING, DOWN }

    volatile Behaviour behaviour = Behaviour.OK;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpServer server;
    private final RSAPrivateKey key;
    private final X509Certificate certificate;
    private final RSAPrivateKey otherKey;
    private final X509Certificate otherCertificate;
    private volatile byte[] digest;
    private volatile String document;

    FakeSmartId(String person) {
        try {
            KeyStore own = keys(person);
            key = (RSAPrivateKey) own.getKey("k", "changeit".toCharArray());
            certificate = (X509Certificate) own.getCertificate("k");
            KeyStore other = keys("PNOEE-30303039914");
            otherKey = (RSAPrivateKey) other.getKey("k", "changeit".toCharArray());
            otherCertificate = (X509Certificate) other.getCertificate("k");
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/signature/notification/document/", this::start);
            server.createContext("/session/", this::session);
            server.start();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private void start(HttpExchange x) throws IOException {
        if (behaviour == Behaviour.DOWN) {
            answer(x, 503, "{}");
            return;
        }
        String path = x.getRequestURI().getPath();
        document = path.substring(path.lastIndexOf('/') + 1);
        digest = Base64.getDecoder().decode(json.readTree(x.getRequestBody())
                .path("signatureProtocolParameters").path("digest").asText());
        answer(x, 200, "{\"sessionID\":\"s1\",\"vc\":{\"type\":\"numeric4\",\"value\":\"1234\"}}");
    }

    private void session(HttpExchange x) throws IOException {
        switch (behaviour) {
            case RUNNING -> answer(x, 200, "{\"state\":\"RUNNING\"}");
            case REFUSED -> answer(x, 200, "{\"state\":\"COMPLETE\",\"result\":{\"endResult\":\"USER_REFUSED\"}}");
            case DOWN -> answer(x, 503, "{}");
            default -> complete(x);
        }
    }

    private void complete(HttpExchange x) throws IOException {
        try {
            boolean other = behaviour == Behaviour.OTHER_PERSON;
            byte[] signed = behaviour == Behaviour.WRONG_SIGNATURE ? sha512(new byte[] {1}) : digest;
            String signature = b64(pss(signed, other ? otherKey : key));
            String cert = b64((other ? otherCertificate : certificate).getEncoded());
            answer(x, 200, """
                    {"state":"COMPLETE","result":{"endResult":"OK","documentNumber":"%s"},
                     "signatureProtocol":"RAW_DIGEST_SIGNATURE",
                     "signature":{"value":"%s","signatureAlgorithm":"rsassa-pss",
                       "signatureAlgorithmParameters":{"hashAlgorithm":"SHA-512","saltLength":64}},
                     "cert":{"value":"%s","certificateLevel":"QUALIFIED"}}""".formatted(document, signature, cert));
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private static void answer(HttpExchange x, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().add("Content-Type", "application/json");
        x.sendResponseHeaders(status, bytes.length);
        x.getResponseBody().write(bytes);
        x.close();
    }

    private static KeyStore keys(String person) throws Exception {
        Path file = Files.createTempDirectory("fake-smart-id").resolve("k.p12");
        Process p = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", "k", "-keyalg", "RSA", "-keysize", "2048", "-validity", "30",
                "-dname", "SERIALNUMBER=" + person + ", CN=" + ISSUER + ", C=EE",
                "-keystore", file.toString(), "-storetype", "PKCS12", "-storepass", "changeit")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) {
            throw new IllegalStateException(out);
        }
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (var in = Files.newInputStream(file)) {
            ks.load(in, "changeit".toCharArray());
        }
        return ks;
    }

    /** EMSA-PSS over a ready digest (RFC 8017, 9.1.1), because the JDK signs only whole messages. */
    private static byte[] pss(byte[] mHash, RSAPrivateKey k) throws Exception {
        int bits = k.getModulus().bitLength() - 1;
        int emLen = (bits + 7) / 8;
        byte[] salt = new byte[64];
        new SecureRandom().nextBytes(salt);
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        md.update(new byte[8]);
        md.update(mHash);
        byte[] h = md.digest(salt);
        byte[] db = new byte[emLen - 65];
        db[db.length - 65] = 1;
        System.arraycopy(salt, 0, db, db.length - 64, 64);
        byte[] mask = new byte[0];
        for (int i = 0; mask.length < db.length; i++) {
            md.update(h);
            byte[] part = md.digest(new byte[] {0, 0, 0, (byte) i});
            int old = mask.length;
            mask = Arrays.copyOf(mask, old + part.length);
            System.arraycopy(part, 0, mask, old, part.length);
        }
        for (int i = 0; i < db.length; i++) {
            db[i] ^= mask[i];
        }
        db[0] &= (byte) (0xff >>> (8 * emLen - bits));
        byte[] em = Arrays.copyOf(db, emLen);
        System.arraycopy(h, 0, em, db.length, 64);
        em[emLen - 1] = (byte) 0xbc;
        byte[] s = new BigInteger(1, em).modPow(k.getPrivateExponent(), k.getModulus()).toByteArray();
        byte[] out = new byte[(k.getModulus().bitLength() + 7) / 8];
        int n = Math.min(s.length, out.length);
        System.arraycopy(s, s.length - n, out, out.length - n, n);
        return out;
    }

    private static byte[] sha512(byte[] b) throws Exception {
        return MessageDigest.getInstance("SHA-512").digest(b);
    }

    private static String b64(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }
}
