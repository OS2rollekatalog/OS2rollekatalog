package dk.digitalidentity.rc.dao.model.assignment;

public interface CurrentAssignmentSmallProjection {
    Long getId();
    
    String getUserId();
    long getUserRoleId();
    long getUserDomainId();
}
