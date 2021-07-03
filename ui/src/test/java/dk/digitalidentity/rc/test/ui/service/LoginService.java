package dk.digitalidentity.rc.test.ui.service;

import java.util.concurrent.atomic.AtomicReference;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LoginService {
    private static boolean isLoggedIn = false;
    private static String url = "https://localhost:8090";
    private static AtomicReference<LoginService> INSTANCE = new AtomicReference<>();
    private ChromeDriver driver;

    @Value("${chromedriver.bin}")
    private String chromeDriver;

    @Value("${tests.username}")
    private String username;

    @Value("${tests.password}")
    private String password;

    public LoginService() {
        final LoginService previous = INSTANCE.getAndSet(this);
        if (previous != null) {
            throw new IllegalStateException("Second singleton " + this + " created after " + previous);
        }
    }

    public static LoginService getInstance() {
        return INSTANCE.get();
    }

    public void logout() {
    	driver.close();
    	isLoggedIn = false;
    }
    
    public ChromeDriver login() {
        if (isLoggedIn) {
            return driver;
        }

        initDriver();
        driver.get(url + "/ui/userroles/list");

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
            isLoggedIn = true;
        }

        return driver;
    }

    private void initDriver() {
        System.setProperty("webdriver.chrome.driver", chromeDriver);
        ChromeOptions options = new ChromeOptions();
        options.addArguments("window-size=1024,768");

        DesiredCapabilities capabilities = DesiredCapabilities.chrome();
        capabilities.setCapability(ChromeOptions.CAPABILITY, options);
        capabilities.setJavascriptEnabled(true);
        driver = new ChromeDriver(capabilities);
    }
}