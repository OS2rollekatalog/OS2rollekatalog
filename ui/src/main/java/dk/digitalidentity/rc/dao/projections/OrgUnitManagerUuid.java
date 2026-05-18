package dk.digitalidentity.rc.dao.projections;

public interface OrgUnitManagerUuid {
    String getUuid();
    ManagerUuid getManager();

    interface ManagerUuid {
        String getUuid();
    }
}