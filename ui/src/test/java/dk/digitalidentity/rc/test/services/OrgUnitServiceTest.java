package dk.digitalidentity.rc.test.services;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.service.OrgUnitService;
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
public class OrgUnitServiceTest {
    private static String ORG_UNIT_UUID = "c76d0f26-fb18-4af0-8ff8-26a29b521175";
    private static String ORG_UNIT_NAME = "OrgUnit#1";

    @Autowired
    private OrgUnitService orgUnitService;

    @Test
    public void readOu() {
        OrgUnit ou = orgUnitService.getByUuid(ORG_UNIT_UUID);
        Assert.assertNotNull(ou);

        Assert.assertEquals(ou.getUuid(), ORG_UNIT_UUID);
    }

    @Test
    public void updateOu() {
        OrgUnit firstRead = orgUnitService.getByUuid(ORG_UNIT_UUID);
        firstRead.setName("OrgUnit#2");
        orgUnitService.save(firstRead);

        OrgUnit secondRead = orgUnitService.getByUuid(ORG_UNIT_UUID);
        Assert.assertEquals("OrgUnit#2", secondRead.getName());
        secondRead.setName(ORG_UNIT_NAME);
        orgUnitService.save(secondRead);

        OrgUnit thirdRead = orgUnitService.getByUuid(ORG_UNIT_UUID);
        Assert.assertEquals(ORG_UNIT_NAME, thirdRead.getName());
    }

    @Before
    public void setup() {
        OrgUnit ou = getTestOrgUnit();

        orgUnitService.save(ou);
    }

    private static OrgUnit getTestOrgUnit() {
        OrgUnit orgUnit = new OrgUnit();
        orgUnit.setActive(true);
        orgUnit.setName(ORG_UNIT_NAME);
        orgUnit.setChildren(new ArrayList<OrgUnit>());
        orgUnit.setUuid(ORG_UNIT_UUID);
        orgUnit.setParent(null);
        return orgUnit;
    }
}
