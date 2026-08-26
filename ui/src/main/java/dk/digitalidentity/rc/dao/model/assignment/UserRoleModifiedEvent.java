package dk.digitalidentity.rc.dao.model.assignment;

import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Component
@AllArgsConstructor
@Getter
public class UserRoleModifiedEvent {
	private long userRoleId;
}
