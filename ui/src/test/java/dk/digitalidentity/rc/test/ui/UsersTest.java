package dk.digitalidentity.rc.test.ui;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
public class UsersTest {
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
    public void usersTest() throws Exception {
        listUsers();
        String viggoVandmandUUID = BootstrapDevMode.userUUID;
        viewUser(viggoVandmandUUID);
    }

    public void viewUser(String uuid) {
        driver.get(url + "/ui/users/manage/" + uuid);
        Assert.assertEquals("OS2rollekatalog", driver.getTitle());
    }

    public void listUsers() {
        driver.get(url + "/ui/users/list");
        Assert.assertEquals("OS2rollekatalog", driver.getTitle());
    }
}
