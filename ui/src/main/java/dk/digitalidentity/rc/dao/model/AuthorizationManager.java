package dk.digitalidentity.rc.dao.model;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dk.digitalidentity.rc.log.AuditLoggable;
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
