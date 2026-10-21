package dk.digitalidentity.rc.rolerequest;

public interface RequestConstants {

	String CACHE_PREFIX = "rolerequest#";

	// must match the VARCHAR(768) reason columns on requests and assignment reasons
	int REASON_MAX_LENGTH = 768;

}
