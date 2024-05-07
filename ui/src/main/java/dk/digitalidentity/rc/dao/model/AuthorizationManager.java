package dk.digitalidentity.rc.dao.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dk.digitalidentity.rc.log.AuditLoggable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "ou_authorization_managers")
@Getter
@Setter
public class AuthorizationManager implements AuditLoggable {

    @Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_uuid", nullable = true)
    private User user;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ou_uuid", nullable = true)
    private OrgUnit orgUnit;

	@JsonIgnore
	@Override
	public String getEntityName() {
		return user.getName();
	}

	@JsonIgnore
	@Override
	public String getEntityId() {
		return user.getUuid();
	}
}
