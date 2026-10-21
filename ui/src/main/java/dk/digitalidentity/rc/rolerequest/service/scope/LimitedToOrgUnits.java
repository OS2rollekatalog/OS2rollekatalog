package dk.digitalidentity.rc.rolerequest.service.scope;

import java.util.Set;

public record LimitedToOrgUnits(LimitedToType type, Set<String> orgUnits) {}
