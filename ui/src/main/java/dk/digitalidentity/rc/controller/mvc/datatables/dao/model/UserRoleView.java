package dk.digitalidentity.rc.controller.mvc.datatables.dao.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;

import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
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
	private boolean pendingNemloginSync;

	@Column
	private boolean syncNemloginFailed;

	@Column
	private String delegatedFromCvr;

}
