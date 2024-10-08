package dk.digitalidentity.rc.controller.mvc.datatables.dao.model;

import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "view_datatables_userroles")
public class UserRoleView {
	
	@Id
	@Column
	private long id;

	@Column
	private String name;

	@Column
	private String description;

	@Column
	private long itSystemId;
	
	@Column
	private String itSystemName;

	@Enumerated(EnumType.STRING)
	@Column
	private ItSystemType itSystemType;

	@Column
	private boolean canRequest;

	@Column
	private boolean pendingSync;

	@Column
	private boolean syncFailed;

	@Column
	private String delegatedFromCvr;

}
