package dk.digitalidentity.rc.test.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.chrome.ChromeDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import dk.digitalidentity.rc.test.ui.service.LoginService;
import dk.digitalidentity.rc.util.BootstrapDevMode;

@ExtendWith(SpringExtension.class)
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

    @BeforeEach
    public void before() throws Exception {
    	bootstrapper.init(false);
        driver = loginService.login();
    }
    
    @AfterEach
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
        Assertions.assertEquals("OS2rollekatalog", driver.getTitle());
    }

    public void listUsers() {
        driver.get(url + "/ui/users/list");
        Assertions.assertEquals("OS2rollekatalog", driver.getTitle());
    }
}
