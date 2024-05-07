package dk.digitalidentity.rc.attestation.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashSet;
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
        IT_SYSTEM_ATTESTATION,
        IT_SYSTEM_ROLES_ATTESTATION
    }
    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column
    private String uuid;
    @Column
    private LocalDate createdAt;
    @Column
    @Enumerated(EnumType.STRING)
    private AttestationType attestationType;
    @Column
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
    @OneToMany(mappedBy = "attestation", fetch = FetchType.LAZY)
    @ToString.Exclude
    private Set<ItSystemUserAttestationEntry> itSystemUserAttestationEntries = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "attestation", fetch = FetchType.LAZY)
    @ToString.Exclude
    private Set<ItSystemRoleAttestationEntry> itSystemUserRoleAttestationEntries = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "attestation", fetch = FetchType.LAZY)
    @ToString.Exclude
    private Set<ItSystemOrganisationAttestationEntry> itSystemOrganisationAttestationEntries = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "attestation", fetch = FetchType.LAZY)
    @ToString.Exclude
    private Set<AttestationUser> usersForAttestation = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "attestation", fetch = FetchType.LAZY)
    @ToString.Exclude
    private Set<AttestationMail> mails = new HashSet<>();

}
