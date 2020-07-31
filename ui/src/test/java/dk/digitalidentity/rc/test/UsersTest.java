package dk.digitalidentity.rc.test;

import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
public class UsersTest {
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
    public void usersTest() {
        listUsers();
        String viggoVandmandUUID = BootstrapDevMode.userUUID;
        viewUser(viggoVandmandUUID);
        // TODO: fix this test
        // editUser(viggoVandmandUUID, "1");
        // verifyEditUser(viggoVandmandUUID, "1");
    }

    public void viewUser(String uuid) {
        driver.get(url + "/ui/users/view/" + uuid);
        Assert.assertEquals("Vis bruger", driver.getTitle());
    }

    public void editUser(String userUUUID, String roleId) {
        driver.get(url + "/ui/users/edit/" + userUUUID);
        Assert.assertEquals("Rediger bruger", driver.getTitle());

        List<WebElement> checkboxes = driver.findElementsByTagName("input");

        // TODO: this fails with an exception, because of the modal dialog popup I think... FIX IT!
        for (WebElement e : checkboxes) {
            if (e.getAttribute("id") != null) {
                if (e.getAttribute("id").equals(roleId)) {
                    e.click();
                }
            }
        }
    }

    public void verifyEditUser(String userUUID, String roleId) {
        driver.get(url + "/ui/users/edit/" + userUUID);
        Assert.assertEquals("Rediger bruger", driver.getTitle());

        List<WebElement> checkboxes = driver.findElementsByTagName("input");

        for (WebElement e : checkboxes) {
            if (e.getAttribute("id") != null) {
                if (e.getAttribute("id").equals(roleId)) {
                    Assert.assertTrue(e.isSelected());
                }
            }
        }
    }

    public void listUsers() {
        driver.get(url + "/ui/users/list");
        Assert.assertEquals("Brugere", driver.getTitle());
    }
}
