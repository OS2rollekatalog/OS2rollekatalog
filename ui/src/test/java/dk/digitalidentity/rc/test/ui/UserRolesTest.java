package dk.digitalidentity.rc.test.ui;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import dk.digitalidentity.rc.test.ui.service.LoginService;
import dk.digitalidentity.rc.util.BootstrapDevMode;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(locations="classpath:test.properties")
@ActiveProfiles({ "test" })
public class UserRolesTest {
    protected String url = "https://localhost:8090";
    private ChromeDriver driver;

    @Autowired
    private BootstrapDevMode bootstrapper;

    @Autowired
    private LoginService loginService;

    @Before
    public void before() throws Exception {
    	bootstrapper.init(false);
        driver = loginService.login();
    }

    @After
    public void after() {
    	loginService.logout();
    }

    @Test
    public void userRolesTest() throws Exception {
        String userId = newUserRole();
        viewUserRole(userId);
        listUserRoles();
    }

    public String newUserRole() {
        driver.get(url + "/ui/userroles/new");
        Assert.assertEquals("OS2rollekatalog", driver.getTitle());
        WebElement nameInputBox = driver.findElement(By.id("name"));
        WebElement submitButton = driver.findElement(By.tagName("button"));

        nameInputBox.click();
        nameInputBox.clear();
        nameInputBox.sendKeys("new test role");

        submitButton.click();
        Assert.assertEquals("OS2rollekatalog", driver.getTitle());

        return driver.getCurrentUrl().substring(driver.getCurrentUrl().lastIndexOf("/") + 1);
    }

    public void viewUserRole(String id) {
        driver.get(url + "/ui/userroles/view/" + id);
        Assert.assertEquals("OS2rollekatalog", driver.getTitle());
    }

    public void listUserRoles() {
        driver.get(url + "/ui/userroles/list");
        Assert.assertEquals("OS2rollekatalog", driver.getTitle());
    }
}
