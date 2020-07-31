package dk.digitalidentity.rc.test;

import java.util.List;

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

import dk.digitalidentity.rc.test.service.LoginService;
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
    	bootstrapper.init();
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
        editUserRole(userId, "2");
        Thread.sleep(1000); // *sigh*
        verifyEditUserRole(userId, "2");
        listUserRoles();
    }

    public String newUserRole() {
        driver.get(url + "/ui/userroles/new");
        Assert.assertEquals("Opret ny jobfunktionsrolle", driver.getTitle());
        WebElement nameInputBox = driver.findElement(By.name("name"));
        WebElement submitButton = driver.findElement(By.tagName("button"));

        nameInputBox.click();
        nameInputBox.clear();
        nameInputBox.sendKeys("new test role");

        submitButton.click();
        Assert.assertEquals("Tilføj brugersystemroller", driver.getTitle());

        return driver.getCurrentUrl().substring(driver.getCurrentUrl().lastIndexOf("/") + 1);
    }

    public void viewUserRole(String id) {
        driver.get(url + "/ui/userroles/view/" + id);
        Assert.assertEquals("Vis jobfunktionsrolle", driver.getTitle());
    }

    public void editUserRole(String id, String roleId) {
        driver.get(url+ "/ui/userroles/edit/" + id);
        Assert.assertEquals("Tilføj brugersystemroller", driver.getTitle());

        List<WebElement> checkboxes = driver.findElementsByTagName("input");

        for (WebElement e : checkboxes) {
            if (e.getAttribute("data-roleid") != null) {
                if (e.getAttribute("data-roleid").equals(roleId)) {
                    e.click();
                }
            }
        }
    }

    public void verifyEditUserRole(String id, String roleId) {
        driver.get(url + "/ui/userroles/edit/" + id);
        Assert.assertEquals("Tilføj brugersystemroller", driver.getTitle());

        List<WebElement> checkboxes = driver.findElementsByTagName("input");

        for (WebElement e : checkboxes) {
            if (e.getAttribute("data-roleid") != null) {
                if (e.getAttribute("data-roleid").equals(roleId)) {
                    Assert.assertTrue(e.isSelected());
                }
            }
        }
    }

    public void listUserRoles() {
        driver.get(url + "/ui/userroles/list");
        Assert.assertEquals("Jobfunktionsroller", driver.getTitle());
    }
}
