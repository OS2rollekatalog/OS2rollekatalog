package dk.digitalidentity.rc.event;

import dk.digitalidentity.rc.dao.model.User;

public interface UserAssignmentsChangedListener {
	void onUserAssignmentsChanged(User user);
}
