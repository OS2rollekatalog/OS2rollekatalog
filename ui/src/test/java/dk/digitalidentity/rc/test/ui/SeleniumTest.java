package dk.digitalidentity.rc.test.ui;

import dk.digitalidentity.rc.TestContainersConfiguration;
import dk.digitalidentity.rc.util.BootstrapDevMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.context.WebServerInitializedEvent;
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
import org.testcontainers.containers.Network;
import org.testcontainers.containers.VncRecordingContainer;
import org.testcontainers.junit.jupiter.Container;

import java.io.File;
import java.time.Duration;


@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(locations="classpath:test.properties")
@ActiveProfiles({ "test" })
@org.testcontainers.junit.jupiter.Testcontainers
@Import(TestContainersConfiguration.class)
@ContextConfiguration(initializers = SeleniumTest.Initializer.class)
public class SeleniumTest {
    public static Network NETWORK = Network.newNetwork();

    public static String url = "https://host.testcontainers.internal:8090";
    protected RemoteWebDriver driver;

    @Autowired
    private BootstrapDevMode bootstrapper;

    @Value("${tests.username}")
    private String username;

    @Value("${tests.password}")
    private String password;
    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            applicationContext.addApplicationListener(
                    (ApplicationListener<WebServerInitializedEvent>) event -> {
                        Testcontainers.exposeHostPorts(8090);
                    }
            );
        }
    }

    @Container
    public BrowserWebDriverContainer<?> seleniumContainer =
            new BrowserWebDriverContainer<>()
                    .withNetwork(NETWORK)
                    .withRecordingMode(BrowserWebDriverContainer.VncRecordingMode.RECORD_FAILING,
                            new File("./target"),
                            VncRecordingContainer.VncRecordingFormat.MP4
                    )
                    .withRecordingFileFactory(new DefaultRecordingFileFactory());

    @BeforeEach
    public void before() throws Exception {
        bootstrapper.init(false);
        initDriver();
        login();
    }

    @AfterEach
    public void after() {
        logout();
    }

    private void logout() {
        // logout
        driver.get(url + "/ui/userroles/list");
        try {
            WebElement logoutForm = driver.findElement(By.id("logout_form"));
            logoutForm.submit();
        } catch (NoSuchElementException ignored) {}
    }

    private void login() {
        driver.get(url + "/ui/userroles/list");

        if (driver.getTitle().equals("Home Realm Discovery")) {
            driver.executeScript("HRD.selection('AD AUTHORITY')");

        }
        if (driver.getTitle().equals("Sign In")) {
            WebElement usernameInputBox = driver.findElement(By.name("UserName"));
            WebElement passwordInputBox = driver.findElement(By.name("Password"));
            WebElement submitButton = driver.findElement(By.id("submitButton"));

            usernameInputBox.click();
            usernameInputBox.clear();
            usernameInputBox.sendKeys(username);

            passwordInputBox.click();
            passwordInputBox.clear();
            passwordInputBox.sendKeys(password);
            submitButton.click();
        }
    }


    private void initDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("window-size=1024,768");
        options.setAcceptInsecureCerts(true);
        driver = new RemoteWebDriver(seleniumContainer.getSeleniumAddress(), options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
    }

}
