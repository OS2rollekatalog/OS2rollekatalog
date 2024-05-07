package dk.digitalidentity.rc.test.ui;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public class UserRolesTest extends SeleniumTest {

    @Test
    public void userRolesTest() throws Exception {
        String userId = newUserRole();
        viewUserRole(userId);
        listUserRoles();
    }

    public String newUserRole() {
        driver.get(url + "/ui/userroles/new");
        Assertions.assertEquals("OS2rollekatalog", driver.getTitle());
        WebElement nameInputBox = driver.findElement(By.id("name"));
        WebElement submitButton = driver.findElement(By.tagName("button"));

        nameInputBox.click();
        nameInputBox.clear();
        nameInputBox.sendKeys("new test role");

        submitButton.click();
        Assertions.assertEquals("OS2rollekatalog", driver.getTitle());

        return driver.getCurrentUrl().substring(driver.getCurrentUrl().lastIndexOf("/") + 1);
    }

    public void viewUserRole(String id) {
        driver.get(url + "/ui/userroles/view/" + id);
        Assertions.assertEquals("OS2rollekatalog", driver.getTitle());
    }

    public void listUserRoles() {
        driver.get(url + "/ui/userroles/list");
        Assertions.assertEquals("OS2rollekatalog", driver.getTitle());
    }
}
