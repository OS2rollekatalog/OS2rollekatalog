package dk.digitalidentity.rc.test.services;

import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.UserService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.UUID;

import javax.transaction.Transactional;

@RunWith(SpringRunner.class)
@Transactional
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(locations = "classpath:test.properties")
@ActiveProfiles({"test"})
public class UserServiceTest {
    private static String USER_UUID = "057f2d05-8648-49b6-8b81-7ec9d49d03c7";
    private static String USER_NAME = "User#1";

    @Autowired
    private UserService userService;

    @Test
    public void readUser() {
        User user = userService.getByUuid(USER_UUID);
        Assert.assertNotNull(user);

        Assert.assertEquals(user.getUuid(), USER_UUID);
    }

    @Test
    public void updateUser() {
        User firstRead = userService.getByUuid(USER_UUID);
        firstRead.setName("OrgUnit#2");
        userService.save(firstRead);

        User secondRead = userService.getByUuid(USER_UUID);
        Assert.assertEquals("OrgUnit#2", secondRead.getName());
        secondRead.setName(USER_NAME);
        userService.save(secondRead);

        User thirdRead = userService.getByUuid(USER_UUID);
        Assert.assertEquals(USER_NAME, thirdRead.getName());
    }


    @Before
    public void setup() {
        User user = getTestUser();
        userService.save(user);
    }

    private static User getTestUser() {
        User user = new User();
        user.setActive(true);
        user.setName(USER_NAME);
        user.setUuid(USER_UUID);
        user.setExtUuid(UUID.randomUUID().toString());

        return user;
    }
}
