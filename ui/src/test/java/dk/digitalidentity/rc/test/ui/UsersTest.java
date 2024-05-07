package dk.digitalidentity.rc.test.ui;

import dk.digitalidentity.rc.util.BootstrapDevMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class UsersTest extends SeleniumTest {

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
