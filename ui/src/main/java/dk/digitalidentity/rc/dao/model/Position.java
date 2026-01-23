package dk.digitalidentity.rc.dao.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dk.digitalidentity.rc.log.AuditLoggable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.util.Date;

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

	@ManyToOne(fetch = FetchType.LAZY)
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

	@Column
	@CreationTimestamp
	private Date created;

	@Column(name = "primary_position")
	private boolean isPrimary;

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
