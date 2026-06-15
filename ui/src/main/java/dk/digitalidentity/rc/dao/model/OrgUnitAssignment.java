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

}
