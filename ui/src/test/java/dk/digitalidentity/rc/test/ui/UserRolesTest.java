package dk.digitalidentity.rc.test.ui;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

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
        // POST /ui/userroles/new redirects to /ui/userroles/edit/{id} on success.
        // Without this wait, reading getCurrentUrl() raced the navigation and occasionally
        // returned the /new URL, which then caused viewUserRole("new") to hit the error page.
        new WebDriverWait(driver, Duration.ofSeconds(15))
                .until(ExpectedConditions.urlContains("/ui/userroles/edit/"));
        Assertions.assertEquals("OS2rollekatalog", driver.getTitle());

        String currentUrl = driver.getCurrentUrl();
        return currentUrl.substring(currentUrl.lastIndexOf("/") + 1);
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
