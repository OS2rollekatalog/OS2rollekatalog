package dk.digitalidentity.rc.rolerequest.service.scope;

import java.util.Set;

public record LimitedToItSystems(LimitedToType type, Set<Long> itSystems) {}
