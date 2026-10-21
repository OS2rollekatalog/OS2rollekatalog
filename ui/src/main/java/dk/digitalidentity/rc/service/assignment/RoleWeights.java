package dk.digitalidentity.rc.service.assignment;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.dao.projections.UserRoleWeight;

/**
 * The weight of each user role: the heaviest weight among its system roles.
 * Within an it system, a user holding a heavier role gets nothing from the lighter ones.
 */
public final class RoleWeights {
	private static final int DEFAULT_WEIGHT = 1;

	private final Map<Long, Integer> weightByUserRoleId;

	RoleWeights(Collection<UserRoleWeight> weights) {
		this.weightByUserRoleId = weights.stream()
			.collect(Collectors.toUnmodifiableMap(UserRoleWeight::userRoleId, UserRoleWeight::weight));
	}

	public int weightOf(long userRoleId) {
		return weightByUserRoleId.getOrDefault(userRoleId, DEFAULT_WEIGHT);
	}

	public boolean outweighs(long candidateUserRoleId, long userRoleId) {
		return weightOf(candidateUserRoleId) > weightOf(userRoleId);
	}

	/** Only roles that have system roles are known; a role without any can never outweigh another. */
	public Set<Long> rolesOutweighing(long userRoleId) {
		return weightByUserRoleId.keySet().stream()
			.filter(candidate -> outweighs(candidate, userRoleId))
			.collect(Collectors.toSet());
	}
}
