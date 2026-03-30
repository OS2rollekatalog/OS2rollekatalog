package dk.digitalidentity.rc.security.permission;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Models a constraint for a given combination of the current User, a Section and Permission
 * Contains sets of Ids for constrained ItSystems and OrgUnits.
 * These sets can be null, which means there are no constraints on that type.
 */
@AllArgsConstructor
@Getter
public class PermissionConstraint implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	private Set<Long> constrainedItSystemIds;
	private Set<String> constrainedOUUuids;

	// Copy constructor
	public PermissionConstraint(PermissionConstraint other) {
		this.constrainedItSystemIds = other.constrainedItSystemIds == null ? null : new HashSet<>(other.constrainedItSystemIds);
		this.constrainedOUUuids = other.constrainedOUUuids == null ? null : new HashSet<>(other.constrainedOUUuids);
	}

	/**
	 * Returns true if the passed IT system id is allowed by the constraint
	 */
	public boolean allowsITSystem(Long systemId) {
		return constrainedItSystemIds == null || constrainedItSystemIds.contains(systemId);
	}

	public boolean allowsAllITSystems(Collection<Long> systemIds) {
		if (constrainedItSystemIds == null) {
			return true;
		}
		if (constrainedItSystemIds.isEmpty()) {
			return false;
		}
		return  constrainedItSystemIds.containsAll(systemIds);
	}

	/**
	 * Returns true if the passed ou uuid is allowed by the constraint
	 */
	public boolean allowsOrgunit(String ouUuid) {
		if (constrainedOUUuids == null) {
			return true;
		}
		return ouUuid != null && constrainedOUUuids.contains(ouUuid);
	}


	/**
	 * Returns true if any of the passed ou uuids is allowed by the constraint
	 */
	public boolean allowsAnyOrgunit(List<String> ouUuid) {
		if (constrainedOUUuids == null) {
			return true;
		}
		if (ouUuid == null) {
			return false;
		}
		return ouUuid.stream().anyMatch(constrainedOUUuids::contains);
	}

	public boolean isUnconstrained() {
		return constrainedItSystemIds == null && constrainedOUUuids == null;
	}

	/**
	 * Merge with another constraint using "broadest access wins" semantics.
	 * @param other the constraint to merge with
	 * @return a new merged constraint
	 */
	public PermissionConstraint merge(PermissionConstraint other) {
		Set<Long> mergedItSystemIds = mergeSets(this.constrainedItSystemIds, other.constrainedItSystemIds);
		Set<String> mergedOuUuids = mergeSets(this.constrainedOUUuids, other.constrainedOUUuids);
		return new PermissionConstraint(mergedItSystemIds, mergedOuUuids);
	}

	/**
	 * Merge two sets using "broadest access wins" semantics:
	 * - If either is null (unconstrained), result is null
	 * - Otherwise, union the sets (more IDs = broader access)
	 */
	private static <T> Set<T> mergeSets(Set<T> set1, Set<T> set2) {
		// If either is unconstrained, result is unconstrained
		if (set1 == null || set2 == null) {
			return null;
		}

		// Both are constrained - union them for broadest access
		Set<T> merged = new HashSet<>(set1);
		merged.addAll(set2);
		return merged;
	}

	/**
	 * Creates a copy of this constraint.
	 */
	public PermissionConstraint copy() {
		return new PermissionConstraint(this);
	}
}
