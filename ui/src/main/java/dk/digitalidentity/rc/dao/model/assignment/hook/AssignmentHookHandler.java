package dk.digitalidentity.rc.dao.model.assignment.hook;

public interface AssignmentHookHandler {
	void handleEvent(HookEvent hookEvent);
}
