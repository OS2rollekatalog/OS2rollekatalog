package dk.digitalidentity.rc.dao.history.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import dk.digitalidentity.rc.service.model.AssignedThrough;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "history_ou_role_assignments")
@Getter
@Setter
public class HistoryOURoleAssignment implements GenericRoleAssignment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private LocalDate dato;

	@Column(name = "ou_uuid", nullable = false, length = 36)
	private String ouUuid;

	@Column(name = "role_id", nullable = false)
	private long roleId;

	@Column(name = "role_name", nullable = false, length = 128)
	private String roleName;

	@Column(name = "role_it_system_id", nullable = false)
	private Long roleItSystemId;

	@Column(name = "role_it_system_name", nullable = false, length = 64)
	private String roleItSystemName;

	@Column(name = "role_role_group", length = 128)
	private String roleRoleGroup;

	@Enumerated(EnumType.STRING)
	@Column(name = "assigned_through_type", length = 64)
	private AssignedThrough assignedThroughType;

	@Column(name = "assigned_through_uuid", length = 36)
	private String assignedThroughUuid;

	@Column(name = "assigned_through_name", length = 512)
	private String assignedThroughName;

	@Column(name = "role_role_group_id")
	private Long roleRoleGroupId;

	@Column(name = "inherit")
	private Boolean inherit;

	@Column(name = "assigned_by_user_id", nullable = false, length = 255)
	private String assignedByUserId;

	@Column(name = "assigned_by_name", nullable = false, length = 255)
	private String assignedByName;

	@Column(name = "assigned_when", nullable = false)
	private LocalDateTime assignedWhen;

	@Column(name = "start_date")
	private LocalDate startDate;

	@Column(name = "stop_date")
	private LocalDate stopDate;

	@OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<HistoryOURoleAssignmentExclusion> exclusions;

	@Column(name = "manager")
	private Boolean manager;

	@Column(name = "substitutes")
	private Boolean substitutes;
}
