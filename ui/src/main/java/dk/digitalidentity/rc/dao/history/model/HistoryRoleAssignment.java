package dk.digitalidentity.rc.dao.history.model;

import dk.digitalidentity.rc.service.model.AssignedThrough;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDate;
import java.util.Date;

@Entity
@Table(name = "history_role_assignments")
@Getter
@Setter
public class HistoryRoleAssignment {

	@Id
	private long id;
	
	@Column
	private LocalDate dato;
	
	@Column
	private String userUuid;
	
	@Column
	private long roleId;

	@Column
	private long roleItSystemId;

	@Column
	private String roleRoleGroup;

	@Column
	private Long roleRoleGroupId;

	@Enumerated(EnumType.STRING)
	@Column
	private AssignedThrough assignedThroughType;
	
	@Column
	private String assignedThroughUuid;

	@Column
	private String assignedThroughName;

	@Column
	private String assignedByUserId;
	
	@Column
	private String assignedByName;
	
	@Column
	private Date assignedWhen;
	
	@Column
	private String postponedConstraints;

	@Column(name = "ou_uuid")
	private String orgUnitUuid;
	
	@Column(name = "notify_by_email_if_manual_system")
	private boolean notifyByEmailIfManualSystem = true;

}
