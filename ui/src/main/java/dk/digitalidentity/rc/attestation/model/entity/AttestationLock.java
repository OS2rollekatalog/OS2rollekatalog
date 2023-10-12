package dk.digitalidentity.rc.attestation.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;
import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "attestation_locks")
public class AttestationLock {
    @Id
    private String lockId;
    @Column
    @Version
    private Long version;
    @Column
    private LocalDateTime acquiredAt;
}
