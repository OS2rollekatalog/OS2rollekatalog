package dk.digitalidentity.rc.controller.mvc.xlsview;

import java.util.Date;

import dk.digitalidentity.rc.dao.model.UserRole;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Assignment {
	private UserRole userRole;
	private String assignedBy;
	private String assignedThrough;
	private Date assignedTime;
}
