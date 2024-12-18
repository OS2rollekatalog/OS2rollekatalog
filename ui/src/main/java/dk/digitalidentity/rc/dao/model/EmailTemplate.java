package dk.digitalidentity.rc.dao.model;


import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.rc.log.AuditLoggable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "email_templates")
public class EmailTemplate implements AuditLoggable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column
	@NotNull
	private String title;

	@Column
	@NotNull
	private String message;
	
	@Column
	@Enumerated(EnumType.STRING)
	@NotNull
	private EmailTemplateType templateType;

	@Column
	private String notes;
	
	@Column
	private boolean enabled;

	@Column
	private Integer daysBeforeEvent;

	@Override
    public String getEntityName() {
	    return "";
    }

    @Override
    public String getEntityId() {
        return Long.toString(id);
    }
}
