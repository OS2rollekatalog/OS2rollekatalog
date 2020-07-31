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
public class OUsTest {
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
    public void ousTest() {
        listOUs();
        String fiskbaekUUID = BootstrapDevMode.orgUnitUUID;
        viewOU(fiskbaekUUID);
        editOU(fiskbaekUUID, "2");
        verifyEditOU(fiskbaekUUID, "2");
    }

    public void viewOU(String ouUUID) {
        driver.get(url + "/ui/ous/view/" + ouUUID);
        Assert.assertEquals("Vis organisationsenhed", driver.getTitle());
    }

    public void editOU(String userId, String roleId) {
        driver.get(url + "/ui/ous/edit/" + userId);
        Assert.assertEquals("Rediger organisationsenhed", driver.getTitle());

        List<WebElement> checkboxes = driver.findElementsByTagName("input");

        for (WebElement e : checkboxes) {
            if (e.getAttribute("id") != null) {
                if (e.getAttribute("id").equals(roleId)) {
                    e.click();
                }
            }
        }
    }

    public void verifyEditOU(String userId, String roleId) {
        driver.get(url + "/ui/ous/edit/" + userId);
        Assert.assertEquals("Rediger organisationsenhed", driver.getTitle());

        List<WebElement> checkboxes = driver.findElementsByTagName("input");

        for (WebElement e : checkboxes) {
            if (e.getAttribute("id") != null) {
                if (e.getAttribute("id").equals(roleId)) {
                    Assert.assertTrue(e.isSelected());
                }
            }
        }
    }

    public void listOUs() {
        driver.get(url + "/ui/ous/list");
        Assert.assertEquals("Organisationsenheder", driver.getTitle());
    }
}
