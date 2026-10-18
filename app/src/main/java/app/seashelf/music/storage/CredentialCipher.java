package app.seashelf.music.storage;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** AES-GCM envelope: 12-byte nonce followed by ciphertext and authentication tag. */
public final class CredentialCipher {
    private CredentialCipher() { }

    public static String encrypt(SecretKey key, String plaintext)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] nonce = cipher.getIV();
        if (nonce == null || nonce.length != 12) throw new GeneralSecurityException("Invalid GCM IV");
        byte[] payload = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] result = new byte[nonce.length + payload.length];
        System.arraycopy(nonce, 0, result, 0, nonce.length);
        System.arraycopy(payload, 0, result, nonce.length, payload.length);
        return Base64.getEncoder().encodeToString(result);
    }

    public static String decrypt(SecretKey key, String sealed) throws GeneralSecurityException {
        byte[] data;
        try {
            data = Base64.getDecoder().decode(sealed);
        } catch (IllegalArgumentException error) {
            throw new GeneralSecurityException("Credential data is invalid", error);
        }
        if (data.length < 28) throw new GeneralSecurityException("Credential data is too short");
        byte[] nonce = new byte[12];
        System.arraycopy(data, 0, nonce, 0, nonce.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        byte[] plaintext = cipher.doFinal(data, nonce.length, data.length - nonce.length);
        return new String(plaintext, StandardCharsets.UTF_8);
    }
}
