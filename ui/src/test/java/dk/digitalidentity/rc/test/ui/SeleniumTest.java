package dk.digitalidentity.rc.test.ui;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import dk.digitalidentity.rc.SamlIdpContainerConfiguration;
import dk.digitalidentity.rc.TestContainersConfiguration;
import dk.digitalidentity.rc.config.TestInterceptorConfiguration;
import dk.digitalidentity.rc.generator.TestDataBootstrap;
import dk.digitalidentity.rc.util.BootstrapDevMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.containers.DefaultRecordingFileFactory;
import org.testcontainers.containers.VncRecordingContainer;
import org.testcontainers.junit.jupiter.Container;

import java.io.File;
import java.time.Duration;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(locations = "classpath:test.properties")
@ActiveProfiles({"test"})
@org.testcontainers.junit.jupiter.Testcontainers
@Import({TestContainersConfiguration.class, SamlIdpContainerConfiguration.class, TestInterceptorConfiguration.class})
@ContextConfiguration(initializers = SeleniumTest.Initializer.class)
public class SeleniumTest {
    public static final String url = "https://host.testcontainers.internal:8090";

    protected RemoteWebDriver driver;

    @Autowired
    private BootstrapDevMode bootstrapper;

    @Autowired
    private TestDataBootstrap testDataBootstrap;

    @Value("${tests.username}")
    private String username;

    @Value("${tests.password}")
    private String password;

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            applicationContext.addApplicationListener(
                    (ApplicationListener<WebServerInitializedEvent>) _ -> Testcontainers.exposeHostPorts(8090)
            );
            // Set the IdP metadata location BEFORE context refresh so saml-module 5.x's
            // @ConfigurationProperties binding picks up the dynamic Keycloak port.
            // (DynamicPropertyRegistrar is too late — its bean is instantiated after binding.)
            // getHost() honours TESTCONTAINERS_HOST_OVERRIDE / remote docker, so this works
            // both for local docker and for CI runners that talk to a remote docker daemon.
            KeycloakContainer kc = SamlIdpContainerConfiguration.getInstance();
            TestPropertyValues.of(
                    "di.saml.idp.metadataLocation=url:http://" + kc.getHost() + ":" + kc.getMappedPort(8080) + "/realms/test/protocol/saml/descriptor"
            ).applyTo(applicationContext.getEnvironment());
        }
    }

    @Container
    public BrowserWebDriverContainer<?> seleniumContainer =
            new BrowserWebDriverContainer<>()
                    .withNetwork(TestContainersConfiguration.NETWORK)
                    .withRecordingMode(BrowserWebDriverContainer.VncRecordingMode.RECORD_FAILING,
                            new File("./target"),
                            VncRecordingContainer.VncRecordingFormat.MP4)
                    .withRecordingFileFactory(new DefaultRecordingFileFactory());

    @BeforeEach
    public void before() throws Exception {
        bootstrapper.init(false);
        testDataBootstrap.runManually();
        initDriver();
        login();
    }

    @AfterEach
    public void after() {
        logout();
    }

    private void logout() {
        driver.get(url + "/ui/userroles/list");
        try {
            WebElement logoutForm = driver.findElement(By.id("logout_form"));
            logoutForm.submit();
        } catch (NoSuchElementException ignored) {}
    }

    private void login() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        driver.get(url + "/ui/userroles/list");

        WebElement usernameInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("username")));
        usernameInput.clear();
        usernameInput.sendKeys(username);

        driver.findElement(By.id("password")).sendKeys(password);
        driver.findElement(By.id("kc-login")).click();

        new WebDriverWait(driver, Duration.ofSeconds(15))
                .until(d -> d.getCurrentUrl().startsWith(url));
    }

    private void initDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("window-size=1024,768");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--no-sandbox");
        options.setAcceptInsecureCerts(true);
        driver = new RemoteWebDriver(seleniumContainer.getSeleniumAddress(), options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
    }
}
