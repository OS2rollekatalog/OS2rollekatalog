package dk.digitalidentity.rc.attestation.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.time.ZonedDateTime;

/**
 * This class will contain attestations for the contents of a given user-role on an it-system.
 */
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "attestation_it_system_role_attestation_entry")
public class ItSystemRoleAttestationEntry {
    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name="attestation_id", nullable=false)
    private Attestation attestation;

    @Column
    private String remarks;

    @Column
    private Long userRoleId;

    @Column
    private ZonedDateTime createdAt;

    @Column
    private String performedByUserId;

    @Column
    private String performedByUserUuid;

}
