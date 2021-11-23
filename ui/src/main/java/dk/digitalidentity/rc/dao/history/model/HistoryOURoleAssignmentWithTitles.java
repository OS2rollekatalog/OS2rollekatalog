package dk.digitalidentity.rc.dao.history.model;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import dk.digitalidentity.rc.config.StringListConverter;
import lombok.Getter;

@Entity
@Table(name = "history_role_assignment_titles")
@Getter
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
	private String assignedByUserId;
	
	@Column
	private String assignedByName;
	
	@Column
	private Date assignedWhen;
}
