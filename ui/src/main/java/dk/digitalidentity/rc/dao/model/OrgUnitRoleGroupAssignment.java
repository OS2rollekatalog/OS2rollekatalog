package dk.digitalidentity.rc.dao.model;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import dk.digitalidentity.rc.dao.model.enums.ContainsTitles;
import dk.digitalidentity.rc.dao.serializer.LocalDateAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity(name = "ou_rolegroups")
@ToString(exclude = { "orgUnit" })
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Getter
@Setter
public class OrgUnitRoleGroupAssignment implements OrgUnitAssignment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ou_uuid")
	private OrgUnit orgUnit;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "rolegroup_id")
	private RoleGroup roleGroup;

	@Column
	private boolean inherit;

	@Column
	private String assignedByUserId;

	@Column
	private String assignedByName;

	@Column
	private Date assignedTimestamp;

    @Convert(converter = LocalDateAttributeConverter.class)
	@Column
	private LocalDate startDate;

    @Convert(converter = LocalDateAttributeConverter.class)
	@Column
	private LocalDate stopDate;

	@Column
	private String stopDateUser;

	@Column
	private boolean inactive;

	@Column
	private boolean manager;

	@Column
	private boolean substitutes;

	@OneToMany
	@JoinTable(name = "ou_rolegroups_excepted_users", joinColumns = @JoinColumn(name = "ou_rolegroups_id"), inverseJoinColumns = @JoinColumn(name = "user_uuid"))
	private List<User> exceptedUsers;

	@OneToMany
	@JoinTable(name = "ou_rolegroups_titles", joinColumns = @JoinColumn(name = "ou_rolegroups_id"), inverseJoinColumns = @JoinColumn(name = "title_uuid"))
	private List<Title> titles;

	@OneToMany
	@JoinTable(name = "ou_rolegroups_functions", joinColumns = @JoinColumn(name = "ou_rolegroups_id"), inverseJoinColumns = @JoinColumn(name = "function_uuid"))
	private List<Function> functions;

	@Column
	private boolean containsFunctions;

	@Column
	public boolean containsExceptedUsers;

	@Column
	private String caseNumber;

	@Column
	@Enumerated(EnumType.ORDINAL)
	//Default value allows for documentations test to run unchanged since ContainsTitle changed to enum
	public ContainsTitles containsTitles = ContainsTitles.NO;
}
