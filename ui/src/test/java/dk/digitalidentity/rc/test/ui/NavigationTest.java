package dk.digitalidentity.rc.test.ui;

import dk.digitalidentity.rc.util.BootstrapDevMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class NavigationTest extends SeleniumTest {

    @Test
    public void navigate() {
    	ousList();
    	ousView();
    	
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
    }

    private void userRolesList() {
        driver.get(url + "/ui/userroles/list");
        Assertions.assertEquals("OS2rollekatalog", driver.getTitle());
    }

    private void userRolesNew() {
        driver.get(url + "/ui/userroles/new");
        Assertions.assertEquals("OS2rollekatalog", driver.getTitle());
    }

    private void userRolesView() {
        driver.get(url + "/ui/userroles/view/1");
        Assertions.assertEquals("OS2rollekatalog", driver.getTitle());
    }
    
    private void userRolesEdit() {
        driver.get(url + "/ui/userroles/edit/1");
        Assertions.assertEquals("OS2rollekatalog", driver.getTitle());
    }
    
    private void roleGroupsList() {
        driver.get(url + "/ui/rolegroups/list");
        Assertions.assertEquals("OS2rollekatalog", driver.getTitle());
    }

    private void roleGroupsNew() {
        driver.get(url + "/ui/rolegroups/new");
        Assertions.assertEquals("OS2rollekatalog", driver.getTitle());
    }
    
    private void roleGroupsEdit() {
        driver.get(url + "/ui/rolegroups/edit/1");
        Assertions.assertEquals("OS2rollekatalog", driver.getTitle());
    }
    
    private void roleGroupsView() {
        driver.get(url + "/ui/rolegroups/view/1");
        Assertions.assertEquals("OS2rollekatalog", driver.getTitle());
    }

    private void ousList() {
        driver.get(url + "/ui/ous/list");
        Assertions.assertEquals("OS2rollekatalog", driver.getTitle());
    }
    
    private void ousView() {
        driver.get(url + "/ui/ous/manage/" + BootstrapDevMode.orgUnitUUID);
        Assertions.assertEquals("OS2rollekatalog", driver.getTitle());
    }
    
    private void usersList() {
        driver.get(url + "/ui/users/list");
        Assertions.assertEquals("OS2rollekatalog", driver.getTitle());
    }
    
    private void usersView() {
        driver.get(url + "/ui/users/manage/" + BootstrapDevMode.userUUID);
        Assertions.assertEquals("OS2rollekatalog", driver.getTitle());
    }
}