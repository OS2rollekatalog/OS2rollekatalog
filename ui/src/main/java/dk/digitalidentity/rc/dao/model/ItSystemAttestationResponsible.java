package dk.digitalidentity.rc.dao.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "it_system_attestation_responsible")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItSystemAttestationResponsible {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "itsystem_id", nullable = false)
	private ItSystem itSystem;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_uuid", nullable = false)
	private User user;

}
