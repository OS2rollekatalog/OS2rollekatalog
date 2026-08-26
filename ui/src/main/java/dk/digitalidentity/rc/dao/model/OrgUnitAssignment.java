package dk.digitalidentity.rc.dao.model;

import dk.digitalidentity.rc.dao.model.enums.ContainsTitles;

import java.util.List;

public interface OrgUnitAssignment {

	boolean isInherit();

	List<Title> getTitles();

	ContainsTitles getContainsTitles();

	boolean isContainsExceptedUsers();

	List<User> getExceptedUsers();

	boolean isManager();

	boolean isSubstitutes();

	boolean isContainsFunctions();

	List<Function> getFunctions();

	boolean isContainsExceptedOus();

	List<OrgUnit> getExceptedOus();

	void setExceptedOus(List<OrgUnit> exceptedOus);

	void setContainsExceptedOus(boolean containsExceptedOus);

	/**
	 * True when the assignment designates its own recipients: selected titles, functions or
	 * manager/substitute.
	 * <p>
	 * Exclusion lists do not count. "Everyone except these" designates nobody, it deselects, so an
	 * assignment carrying exclusions is still an assignment to the entire org unit.
	 */
	default boolean hasGrantingCondition() {
		return (getContainsTitles() != null && getContainsTitles() != ContainsTitles.NO)
			|| isContainsFunctions()
			|| isManager()
			|| isSubstitutes();
	}

}
