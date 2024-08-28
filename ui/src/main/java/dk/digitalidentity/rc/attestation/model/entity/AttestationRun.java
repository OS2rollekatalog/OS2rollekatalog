package dk.digitalidentity.rc.attestation.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "attestation_run")
public class AttestationRun {
    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private LocalDate createdAt;

    @Column(name = "`sensitive`")
    private boolean sensitive;

    @Column(name = "`super_sensitive`")
    private boolean superSensitive;

    @Column
    private LocalDate deadline;

    @Column
    private boolean finished;

    @Builder.Default
    @OneToMany(mappedBy = "attestationRun", fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<Attestation> attestations = new ArrayList<>();
}
