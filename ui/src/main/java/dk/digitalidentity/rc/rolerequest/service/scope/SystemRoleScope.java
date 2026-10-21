package dk.digitalidentity.rc.rolerequest.service.scope;

/**
 * OUs and the it-systems allowed within them.
 */
public record SystemRoleScope(LimitedToOrgUnits orgUnits, LimitedToItSystems itSystems) {}
