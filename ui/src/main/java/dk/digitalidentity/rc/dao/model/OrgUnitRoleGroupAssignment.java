package dk.digitalidentity.rc.dao.model;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

import org.hibernate.annotations.BatchSize;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import dk.digitalidentity.rc.dao.serializer.LocalDateAttributeConverter;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

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