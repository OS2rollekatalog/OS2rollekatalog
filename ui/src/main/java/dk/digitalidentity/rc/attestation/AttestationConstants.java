package dk.digitalidentity.rc.attestation;

import java.time.ZoneId;

public interface AttestationConstants {

    String CACHE_PREFIX = "attestation";
    ZoneId CET_ZONE_ID = ZoneId.of("Europe/Copenhagen");

    String REPORT_LOCK_NAME="report";

    // How many days after deadline before an attestation run is considered finished
    Integer FINISHED_DAYS_AFTER_DEADLINE = 15;
}
