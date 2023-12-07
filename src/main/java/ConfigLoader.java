import java.util.Properties;
import java.io.InputStream;
import java.io.IOException;

public class ConfigLoader {

    private Properties properties;

    public ConfigLoader() throws IOException {
        this.properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new IOException("Unable to find config.properties");
            }
            this.properties.load(input);
            validateProperties();
        }
    }

    private void validateProperties() throws IOException {
        ensurePropertyExists("server.url");
        ensurePropertyExists("client.realm");
        ensurePropertyExists("client.id");
        ensurePropertyExists("idp.realm");
        ensurePropertyExists("client.secret.env");
        ensurePropertyExists("application.realm");
        ensurePropertyExists("real.run");
        ensurePropertyExists("user.max");
    }

    private void ensurePropertyExists(String key) throws IOException {
        if (getProperty(key) == null || getProperty(key).isEmpty()) {
            throw new IOException("Missing required property: " + key);
        }
    }

    public String getServerUrl() {
        return getProperty("server.url");
    }

    public String getClientRealm() {
        return getProperty("client.realm");
    }

    public String getClientId() {
        return getProperty("client.id");
    }

    public String getClientSecret() {
        // Retrieves the client secret from an environment variable
        return System.getenv(getProperty("client.secret.env"));
    }

    public String getIdpRealm() {
        return getProperty("idp.realm");
    }

    public String getApplicationRealm() {
        return getProperty("application.realm");
    }

    public boolean isRealRun() {
        return Boolean.parseBoolean(getProperty("real.run"));
    }

    public int getUserMax() {
        try {
            return Integer.parseInt(getProperty("user.max"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid format for user.max property", e);
        }
    }

    private String getProperty(String key) {
        return this.properties.getProperty(key);
    }
}
