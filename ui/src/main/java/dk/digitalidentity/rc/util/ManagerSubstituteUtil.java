package dk.digitalidentity.rc.util;

import dk.digitalidentity.rc.dao.model.ManagerSubstitute;

public final class ManagerSubstituteUtil {

	private ManagerSubstituteUtil() {}

	/** Nothing deletes the row when the org unit changes manager, so it must be ignored from then on. */
	public static boolean isActive(ManagerSubstitute managerSubstitute) {
		if (managerSubstitute.getOrgUnit() == null
				|| managerSubstitute.getManager() == null
				|| managerSubstitute.getOrgUnit().getManager() == null) {
			return false;
		}

		// case-insensitive like ManagerSubstituteAssignmentRule, which guards the same way
		return managerSubstitute.getManager().getUuid() != null
				&& managerSubstitute.getManager().getUuid().equalsIgnoreCase(managerSubstitute.getOrgUnit().getManager().getUuid());
	}
}
