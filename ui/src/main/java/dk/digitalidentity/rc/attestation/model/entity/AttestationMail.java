package dk.digitalidentity.rc.attestation.model.entity;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

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
        REMINDER_3, // 5 after deadline
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

    @Builder.Default
    @ToString.Exclude
    @OneToMany(mappedBy = "mail", fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
    @BatchSize(size = 50)
    private List<AttestationMailReceiver> receivers = new ArrayList<>();

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name="attestation_id", nullable=false)
    private Attestation attestation;

}
