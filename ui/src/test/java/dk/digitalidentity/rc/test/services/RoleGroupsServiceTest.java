package dk.digitalidentity.rc.test.services;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.service.RoleGroupService;
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

@RunWith(SpringRunner.class)
@Transactional
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(locations = "classpath:test.properties")
@ActiveProfiles({"test"})
public class RoleGroupsServiceTest {
    private static long ROLEGROUP_ID;
    private static String ROLEGROUP_NAME = "RoleGroup#1";

    @Autowired
    private RoleGroupService roleGroupService;

    @Test
    public void readRoleGroup() {
        RoleGroup rolegroup = roleGroupService.getById(ROLEGROUP_ID);
        Assert.assertNotNull(rolegroup);

        Assert.assertEquals(ROLEGROUP_ID, rolegroup.getId());
    }

    @Test
    public void updateRoleGroup() {
        RoleGroup firstRead = roleGroupService.getById(ROLEGROUP_ID);
        firstRead.setName("RoleGroup#2");
        roleGroupService.save(firstRead);

        RoleGroup secondRead = roleGroupService.getById(ROLEGROUP_ID);
        Assert.assertEquals("RoleGroup#2", secondRead.getName());
        secondRead.setName(ROLEGROUP_NAME);
        roleGroupService.save(secondRead);

        RoleGroup thirdRead = roleGroupService.getById(ROLEGROUP_ID);
        Assert.assertEquals(ROLEGROUP_NAME, thirdRead.getName());
    }

    @Before
    public void setup() {
        RoleGroup roleGroup = getTestRolegroup();
        roleGroup = roleGroupService.save(roleGroup);

        ROLEGROUP_ID = roleGroup.getId();
    }

    private static RoleGroup getTestRolegroup() {
        RoleGroup rolegroup = new RoleGroup();
        rolegroup.setName(ROLEGROUP_NAME);

        return rolegroup;
    }
}