package dk.digitalidentity.rc;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.springframework.boot.devtools.restart.RestartScope;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.time.Duration;
import java.util.Base64;

@TestConfiguration(proxyBeanMethods = false)
public class SamlIdpContainerConfiguration {

    private static final File KEYCLOAK_CERT_FILE;
    private static final File KEYCLOAK_KEY_FILE;

    private static volatile KeycloakContainer instance;

    static {
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (InputStream is = SamlIdpContainerConfiguration.class.getResourceAsStream("/samlKeystore.pfx")) {
                ks.load(is, "Test1234".toCharArray());
            }
            String alias = ks.aliases().nextElement();
            Certificate cert = ks.getCertificate(alias);
            PrivateKey privateKey = (PrivateKey) ks.getKey(alias, "Test1234".toCharArray());

            Base64.Encoder encoder = Base64.getMimeEncoder(64, "\n".getBytes());

            KEYCLOAK_CERT_FILE = File.createTempFile("keycloak-cert", ".pem");
            KEYCLOAK_CERT_FILE.deleteOnExit();
            Files.writeString(KEYCLOAK_CERT_FILE.toPath(),
                    "-----BEGIN CERTIFICATE-----\n" + encoder.encodeToString(cert.getEncoded()) + "\n-----END CERTIFICATE-----\n");

            KEYCLOAK_KEY_FILE = File.createTempFile("keycloak-key", ".pem");
            KEYCLOAK_KEY_FILE.deleteOnExit();
            Files.writeString(KEYCLOAK_KEY_FILE.toPath(),
                    "-----BEGIN PRIVATE KEY-----\n" + encoder.encodeToString(privateKey.getEncoded()) + "\n-----END PRIVATE KEY-----\n");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @SuppressWarnings("resource")
    public static KeycloakContainer getInstance() {
        KeycloakContainer kc = instance;
        if (kc == null) {
            synchronized (SamlIdpContainerConfiguration.class) {
                kc = instance;
                if (kc == null) {
                    kc = new KeycloakContainer("quay.io/keycloak/keycloak:26.0")
                            .withRealmImportFile("keycloak/test-realm.json")
                            .withNetwork(TestContainersConfiguration.NETWORK)
                            .withNetworkAliases("keycloak")
                            .withCopyFileToContainer(MountableFile.forHostPath(KEYCLOAK_CERT_FILE.getAbsolutePath()), "/opt/keycloak/conf/server.crt.pem")
                            .withCopyFileToContainer(MountableFile.forHostPath(KEYCLOAK_KEY_FILE.getAbsolutePath()), "/opt/keycloak/conf/server.key.pem")
                            .withEnv("KC_HTTPS_CERTIFICATE_FILE", "/opt/keycloak/conf/server.crt.pem")
                            .withEnv("KC_HTTPS_CERTIFICATE_KEY_FILE", "/opt/keycloak/conf/server.key.pem")
                            .withEnv("KC_HOSTNAME", "https://keycloak:8443")
                            .withEnv("KC_HOSTNAME_STRICT", "false")
                            .withEnv("KC_HTTP_ENABLED", "true")
                            .waitingFor(Wait.forHttp("/realms/test/protocol/saml/descriptor")
                                    .forPort(8080)
                                    .forResponsePredicate(body -> body.contains("X509Certificate"))
                                    .withStartupTimeout(Duration.ofMinutes(3)));
                    kc.withReuse(true);
                    kc.start();
                    instance = kc;
                }
            }
        }
        return kc;
    }

    @Bean
    @RestartScope
    public KeycloakContainer keycloakContainer() {
        return getInstance();
    }

}
