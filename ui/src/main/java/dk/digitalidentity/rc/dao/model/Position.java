package dk.digitalidentity.rc.dao.model;

import java.util.Date;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

import org.hibernate.annotations.CreationTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import dk.digitalidentity.rc.log.AuditLoggable;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity(name = "positions")
@ToString(exclude = { "user" })
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Getter
@Setter
public class Position implements AuditLoggable {

    @Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

	@Column(name = "name")
	private String name;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "ou_uuid")
	private OrgUnit orgUnit;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_uuid")
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "title_uuid")
	private Title title;
	
	@Column(name = "do_not_inherit")
	private boolean doNotInherit;

	@OneToMany(mappedBy = "position", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<PositionUserRoleAssignment> userRoleAssignments;

	@OneToMany(mappedBy = "position", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<PositionRoleGroupAssignment> roleGroupAssignments;

	@Column
	@CreationTimestamp
	private Date created;

	@JsonIgnore
	@Override
	public String getEntityId() {
		return Long.toString(id);
	}

	@Override
	public String getEntityName() {
		return user.getName() + " (" + user.getUserId() + "), " + name;
	}
}
