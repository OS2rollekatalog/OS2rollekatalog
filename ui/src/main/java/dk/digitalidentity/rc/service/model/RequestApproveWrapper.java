package dk.digitalidentity.rc.service.model;

import dk.digitalidentity.rc.dao.model.RequestApprove;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.UserRole;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestApproveWrapper {
	private RequestApprove request;
	private UserRole userRole;
	private RoleGroup roleGroup;
	private String roleName;
	private String roleDescription;
	private String itSystemName;
	private String childJson;
}
