package dk.digitalidentity.rc.dao.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dk.digitalidentity.rc.dao.serializer.LocalDateAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Entity(name = "ou_rolegroups")
@ToString(exclude = { "orgUnit" })
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Getter
@Setter
public class OrgUnitRoleGroupAssignment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ou_uuid")
	private OrgUnit orgUnit;
	
	@BatchSize(size = 50)
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

	@OneToMany
	@JoinTable(name = "ou_rolegroups_excepted_users", joinColumns = @JoinColumn(name = "ou_rolegroups_id"), inverseJoinColumns = @JoinColumn(name = "user_uuid"))
	private List<User> exceptedUsers;
	
	@OneToMany
	@JoinTable(name = "ou_rolegroups_titles", joinColumns = @JoinColumn(name = "ou_rolegroups_id"), inverseJoinColumns = @JoinColumn(name = "title_uuid"))
	private List<Title> titles;

	@Column
	public boolean containsExceptedUsers;
	
	@Column
	public boolean containsTitles;
}