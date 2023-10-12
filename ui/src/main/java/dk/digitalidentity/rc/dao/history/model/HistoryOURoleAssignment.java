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
@Table(name = "history_ou_role_assignments")
@Getter
@Setter
public class HistoryOURoleAssignment {

	@Id
	private long id;
	
	@Column
	private LocalDate dato;
	
	@Column
	private String ouUuid;
	
	@Column
	private long roleId;

	@Column
	private Boolean inherit;

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
}
