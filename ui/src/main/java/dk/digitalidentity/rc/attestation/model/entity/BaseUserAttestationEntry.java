package dk.digitalidentity.rc.attestation.model.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import java.time.ZonedDateTime;

@Getter
@Setter
@ToString
@MappedSuperclass
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseUserAttestationEntry {

    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String remarks;

    @Column
    private String userUuid;

    @Column
    private ZonedDateTime createdAt;

    @Column
    private String performedByUserId;

    @Column
    private String performedByUserUuid;

    @ManyToOne
    @JoinColumn(name="attestation_id", nullable=false)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Attestation attestation;

}
