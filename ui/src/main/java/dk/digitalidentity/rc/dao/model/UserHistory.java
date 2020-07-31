package dk.digitalidentity.rc.dao.model;

import java.util.Date;

import dk.digitalidentity.rc.dao.model.enums.EntityType;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserHistory {
	private Date timestamp;
	private String username;
	private EventType eventType;
	private EntityType entityType;
	private String roleName;
	private String systemName; 
}
