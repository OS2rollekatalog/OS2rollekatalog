package dk.digitalidentity.rc.attestation.model.entity.temporal;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@MappedSuperclass
public class TemporalAssignmentBase {
    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column
    private LocalDate validFrom;
    @Column
    private LocalDate validTo;
    @Column
    private LocalDate updatedAt;
    @Column
    private String recordHash;
}
