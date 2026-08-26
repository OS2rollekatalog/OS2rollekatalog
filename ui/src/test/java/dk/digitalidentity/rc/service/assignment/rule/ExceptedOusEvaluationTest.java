package dk.digitalidentity.rc.service.assignment.rule;

import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.enums.ContainsTitles;
import dk.digitalidentity.rc.dao.model.User;
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
import static org.mockito.Mockito.when;

/**
 * Inheriting org unit assignment with excepted org units, run through the complete rule set.
 * <p>
 * The exception is a veto: {@link ExcludedOusRule} deselects the excepted org units, while granting
 * comes from {@link OrgUnitAssignmentRule}, which honours the assignment's inheritance.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExceptedOusEvaluationTest {

	@Mock
	private OrgUnitDao orgUnitDao;

	private AssignmentRuleEvaluator evaluator;

	private OrgUnit forvaltning;
	private OrgUnit plejecenter;
	private OrgUnit koekken;
	private OrgUnit koekkenKaelder;
	private Title titel;
	private User bruger;

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

		forvaltning = createOrgUnit("forvaltning-uuid", null);
		plejecenter = createOrgUnit("plejecenter-uuid", forvaltning);
		koekken = createOrgUnit("koekken-uuid", forvaltning);
		koekkenKaelder = createOrgUnit("koekken-kaelder-uuid", koekken);
		titel = createTitle("titel-uuid");
		bruger = createUser("bruger-uuid");

		when(orgUnitDao.findAllAncestorUuids("forvaltning-uuid")).thenReturn(List.of("forvaltning-uuid"));
		when(orgUnitDao.findAllAncestorUuids("plejecenter-uuid")).thenReturn(List.of("plejecenter-uuid", "forvaltning-uuid"));
		when(orgUnitDao.findAllAncestorUuids("koekken-uuid")).thenReturn(List.of("koekken-uuid", "forvaltning-uuid"));
		when(orgUnitDao.findAllAncestorUuids("koekken-kaelder-uuid")).thenReturn(List.of("koekken-kaelder-uuid", "koekken-uuid", "forvaltning-uuid"));
	}

	private OrgUnitUserRoleAssignment assignment(final List<OrgUnit> exceptedOus, final boolean inherit) {
		OrgUnitUserRoleAssignment assignment = new OrgUnitUserRoleAssignment();
		assignment.setOrgUnit(forvaltning);
		assignment.setInherit(inherit);
		assignment.setExceptedOus(new ArrayList<>(exceptedOus));
		assignment.setContainsExceptedOus(!exceptedOus.isEmpty());
		return assignment;
	}

	@Test
	@DisplayName("inheriting with excepted org units: a non-excepted sub org unit gets the role")
	void nonExceptedSubOuGetsTheRole() {
		Position stilling = createPosition(plejecenter, titel, bruger, false);

		assertThat(evaluator.applies(assignment(List.of(koekken), true), bruger, stilling, forvaltning))
			.isEqualTo(AssignmentAppliesResult.POSITIVE);
	}

	@Test
	@DisplayName("inheriting with excepted org units: the excepted org unit is deselected")
	void exceptedSubOuIsNegative() {
		Position stilling = createPosition(koekken, titel, bruger, false);

		assertThat(evaluator.applies(assignment(List.of(koekken), true), bruger, stilling, forvaltning))
			.isEqualTo(AssignmentAppliesResult.NEGATIVE);
	}

	@Test
	@DisplayName("inheriting with excepted org units: an org unit below an excepted one is deselected too")
	void ouUnderExceptedOuIsNegative() {
		Position stilling = createPosition(koekkenKaelder, titel, bruger, false);

		assertThat(evaluator.applies(assignment(List.of(koekken), true), bruger, stilling, forvaltning))
			.isEqualTo(AssignmentAppliesResult.NEGATIVE);
	}

	@Test
	@DisplayName("inheriting with excepted org units: the assignment's own org unit gets the role")
	void assignmentsOwnOuGetsTheRole() {
		Position stilling = createPosition(forvaltning, titel, bruger, false);

		assertThat(evaluator.applies(assignment(List.of(koekken), true), bruger, stilling, forvaltning))
			.isEqualTo(AssignmentAppliesResult.POSITIVE);
	}

	@Test
	@DisplayName("without inheritance the assignment reaches no sub org unit, not even the non-excepted ones")
	void withoutInheritanceNoSubOuIsIncluded() {
		Position stilling = createPosition(plejecenter, titel, bruger, false);

		assertThat(evaluator.applies(assignment(List.of(koekken), false), bruger, stilling, forvaltning))
			.isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
	}

	@Test
	@DisplayName("title filter + excepted org units: the title must still match in the non-excepted org units")
	void titleFilterIsRespectedAlongsideExceptedOus() {
		Title andenTitel = createTitle("anden-titel-uuid");
		Position stilling = createPosition(plejecenter, andenTitel, bruger, false);

		OrgUnitUserRoleAssignment tildeling = assignment(List.of(koekken), true);
		tildeling.setTitles(new ArrayList<>(List.of(titel)));
		tildeling.setContainsTitles(ContainsTitles.POSITIVE);

		assertThat(evaluator.applies(tildeling, bruger, stilling, forvaltning))
			.isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
	}
}
