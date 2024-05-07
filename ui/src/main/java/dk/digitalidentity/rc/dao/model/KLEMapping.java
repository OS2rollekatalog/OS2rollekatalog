package dk.digitalidentity.rc.dao.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.log.AuditLoggable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(exclude = { "orgUnit", "id" })
@Entity(name = "ou_kles")
public class KLEMapping implements AuditLoggable {

	@Id
	@Column(name = "id")
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@JsonBackReference
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name="ou_uuid")
	private OrgUnit orgUnit;

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
