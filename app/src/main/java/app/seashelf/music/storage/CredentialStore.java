package app.seashelf.music.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import app.seashelf.music.api.ServerAddress;
import app.seashelf.music.api.SubsonicClient;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/** Keeps the password encrypted with a device-bound Android Keystore key. */
public final class CredentialStore {
    private static final String ALIAS = "seashelf-navidrome-v1";
    private final SharedPreferences prefs;

    public CredentialStore(Context context) {
        prefs = context.getSharedPreferences("connection", Context.MODE_PRIVATE);
    }

    public void save(String address, String username, String password) throws GeneralSecurityException {
        String sealed = CredentialCipher.encrypt(key(), password);
        prefs.edit().putString("address", ServerAddress.parse(address).baseUrl())
                .putString("username", username)
                .putString("secret", sealed).apply();
    }

    public Connection load() throws GeneralSecurityException {
        String address = prefs.getString("address", null);
        String username = prefs.getString("username", null);
        String sealed = prefs.getString("secret", null);
        if (address == null || username == null || sealed == null) return null;
        return new Connection(address, username, CredentialCipher.decrypt(key(), sealed));
    }

    public void clear() { prefs.edit().clear().apply(); }

    private static SecretKey key() throws GeneralSecurityException {
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            KeyStore.Entry existing = store.getEntry(ALIAS, null);
            if (existing instanceof KeyStore.SecretKeyEntry)
                return ((KeyStore.SecretKeyEntry) existing).getSecretKey();
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build());
            return generator.generateKey();
        } catch (GeneralSecurityException error) {
            throw error;
        } catch (Exception error) {
            throw new GeneralSecurityException("无法读取设备安全存储", error);
        }
    }

    public static final class Connection {
        private final String address;
        private final String username;
        private final String password;

        private Connection(String address, String username, String password) {
            this.address = address;
            this.username = username;
            this.password = password;
        }

        public String address() { return address; }
        public String username() { return username; }
        public SubsonicClient client() {
            return new SubsonicClient(ServerAddress.parse(address), username, password);
        }
    }
}
