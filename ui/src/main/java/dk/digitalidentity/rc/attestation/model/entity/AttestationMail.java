package dk.digitalidentity.rc.attestation.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.time.ZonedDateTime;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "attestation_mail")
public class AttestationMail {
    public enum MailType {
        INFORMATION, // 20 days before deadline
        REMINDER_1,  // 10 days before deadline
        REMINDER_2,  // 3 days before deadline
        ESCALATION_REMINDER // 5 after deadline
    }

    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    @Enumerated(EnumType.STRING)
    private MailType emailType;

    @Column
    @Enumerated(EnumType.STRING)
    private EmailTemplateType emailTemplateType;

    @Column
    private ZonedDateTime sentAt;

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name="attestation_id", nullable=false)
    private Attestation attestation;

}
