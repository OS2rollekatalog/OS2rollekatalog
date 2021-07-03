package dk.digitalidentity.rc.test.ui;

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

import dk.digitalidentity.rc.test.ui.service.LoginService;
import dk.digitalidentity.rc.util.BootstrapDevMode;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(locations="classpath:test.properties")
@ActiveProfiles({ "test" })
public class RoleGroupsTest {
    private String url = "https://localhost:8090";
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
    public void roleGroupsTest() {
        String roleGroupId = newRoleGroup();
        viewRoleGroup(roleGroupId);
        editRoleGroup(roleGroupId, "1");
        verifyEditRoleGroup(roleGroupId, "1");
        listRoleGroups();
    }

    public String newRoleGroup() {
        driver.get(url + "/ui/rolegroups/new");
        Assert.assertEquals("OS2rollekatalog", driver.getTitle());
        WebElement nameInputBox = driver.findElement(By.id("name"));
        WebElement submitButton = driver.findElement(By.tagName("button"));

        nameInputBox.click();
        nameInputBox.clear();
        nameInputBox.sendKeys("Test rolegroup");

        submitButton.click();
        Assert.assertEquals("OS2rollekatalog", driver.getTitle());
        return driver.getCurrentUrl().substring(driver.getCurrentUrl().lastIndexOf("/") + 1);
    }

    public void viewRoleGroup(String roleGroupId) {
        driver.get(url + "/ui/rolegroups/view/" + roleGroupId);
        Assert.assertEquals("OS2rollekatalog", driver.getTitle());
    }

    public void editRoleGroup(String roleGroupId, String roleId) {
        driver.get(url + "/ui/rolegroups/edit/" + roleGroupId);
        Assert.assertEquals("OS2rollekatalog", driver.getTitle());

        List<WebElement> checkboxes = driver.findElementsByTagName("input");

        for (WebElement e : checkboxes) {
            if (e.getAttribute("id") != null) {
                if (e.getAttribute("id").equals(roleId)) {
                    e.click();
                }
            }
        }
        
        try {
        	Thread.sleep(1000);
		}
        catch (InterruptedException e1) {
        	; // wait a bit, clicking the checkbox is async
		}
    }

    public void verifyEditRoleGroup(String userId, String roleId) {
        driver.get(url + "/ui/rolegroups/edit/" + userId);
        Assert.assertEquals("OS2rollekatalog", driver.getTitle());

        List<WebElement> checkboxes = driver.findElementsByTagName("input");

        for (WebElement e : checkboxes) {
            if (e.getAttribute("id") != null) {
                if (e.getAttribute("id").equals(roleId)) {
                    Assert.assertTrue(e.isSelected());
                }
            }
        }
    }

    public void listRoleGroups() {
        driver.get(url + "/ui/rolegroups/list");
        Assert.assertEquals("OS2rollekatalog", driver.getTitle());
    }
}
