package dk.digitalidentity.rc.service.model;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignedByWhen {
	private String assignedBy;
	private Date assignedTime;
	private long roleId;
}
