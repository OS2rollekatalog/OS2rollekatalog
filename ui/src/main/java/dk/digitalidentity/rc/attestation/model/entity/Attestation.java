package dk.digitalidentity.rc.attestation.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "attestation_attestation")
public class Attestation {
    public enum AttestationType {
        ORGANISATION_ATTESTATION,
        IT_SYSTEM_ATTESTATION, // This is the user assignments attestation
        IT_SYSTEM_ROLES_ATTESTATION, // This is the user roles attestation
        MANAGER_DELEGATED_ATTESTATION, // Attestation for manager delegates
    }
    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name="attestation_run_id", nullable=false)
    private AttestationRun attestationRun;

    @Column
    private String uuid;
    @Column
    private LocalDate createdAt;
    @Column
    @Enumerated(EnumType.STRING)
    private AttestationType attestationType;

    // This field have been moved to AttestationRun but kept here for backwards compatability
    // Can be safely removed af 2024r2 release
    @Column
    @Deprecated
    private LocalDate deadline;
    @Column
    private ZonedDateTime verifiedAt;
    @Column
    private Long itSystemId;
    @Column
    private String itSystemName;
    @Column
    private String responsibleOuUuid;
    @Column
    private String responsibleOuName;
    @Column
    private String responsibleUserUuid;
    @Column
    private String responsibleUserId;

    // This field have been moved to AttestationRun but kept here for backwards compatability
    // Can be safely removed af 2024r2 release
    @Deprecated
    @Column(name = "`sensitive`")
    private boolean sensitive;

    @Builder.Default
    @OneToMany(mappedBy = "attestation", fetch = FetchType.LAZY)
    @ToString.Exclude
    private Set<OrganisationUserAttestationEntry> organisationUserAttestationEntries = new HashSet<>();

    @OneToOne(mappedBy = "attestation")
    @PrimaryKeyJoinColumn
    private OrganisationRoleAttestationEntry organisationRolesAttestationEntry;

    @Builder.Default
    @ToString.Exclude
    @OneToMany(mappedBy = "attestation", fetch = FetchType.LAZY)
    @BatchSize(size = 500)
    private Set<ItSystemUserAttestationEntry> itSystemUserAttestationEntries = new HashSet<>();

    @Builder.Default
    @ToString.Exclude
    @OneToMany(mappedBy = "attestation", fetch = FetchType.LAZY)
    @BatchSize(size = 500)
    private Set<ItSystemRoleAttestationEntry> itSystemUserRoleAttestationEntries = new HashSet<>();

    @Builder.Default
    @ToString.Exclude
    @OneToMany(mappedBy = "attestation", fetch = FetchType.LAZY)
    @BatchSize(size = 500)
    private Set<ItSystemOrganisationAttestationEntry> itSystemOrganisationAttestationEntries = new HashSet<>();

    @Builder.Default
    @ToString.Exclude
    @OneToMany(mappedBy = "attestation", fetch = FetchType.LAZY)
    @BatchSize(size = 500)
    private Set<AttestationUser> usersForAttestation = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "attestation", fetch = FetchType.LAZY)
    @ToString.Exclude
    @BatchSize(size = 500)
    private Set<AttestationMail> mails = new HashSet<>();

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
