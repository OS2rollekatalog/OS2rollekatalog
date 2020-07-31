package dk.digitalidentity.rc.dao.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonBackReference;

import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.log.AuditLoggable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "user_kles")
@Getter
@Setter
@EqualsAndHashCode(exclude = { "user", "id" })
public class UserKLEMapping implements AuditLoggable {

	@Id
	@Column(name = "id")
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@JsonBackReference
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="user_uuid")
	private User user;

	@Column(name = "code")
	private String code;

	@Enumerated(EnumType.STRING)
	@Column(name = "assignment_type")
	private KleType assignmentType;

	@Override
	public String getEntityId() {
		return code;
	}

	@Override
	public String getEntityName() {
		return code;
	}
}
