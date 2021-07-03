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
public class NavigationTest {
    private String url = "https://localhost:8090";
    private ChromeDriver driver;

    @Autowired
    private LoginService loginService;
    
    @Autowired
    private BootstrapDevMode bootstrapper;
    
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
    public void navigate() {
    	ousList();
    	ousView();
    	ousEdit();
    	
    	roleGroupsList();
    	roleGroupsNew();
    	roleGroupsEdit();
    	roleGroupsView();
    	
    	userRolesList();
    	userRolesNew();
    	userRolesEdit();
    	userRolesView();
    	
    	usersList();
    	usersView();
    	usersEdit();
    }

    private void userRolesList() {
        driver.get(url + "/ui/userroles/list");
        Assert.assertEquals("OS2rollekatalog", driver.getTitle());
    }

    private void userRolesNew() {
        driver.get(url + "/ui/userroles/new");
        Assert.assertEquals("OS2rollekatalog", driver.getTitle());
    }

    private void userRolesView() {
        driver.get(url + "/ui/userroles/view/1");
        Assert.assertEquals("OS2rollekatalog", driver.getTitle());
    }
    
    private void userRolesEdit() {
        driver.get(url + "/ui/userroles/edit/1");
        Assert.assertEquals("OS2rollekatalog", driver.getTitle());
    }
    
    private void roleGroupsList() {
        driver.get(url + "/ui/rolegroups/list");
        Assert.assertEquals("OS2rollekatalog", driver.getTitle());
    }

    private void roleGroupsNew() {
        driver.get(url + "/ui/rolegroups/new");
        Assert.assertEquals("OS2rollekatalog", driver.getTitle());
    }
    
    private void roleGroupsEdit() {
        driver.get(url + "/ui/rolegroups/edit/1");
        Assert.assertEquals("OS2rollekatalog", driver.getTitle());
    }
    
    private void roleGroupsView() {
        driver.get(url + "/ui/rolegroups/view/1");
        Assert.assertEquals("OS2rollekatalog", driver.getTitle());
    }

    private void ousList() {
        driver.get(url + "/ui/ous/list");
        Assert.assertEquals("OS2rollekatalog", driver.getTitle());
    }
    
    private void ousView() {
        driver.get(url + "/ui/ous/view/" + BootstrapDevMode.orgUnitUUID);
        Assert.assertEquals("OS2rollekatalog", driver.getTitle());
    }
    
    private void ousEdit() {
        driver.get(url + "/ui/ous/edit/" + BootstrapDevMode.orgUnitUUID);
        Assert.assertEquals("OS2rollekatalog", driver.getTitle());
    }

    private void usersList() {
        driver.get(url + "/ui/users/list");
        Assert.assertEquals("OS2rollekatalog", driver.getTitle());
    }
    
    private void usersView() {
        driver.get(url + "/ui/users/view/" + BootstrapDevMode.userUUID);
        Assert.assertEquals("OS2rollekatalog", driver.getTitle());
    }
    
    private void usersEdit() {
        driver.get(url + "/ui/users/edit/" + BootstrapDevMode.userUUID);
        Assert.assertEquals("OS2rollekatalog", driver.getTitle());
    }
}