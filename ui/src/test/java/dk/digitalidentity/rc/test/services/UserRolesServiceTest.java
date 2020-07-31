package dk.digitalidentity.rc.test.services;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.UserRoleService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import javax.transaction.Transactional;
import java.util.ArrayList;

@RunWith(SpringRunner.class)
@Transactional
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(locations = "classpath:test.properties")
@ActiveProfiles({"test"})
public class UserRolesServiceTest {
    private static long ROLE_ID;
    private static String ROLE_NAME = "Role#1";

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private ItSystemService itSystemService;

    @Test
    public void readUserRole() {
        UserRole role = userRoleService.getById(ROLE_ID);
        Assert.assertNotNull(role);

        Assert.assertEquals(role.getId(), ROLE_ID);
    }

    @Test
    public void updateUserRole() {
        UserRole firstRead = userRoleService.getById(ROLE_ID);
        firstRead.setName("Role#2");
        userRoleService.save(firstRead);

        UserRole secondRead = userRoleService.getById(ROLE_ID);
        Assert.assertEquals("Role#2", secondRead.getName());
        secondRead.setName(ROLE_NAME);
        userRoleService.save(secondRead);

        UserRole thirdRead = userRoleService.getById(ROLE_ID);
        Assert.assertEquals(ROLE_NAME, thirdRead.getName());
    }

    @Before
    public void setup() {
        UserRole userRole = getTestRole();
        userRole = userRoleService.save(userRole);
        ROLE_ID = userRole.getId();
    }

    private UserRole getTestRole() {
        UserRole role = new UserRole();
        role.setName(ROLE_NAME);
        role.setIdentifier("identifier");
        role.setSystemRoleAssignments(new ArrayList<SystemRoleAssignment>());
        role.setId(10L);

        ItSystem itSystem = itSystemService.getAll().get(0);
        role.setItSystem(itSystem);
        return role;
    }
}