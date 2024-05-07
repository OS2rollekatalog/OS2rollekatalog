package dk.digitalidentity.rc.test.ui;

import dk.digitalidentity.rc.util.BootstrapDevMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class OUsTest extends SeleniumTest {

    @Test
    public void ousTest() throws Exception {
        listOUs();
        String fiskbaekUUID = BootstrapDevMode.orgUnitUUID;
        viewOU(fiskbaekUUID);
    }

    public void viewOU(String ouUUID) {
        driver.get(url + "/ui/ous/manage/" + ouUUID);
        Assertions.assertEquals("OS2rollekatalog", driver.getTitle());
    }

    public void listOUs() {
        driver.get(url + "/ui/ous/list");
        Assertions.assertEquals("OS2rollekatalog", driver.getTitle());
    }
}
