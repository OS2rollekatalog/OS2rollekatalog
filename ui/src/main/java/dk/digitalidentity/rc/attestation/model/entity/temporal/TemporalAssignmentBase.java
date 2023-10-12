package dk.digitalidentity.rc.attestation.model.entity.temporal;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
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
