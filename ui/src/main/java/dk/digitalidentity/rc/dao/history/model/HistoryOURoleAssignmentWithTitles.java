package dk.digitalidentity.rc.dao.history.model;

import dk.digitalidentity.rc.config.StringListConverter;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "history_role_assignment_titles")
@Getter
@Setter
public class HistoryOURoleAssignmentWithTitles {

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
}
