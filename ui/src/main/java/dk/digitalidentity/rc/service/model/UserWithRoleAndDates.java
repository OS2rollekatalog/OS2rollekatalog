package dk.digitalidentity.rc.service.model;

import java.time.LocalDate;

import dk.digitalidentity.rc.dao.model.User;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserWithRoleAndDates {
	private User user;
	private AssignedThrough assignedThrough;
	private LocalDate startDate;
	private LocalDate stopDate;
}
