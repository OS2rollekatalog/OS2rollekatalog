package dk.digitalidentity.rc.dao.history.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "history_it_system_attestation_responsible")
@Getter
@Setter
public class HistoryItSystemAttestationResponsible {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column(name = "user_uuid", nullable = false)
	private String userUuid;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "history_it_systems_id", nullable = false)
	private HistoryItSystem historyItSystem;
}
