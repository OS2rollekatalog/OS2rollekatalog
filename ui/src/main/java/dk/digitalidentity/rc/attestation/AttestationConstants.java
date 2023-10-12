package dk.digitalidentity.rc.attestation;

import java.time.ZoneId;

public interface AttestationConstants {

    String CACHE_PREFIX = "attestation";
    ZoneId CET_ZONE_ID = ZoneId.of("Europe/Copenhagen");

    String REPORT_LOCK_NAME="report";
}
