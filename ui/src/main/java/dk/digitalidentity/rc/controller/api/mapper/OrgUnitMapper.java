package dk.digitalidentity.rc.controller.api.mapper;

import dk.digitalidentity.rc.controller.api.model.OrgUnitShallowAM;
import dk.digitalidentity.rc.dao.model.OrgUnit;

public abstract class OrgUnitMapper {

    public static OrgUnitShallowAM toShallowApi(final OrgUnit orgUnit) {
        if (orgUnit == null) {
            return null;
        }
        return OrgUnitShallowAM.builder()
                .name(orgUnit.getName())
                .uuid(orgUnit.getUuid())
                .build();
    }

}
