package dk.digitalidentity.rc.controller.rest.model;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.UserRoleView;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

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
	private boolean readOnly;

	// Constructor til at mappe fra UserRoleView
	public UserRoleViewDTO(UserRoleView view) {
		this.id = view.getId();
		this.name = view.getName();
		this.description = view.getDescription();
		this.itSystemId = view.getItSystemId();
		this.itSystemName = view.getItSystemName();
		this.itSystemType = view.getItSystemType();
		this.canRequest = view.isCanRequest();
		this.pendingSync = view.isPendingSync();
		this.syncFailed = view.isSyncFailed();
		this.delegatedFromCvr = view.getDelegatedFromCvr();
		this.readOnly = view.isReadOnly();
	}
}