package ee.taltech.ite4120.reports.service;

import java.security.SecureRandom;
import java.time.Year;
import java.time.ZoneId;

/** Reference numbers that cannot be guessed from one another, so reports cannot be enumerated. */
final class ReferenceNumbers {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private ReferenceNumbers() {}

    static String next() {
        StringBuilder sb = new StringBuilder("R-").append(Year.now(ZoneId.of("Europe/Tallinn"))).append('-');
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
