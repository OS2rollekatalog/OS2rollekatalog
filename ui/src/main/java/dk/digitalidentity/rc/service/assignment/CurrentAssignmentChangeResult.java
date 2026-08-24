package dk.digitalidentity.rc.service.assignment;

import dk.digitalidentity.rc.dao.model.User;

import java.util.Set;

/**
 * Resultatet af {@link CurrentAssignmentService#saveAllForUsers}.
 *
 * <ul>
 *   <li>{@code changedUsers} — brugere hvis current-assignments faktisk blev ændret (oprettet/slettet).</li>
 *   <li>{@code affectedUserRoleIds} — id'erne på de UserRoles hvis effektive medlemskab ændrede sig,
 *       udledt af det faktiske delete/create-delta. Bruges til at udsende RoleMembershipChanged, så
 *       medlemskabs-drevne downstream-systemer (AD, KSP-CICS) kan re-synkronisere netop de roller.</li>
 * </ul>
 */
public record CurrentAssignmentChangeResult(Set<User> changedUsers, Set<Long> affectedUserRoleIds) {
}
