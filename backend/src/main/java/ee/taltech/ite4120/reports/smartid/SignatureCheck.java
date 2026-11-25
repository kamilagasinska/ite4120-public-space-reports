package ee.taltech.ite4120.reports.smartid;

import ee.taltech.ite4120.reports.smartid.http.wire.SmartIdSessionStatus;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Base64;
import java.util.Map;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.security.auth.x500.X500Principal;

/**
 * Does the signature belong to this text, this person and the demo authority. The issuer
 * is compared by name and the answer comes over TLS from the provider. Before production
 * the certificate chain and revocation must be checked too.
 */
public class SignatureCheck {

    private final String trustedIssuer;

    public SignatureCheck(String trustedIssuer) {
        this.trustedIssuer = trustedIssuer;
    }

    /** Null when everything fits, otherwise the reason, without personal data. */
    public String problem(SmartIdSessionStatus s, String document, byte[] text) {
        if (s.cert() == null || s.cert().value() == null || s.signature() == null || s.signature().value() == null) {
            return "certificate or signature is missing";
        }
        if (!"QUALIFIED".equals(s.cert().certificateLevel())) {
            return "certificate is not qualified";
        }
        if (!"rsassa-pss".equals(s.signature().signatureAlgorithm())) {
            return "unexpected algorithm";
        }
        if (!document.equals(s.result().documentNumber())) {
            return "another document";
        }
        try {
            X509Certificate cert = certificate(s);
            cert.checkValidity();
            if (!trustedIssuer.equals(attribute(cert.getIssuerX500Principal(), "CN"))) {
                return "unknown issuer";
            }
            if (!TestPersons.person(document).equals(attribute(cert.getSubjectX500Principal(), "SERIALNUMBER"))) {
                return "another person";
            }
            Signature signature = Signature.getInstance("RSASSA-PSS");
            signature.setParameter(new PSSParameterSpec("SHA-512", "MGF1", MGF1ParameterSpec.SHA512, 64, 1));
            signature.initVerify(cert.getPublicKey());
            signature.update(text);
            return signature.verify(Base64.getDecoder().decode(s.signature().value())) ? null : "signature does not match";
        } catch (GeneralSecurityException | InvalidNameException | IllegalArgumentException e) {
            return e.getClass().getSimpleName();
        }
    }

    /** The person's name in the certificate, or null. */
    public String name(SmartIdSessionStatus s) {
        try {
            X500Principal subject = certificate(s).getSubjectX500Principal();
            String given = attribute(subject, "GIVENNAME");
            String family = attribute(subject, "SURNAME");
            return given == null || family == null ? null : given + " " + family;
        } catch (GeneralSecurityException | InvalidNameException | IllegalArgumentException e) {
            return null;
        }
    }

    private static X509Certificate certificate(SmartIdSessionStatus s) throws GeneralSecurityException {
        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(
                new ByteArrayInputStream(Base64.getDecoder().decode(s.cert().value())));
    }

    private static String attribute(X500Principal name, String type) throws InvalidNameException {
        return new LdapName(name.getName(X500Principal.RFC2253, Map.of("2.5.4.5", "SERIALNUMBER",
                        "2.5.4.42", "GIVENNAME", "2.5.4.4", "SURNAME"))).getRdns().stream()
                .filter(r -> r.getType().equalsIgnoreCase(type))
                .map(r -> text(r.getValue())).findFirst().orElse(null);
    }

    /** A named attribute may still come as its raw encoding. */
    private static String text(Object value) {
        if (value instanceof byte[] der && der.length > 2 && der[1] >= 0) {
            return new String(der, 2, der[1], StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }
}
