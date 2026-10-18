package app.seashelf.music.storage;

import org.junit.Test;
import java.util.Base64;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import static org.junit.Assert.*;

public class CredentialCipherTest {
    @Test public void encryptsAndDecryptsWithoutPlaintext() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        SecretKey key = generator.generateKey();
        String sealed = CredentialCipher.encrypt(key, "my Navidrome password");
        String sealedAgain = CredentialCipher.encrypt(key, "my Navidrome password");
        assertFalse(sealed.contains("my Navidrome password"));
        assertNotEquals(sealed, sealedAgain);
        assertEquals("my Navidrome password", CredentialCipher.decrypt(key, sealed));
        assertEquals("my Navidrome password", CredentialCipher.decrypt(key, sealedAgain));
    }

    @Test public void rejectsTamperedCiphertext() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        SecretKey key = generator.generateKey();
        String sealed = CredentialCipher.encrypt(key, "secret");
        byte[] changed = Base64.getDecoder().decode(sealed);
        changed[changed.length - 1] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(changed);
        try {
            CredentialCipher.decrypt(key, tampered);
            fail("Accepted tampered ciphertext");
        } catch (Exception expected) {
            // Authenticated encryption must reject changes.
        }
    }
}
