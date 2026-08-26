package dk.digitalidentity.rc.service.assignment.rule;

import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.ContainsTitles;
import dk.digitalidentity.rc.service.assignment.rules.AssignmentRule.AssignmentAppliesResult;
import dk.digitalidentity.rc.service.assignment.rules.AssignmentRuleEvaluator;
import dk.digitalidentity.rc.service.assignment.rules.ExcludedOusRule;
import dk.digitalidentity.rc.service.assignment.rules.ExcludedUsersRule;
import dk.digitalidentity.rc.service.assignment.rules.FunctionAssignmentRule;
import dk.digitalidentity.rc.service.assignment.rules.ManagerSubstituteAssignmentRule;
import dk.digitalidentity.rc.service.assignment.rules.OrgUnitAssignmentRule;
import dk.digitalidentity.rc.service.assignment.rules.TitleAssignmentRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createOrgUnit;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createPosition;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createTitle;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUser;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Excluded users on org unit assignments, run through the complete rule set.
 * <p>
 * The Svendborg case: a medication course assigned by title (authorized / unauthorized) where a few
 * users are excluded on the same assignment. The exclusion must only deselect, never grant.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExceptedUsersEvaluationTest {

	@Mock
	private OrgUnitDao orgUnitDao;

	private AssignmentRuleEvaluator evaluator;

	private OrgUnit plejecenter;
	private Title autoriseret;
	private Title uautoriseret;

	@BeforeEach
	void setUp() {
		evaluator = new AssignmentRuleEvaluator(List.of(
			new OrgUnitAssignmentRule(),
			new TitleAssignmentRule(),
			new ExcludedUsersRule(),
			new ExcludedOusRule(orgUnitDao),
			new FunctionAssignmentRule(),
			new ManagerSubstituteAssignmentRule()
		));

		plejecenter = createOrgUnit("plejecenter-uuid", null);
		autoriseret = createTitle("titel-autoriseret");
		uautoriseret = createTitle("titel-uautoriseret");
	}

	private OrgUnitUserRoleAssignment assignment(List<Title> titles, List<User> exceptedUsers) {
		OrgUnitUserRoleAssignment assignment = new OrgUnitUserRoleAssignment();
		assignment.setOrgUnit(plejecenter);
		assignment.setTitles(new ArrayList<>(titles));
		assignment.setContainsTitles(titles.isEmpty() ? ContainsTitles.NO : ContainsTitles.POSITIVE);
		assignment.setExceptedUsers(new ArrayList<>(exceptedUsers));
		assignment.setContainsExceptedUsers(!exceptedUsers.isEmpty());
		return assignment;
	}

	@Test
	@DisplayName("title filter alone: unauthorized employee does not get the course for authorized staff")
	void titlesOnly() {
		User hanne = createUser("hanne");
		Position hannesStilling = createPosition(plejecenter, uautoriseret, hanne, false);

		OrgUnitUserRoleAssignment kursusAutoriserede = assignment(List.of(autoriseret), List.of());

		assertThat(evaluator.applies(kursusAutoriserede, hanne, hannesStilling, plejecenter))
			.isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
	}

	@Test
	@DisplayName("title filter + excluded user: unauthorized employee must STILL not get the course for authorized staff")
	void titlesCombinedWithExceptedUsers() {
		User hanne = createUser("hanne");
		Position hannesStilling = createPosition(plejecenter, uautoriseret, hanne, false);
		User undtagetBruger = createUser("ole");

		OrgUnitUserRoleAssignment kursusAutoriserede = assignment(List.of(autoriseret), List.of(undtagetBruger));

		assertThat(evaluator.applies(kursusAutoriserede, hanne, hannesStilling, plejecenter))
			.isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
	}

	@Test
	@DisplayName("title filter + excluded user: a matching title still gets the role")
	void titlesCombinedWithExceptedUsersStillGrants() {
		User sanne = createUser("sanne");
		Position sannesStilling = createPosition(plejecenter, autoriseret, sanne, false);
		User undtagetBruger = createUser("ole");

		OrgUnitUserRoleAssignment kursusAutoriserede = assignment(List.of(autoriseret), List.of(undtagetBruger));

		assertThat(evaluator.applies(kursusAutoriserede, sanne, sannesStilling, plejecenter))
			.isEqualTo(AssignmentAppliesResult.POSITIVE);
	}

	@Test
	@DisplayName("title filter + excluded user: the excluded user with a matching title is negative")
	void exceptedUserWithMatchingTitleIsNegative() {
		User ole = createUser("ole");
		Position olesStilling = createPosition(plejecenter, autoriseret, ole, false);

		OrgUnitUserRoleAssignment kursusAutoriserede = assignment(List.of(autoriseret), List.of(ole));

		assertThat(evaluator.applies(kursusAutoriserede, ole, olesStilling, plejecenter))
			.isEqualTo(AssignmentAppliesResult.NEGATIVE);
	}

	@Test
	@DisplayName("excluded users only (no titles): everyone else gets the role")
	void exceptedUsersOnlyStillGrantsToEveryoneElse() {
		User hanne = createUser("hanne");
		Position hannesStilling = createPosition(plejecenter, uautoriseret, hanne, false);
		User ole = createUser("ole");

		OrgUnitUserRoleAssignment tildeltAlleUndtagenOle = assignment(List.of(), List.of(ole));

		assertThat(evaluator.applies(tildeltAlleUndtagenOle, hanne, hannesStilling, plejecenter))
			.isEqualTo(AssignmentAppliesResult.POSITIVE);
	}

	@Test
	@DisplayName("negative titles + excluded user: every other title gets the role, the excluded user does not")
	void negativeTitlesCombinedWithExceptedUsers() {
		User hanne = createUser("hanne");
		Position hannesStilling = createPosition(plejecenter, uautoriseret, hanne, false);
		User ole = createUser("ole");
		Position olesStilling = createPosition(plejecenter, uautoriseret, ole, false);

		OrgUnitUserRoleAssignment alleUndtagenAutoriserede = assignment(List.of(autoriseret), List.of(ole));
		alleUndtagenAutoriserede.setContainsTitles(ContainsTitles.NEGATIVE);

		assertThat(evaluator.applies(alleUndtagenAutoriserede, hanne, hannesStilling, plejecenter))
			.isEqualTo(AssignmentAppliesResult.POSITIVE);
		assertThat(evaluator.applies(alleUndtagenAutoriserede, ole, olesStilling, plejecenter))
			.isEqualTo(AssignmentAppliesResult.NEGATIVE);
	}

	@Test
	@DisplayName("when the data says inherit, both the assignment and the exclusion reach the sub org unit")
	void inheritedExceptedUsersAssignmentAppliesInSubOu() {
		OrgUnit afdeling = createOrgUnit("afdeling-uuid", plejecenter);
		User hanne = createUser("hanne");
		Position hannesStilling = createPosition(afdeling, uautoriseret, hanne, false);
		User ole = createUser("ole");
		Position olesStilling = createPosition(afdeling, uautoriseret, ole, false);

		OrgUnitUserRoleAssignment tildeltAlleUndtagenOle = assignment(List.of(), List.of(ole));
		tildeltAlleUndtagenOle.setInherit(true);

		assertThat(evaluator.applies(tildeltAlleUndtagenOle, hanne, hannesStilling, plejecenter))
			.isEqualTo(AssignmentAppliesResult.POSITIVE);
		assertThat(evaluator.applies(tildeltAlleUndtagenOle, ole, olesStilling, plejecenter))
			.isEqualTo(AssignmentAppliesResult.NEGATIVE);
	}
}
