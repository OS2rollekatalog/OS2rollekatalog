package dk.digitalidentity.rc.controller.rest.model;

import java.util.List;
import java.util.Set;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.UserRoleView;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRoleViewDTO {
	private long id;
	private String name;
	private String description;
	private long itSystemId;
	private String itSystemName;
	private ItSystemType itSystemType;
	private boolean canRequest;
	private boolean pendingSync;
	private boolean syncFailed;
	private String delegatedFromCvr;
	private List<String> adGroupNames;
	private ItemPermissionDTO allowedActions = new ItemPermissionDTO();
	private boolean readOnly;
	private Set<RequestableBy> effectiveRequesterPermission;
	private Set<ApprovableBy> effectiveApproverPermission;

	// Constructor til at mappe fra UserRoleView
	public UserRoleViewDTO(UserRoleView view) {
		this.id = view.getId();
		this.name = view.getName();
		this.description = view.getDescription();
		this.itSystemId = view.getItSystemId();
		this.itSystemName = view.getItSystemName();
		this.itSystemType = view.getItSystemType();
		this.pendingSync = view.isPendingSync();
		this.syncFailed = view.isSyncFailed();
		this.delegatedFromCvr = view.getDelegatedFromCvr();
		this.readOnly = view.isReadOnly();
		this.effectiveApproverPermission = view.getEffectiveApproverPermission();
		this.effectiveRequesterPermission = view.getEffectiveRequesterPermission();
	}
}
