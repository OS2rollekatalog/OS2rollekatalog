package dk.digitalidentity.rc.dao.history.model;

import dk.digitalidentity.rc.config.StringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "history_role_assignment_excepted_users")
@Getter
@Setter
public class HistoryOURoleAssignmentWithExceptions {

	@Id
	private long id;
	
	@Column
	private LocalDate dato;
	
	@Column
	private String ouUuid;

	@Column
	@Convert(converter = StringListConverter.class)
	private List<String> userUuids;
	
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
}
