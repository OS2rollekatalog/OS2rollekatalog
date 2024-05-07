package dk.digitalidentity.rc.test.ui;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public class RoleGroupsTest extends SeleniumTest {

    @Test
    public void roleGroupsTest() {
        String roleGroupId = newRoleGroup();
        viewRoleGroup(roleGroupId);
        listRoleGroups();
    }

    public String newRoleGroup() {
        driver.get(url + "/ui/rolegroups/new");
        Assertions.assertEquals("OS2rollekatalog", driver.getTitle());
        WebElement nameInputBox = driver.findElement(By.id("name"));
        WebElement submitButton = driver.findElement(By.tagName("button"));

        nameInputBox.click();
        nameInputBox.clear();
        nameInputBox.sendKeys("Test rolegroup");

        submitButton.click();
        Assertions.assertEquals("OS2rollekatalog", driver.getTitle());
        return driver.getCurrentUrl().substring(driver.getCurrentUrl().lastIndexOf("/") + 1);
    }

    public void viewRoleGroup(String roleGroupId) {
        driver.get(url + "/ui/rolegroups/view/" + roleGroupId);
        Assertions.assertEquals("OS2rollekatalog", driver.getTitle());
    }

    public void listRoleGroups() {
        driver.get(url + "/ui/rolegroups/list");
        Assertions.assertEquals("OS2rollekatalog", driver.getTitle());
    }
}
