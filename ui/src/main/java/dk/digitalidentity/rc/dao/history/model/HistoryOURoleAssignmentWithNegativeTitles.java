package dk.digitalidentity.rc.dao.history.model;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import dk.digitalidentity.rc.config.StringListConverter;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "history_role_assignment_negative_titles")
@Getter
@Setter
public class HistoryOURoleAssignmentWithNegativeTitles implements GenericRoleAssignment {

	@Id
	private long id;
	
	@Column
	private LocalDate dato;
	
	@Column
	private String ouUuid;

	@Column
	@Convert(converter = StringListConverter.class)
	private List<String> titleUuids;
	
	@Column
	private long roleId;

	@Column
	private String roleName;

	@Column
	private long roleItSystemId;

	@Column
	private String roleItSystemName;

	@Column
	private String roleRoleGroup;

	@Column
	private Long roleRoleGroupId;

	@Column
	private String assignedByUserId;
	
	@Column
	private String assignedByName;
	
	@Column
	private Date assignedWhen;

	@Column
	private LocalDate startDate;

	@Column
	private LocalDate stopDate;

	@Enumerated(EnumType.STRING)
	@Column
	private AssignedThrough assignedThroughType;

	@Column
	private String assignedThroughUuid;

	@Column
	private String assignedThroughName;

	@Column
	private Boolean inherit;

}
