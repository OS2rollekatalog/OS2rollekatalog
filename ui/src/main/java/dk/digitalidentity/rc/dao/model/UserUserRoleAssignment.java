package dk.digitalidentity.rc.dao.model;

import dk.digitalidentity.rc.dao.serializer.LocalDateAttributeConverter;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Entity(name = "user_roles_mapping")
@Getter
@Setter
public class UserUserRoleAssignment {

    @Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_uuid")
	private User user;

	@BatchSize(size = 50)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "role_id")
	private UserRole userRole;
	
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
	
	@OneToMany(mappedBy = "userUserRoleAssignment", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<PostponedConstraint> postponedConstraints;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ou_uuid")
	private OrgUnit orgUnit;
	
	@Column(name = "notify_by_email_if_manual_system")
	private boolean notifyByEmailIfManualSystem = true;
}
