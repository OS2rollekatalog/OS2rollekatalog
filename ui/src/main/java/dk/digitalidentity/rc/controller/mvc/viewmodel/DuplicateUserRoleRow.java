package dk.digitalidentity.rc.controller.mvc.viewmodel;

/**
 * Slankt view-objekt til dubletrapporten (USERS_WITH_DUPLICATE_USERROLE_ASSIGNMENTS).
 * Indeholder kun de felter tabellen viser, så listen kan inlines som JSON-array i templaten
 * (UserWithDuplicateRoleAssignmentDTO holder hele UserRole-entiteten og kan ikke serialiseres sikkert).
 */
public record DuplicateUserRoleRow(String name, String userId, String uuid,
                                   String roleName, String itSystemName, String message) {
}
