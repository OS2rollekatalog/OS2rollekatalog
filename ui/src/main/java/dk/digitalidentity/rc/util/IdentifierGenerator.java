package dk.digitalidentity.rc.util;

import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;

public class IdentifierGenerator {

	public static String buildKombitIdentifier(String identifier, String domain) {
		return domain + "roles/jobrole/" + identifier + "/1";
	}
	
	public static String buildKombitConstraintIdentifier(String domain, SystemRole systemRole, SystemRoleAssignment assignment, ConstraintType constraintType) {
		return domain + "id-" + systemRole.getId() + "-" + assignment.getId() + "-" + constraintType.getId() + "/1/parametric";
	}
}
