package dk.digitalidentity.rc.util;

import java.util.UUID;

public class IdentifierGenerator {

	public static String buildKombitIdentifier(String identifier, String domain) {
		return domain + "roles/jobrole/" + identifier + "/1";
	}
	
	public static String buildKombitConstraintIdentifier(String domain) {
		return domain + "id-" + UUID.randomUUID().toString() + "/1/parametric";
	}
}
