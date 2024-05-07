package dk.digitalidentity.rc.dao.history.model;

import dk.digitalidentity.rc.service.model.AssignedThrough;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

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

	@Column
	private LocalDate startDate;
	@Column
	private LocalDate stopDate;
}
