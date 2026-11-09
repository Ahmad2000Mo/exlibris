package hbv.web;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Passwort-Hashing mit PBKDF2-HMAC-SHA256 (im JDK enthalten).
 *
 * Gespeichertes Format (ein einziges Datenbankfeld):
 *   pbkdf2$<iterationen>$<salt base64>$<hash base64>
 *
 * Iterationen und Salt stehen mit im String, damit man die Iterationszahl spaeter
 * erhoehen kann, ohne alte Hashes ungueltig zu machen.
 *
 * Aufruf auf der Kommandozeile, um einen Hash fuer die Datenbank zu erzeugen
 * (Passwort ueber stdin, damit es nicht in der Prozessliste / Shell-History steht):
 *   printf '%s' 'meinPasswort' | java -cp build/WEB-INF/classes hbv.web.PasswordHasher
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2";
    /** OWASP-Empfehlung (2023) fuer PBKDF2-HMAC-SHA256. */
    static final int DEFAULT_ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {}

    public static String hash(String password) {
        return hash(password, DEFAULT_ITERATIONS);
    }

    static String hash(String password, int iterations) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password.toCharArray(), salt, iterations);
        Base64.Encoder b64 = Base64.getEncoder();
        return PREFIX + "$" + iterations + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(hash);
    }

    /** Prueft ein Passwort gegen einen gespeicherten Hash. Gibt bei kaputtem Format false zurueck. */
    public static boolean verify(String password, String stored) {
        if (password == null || stored == null) return false;
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) return false;
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(password.toCharArray(), salt, iterations);
            // Vergleich in konstanter Zeit -> kein Timing-Angriff moeglich
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 nicht verfuegbar", e);
        } finally {
            spec.clearPassword();
        }
    }

    public static void main(String[] args) throws java.io.IOException {
        String password = new String(System.in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replaceAll("[\\r\\n]+$", "");
        if (password.isEmpty()) {
            System.err.println("Aufruf: printf '%s' '<passwort>' | java hbv.web.PasswordHasher");
            System.exit(1);
        }
        System.out.println(hash(password));
    }
}
