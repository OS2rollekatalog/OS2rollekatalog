package dk.digitalidentity.rc.dao.model;

import java.time.LocalDate;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import org.hibernate.annotations.BatchSize;

import dk.digitalidentity.rc.dao.serializer.LocalDateAttributeConverter;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "user_rolegroups")
@Getter
@Setter
public class UserRoleGroupAssignment {

    @Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_uuid")
	private User user;
	
	@BatchSize(size = 50)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "rolegroup_id")
	private RoleGroup roleGroup;
	
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
	private boolean inactive;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ou_uuid")
	private OrgUnit orgUnit;
}
