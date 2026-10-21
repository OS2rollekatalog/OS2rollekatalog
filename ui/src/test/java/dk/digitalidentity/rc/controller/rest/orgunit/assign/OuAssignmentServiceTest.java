package dk.digitalidentity.rc.controller.rest.orgunit.assign;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import dk.digitalidentity.rc.controller.rest.orgunit.assign.OuAssignmentService.ExistingAssignment;
import dk.digitalidentity.rc.controller.rest.orgunit.assign.ScopeRules.AssignmentException;
import dk.digitalidentity.rc.controller.rest.orgunit.assign.ScopeRules.AssignmentTarget;
import dk.digitalidentity.rc.controller.rest.orgunit.assign.ScopeRules.ScopeChoice;
import dk.digitalidentity.rc.dao.model.Function;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ContainsTitles;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.service.OrgUnitAssignmentService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserRoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OuAssignmentService")
class OuAssignmentServiceTest {

	private static final String OU_UUID = "ou-uuid";
	private static final long ROLE_ID = 42L;
	private static final long ASSIGNMENT_ID = 7L;
	private static final String BLOCKED_REASON = "Jobfunktionsrollen er skrivebeskyttet";
	private static final String CASE_NUMBER = "SAG-1";

	private static final Set<String> TITLE_UUIDS = Set.of("title-uuid");
	private static final Set<String> USER_UUIDS = Set.of("excepted-user-uuid");
	private static final Set<String> OU_UUIDS = Set.of("excepted-ou-uuid");
	private static final Set<String> FUNCTION_UUIDS = Set.of("function-uuid");

	@Mock
	private UserRoleService userRoleService;

	@Mock
	private OrgUnitService orgUnitService;

	@Mock
	private OrgUnitAssignmentService orgUnitAssignmentService;

	@Mock
	private MessageSource messageSource;

	@InjectMocks
	private OuAssignmentService ouAssignmentService;

	private OrgUnit orgUnit;
	private UserRole userRole;
	private RoleGroup roleGroup;

	@BeforeEach
	void setUp() {
		orgUnit = orgUnit(OU_UUID);

		userRole = userRole(itSystem(ItSystemType.SAML, false));
		roleGroup = roleGroup(userRole);

		when(messageSource.getMessage(anyString(), any(), any())).thenAnswer(call -> call.getArgument(0));
		when(userRoleService.assignmentBlockedReason(any())).thenReturn(Optional.empty());
	}

	private static OrgUnit orgUnit(String uuid) {
		OrgUnit ou = new OrgUnit();
		ou.setUuid(uuid);
		return ou;
	}

	private static ItSystem itSystem(ItSystemType type, boolean readonly) {
		ItSystem itSystem = new ItSystem();
		itSystem.setId(7L);
		itSystem.setIdentifier("SomeSystem");
		itSystem.setName("Some system");
		itSystem.setSystemType(type);
		itSystem.setReadonly(readonly);
		return itSystem;
	}

	private static UserRole userRole(ItSystem itSystem) {
		UserRole role = new UserRole();
		role.setId(ROLE_ID);
		role.setName("Sagsbehandler");
		role.setItSystem(itSystem);
		return role;
	}

	private static RoleGroup roleGroup(UserRole... roles) {
		RoleGroup group = new RoleGroup();
		group.setId(ROLE_ID);
		group.setName("Sagsbehandlerpakke");
		group.setUserRoleAssignments(List.of(roles).stream().map(role -> {
			RoleGroupUserRoleAssignment assignment = new RoleGroupUserRoleAssignment();
			assignment.setUserRole(role);
			return assignment;
		}).toList());
		return group;
	}

	private static OrgUnitAssignmentRequest request(ScopeChoice choice, ScopeFields fields,
			LocalDate startDate, LocalDate stopDate) {
		return new OrgUnitAssignmentRequest(choice, fields, startDate, stopDate, CASE_NUMBER);
	}

	/** Everyone, not inherited, nothing selected - the simplest scope that resolves. */
	private static OrgUnitAssignmentRequest request(LocalDate startDate, LocalDate stopDate) {
		return request(new ScopeChoice(AssignmentTarget.EVERYONE, AssignmentException.NONE, false),
			ScopeFields.empty(), startDate, stopDate);
	}

	private static OrgUnitAssignmentRequest request() {
		return request(null, null);
	}

	private static OrgUnitAssignmentRequest inheritingRequest() {
		return request(new ScopeChoice(AssignmentTarget.EVERYONE, AssignmentException.NONE, true),
			ScopeFields.empty(), null, null);
	}

	private static OrgUnitAssignmentRequest exceptedOusRequest() {
		return request(new ScopeChoice(AssignmentTarget.EVERYONE, AssignmentException.OUS, true),
			fields(OU_UUIDS, Set.of(), Set.of(), Set.of(), false), null, null);
	}

	private static ScopeFields fields(Set<String> exceptedOus, Set<String> exceptedUsers, Set<String> titles,
			Set<String> functions, boolean includeSubstitutes) {
		return new ScopeFields(exceptedOus, exceptedUsers, titles, functions, includeSubstitutes);
	}

	private static List<String> errorsOf(ThrowingCall call) {
		@SuppressWarnings("deprecation")
		AssignmentValidationException thrown =
			catchThrowableOfType(call::run, AssignmentValidationException.class);

		assertThat(thrown).isNotNull();

		return thrown.errors();
	}

	@FunctionalInterface
	private interface ThrowingCall {
		void run();
	}

	@Nested
	@DisplayName("assign - role validation")
	class RoleValidation {

		@Test
		@DisplayName("a blocked role is rejected with the blocking reason")
		void blockedRole() {
			// ---- Given ---- //
			when(userRoleService.assignmentBlockedReason(userRole)).thenReturn(Optional.of(BLOCKED_REASON));

			// ---- When / Then ---- //
			assertThat(errorsOf(() -> ouAssignmentService.assign(orgUnit, userRole, request())))
				.isEqualTo(List.of(BLOCKED_REASON));
		}

		@Test
		@DisplayName("a role blocked inside a group is rejected with that role's reason")
		void containedRoleBlocked() {
			// ---- Given ---- //
			when(userRoleService.assignmentBlockedReason(userRole)).thenReturn(Optional.of(BLOCKED_REASON));

			// ---- When / Then ---- //
			assertThat(errorsOf(() -> ouAssignmentService.assign(orgUnit, roleGroup, request())))
				.isEqualTo(List.of(BLOCKED_REASON));
		}

		@SuppressWarnings("deprecation")
		@Test
		@DisplayName("a rejected role is never persisted")
		void rejectedRoleIsNotPersisted() {
			// ---- Given ---- //
			userRole.setUserOnly(true);

			// ---- When ---- //
			catchThrowableOfType(() -> ouAssignmentService.assign(orgUnit, userRole, request()),
				AssignmentValidationException.class);

			// ---- Then ---- //
			verify(orgUnitService, never()).addUserRole(any(), any(), anyBoolean(), any(), any(), any(), any(),
				anyBoolean(), anyBoolean(), anyBoolean(), any(), any());
		}

		@Test
		@DisplayName("a role allowing postponed constraints cannot be assigned to an org unit")
		void allowPostponingRole() {
			// ---- Given ---- //
			userRole.setAllowPostponing(true);

			// ---- When / Then ---- //
			assertThat(errorsOf(() -> ouAssignmentService.assign(orgUnit, userRole, request())))
				.isEqualTo(List.of("html.page.ou.assign.error.allowPostponing"));
		}

		@Test
		@DisplayName("a group containing an AD role cannot be assigned with inheritance")
		void adRoleCannotInherit() {
			// ---- Given ---- //
			RoleGroup adGroup = roleGroup(userRole(itSystem(ItSystemType.AD, true)));

			// ---- When / Then ---- //
			assertThat(errorsOf(() -> ouAssignmentService.assign(orgUnit, adGroup, inheritingRequest())))
				.isEqualTo(List.of("html.page.ou.assign.error.roleGroup.inheritAdRole"));
		}

		@Test
		@DisplayName("a group containing a read-only it system role cannot be assigned with inheritance")
		void readOnlyRoleCannotInherit() {
			// ---- Given ---- //
			RoleGroup readOnlyGroup = roleGroup(userRole(itSystem(ItSystemType.SAML, true)));

			// ---- When / Then ---- //
			assertThat(errorsOf(() -> ouAssignmentService.assign(orgUnit, readOnlyGroup, inheritingRequest())))
				.isEqualTo(List.of("html.page.ou.assign.error.roleGroup.inheritAdRole"));
		}

		@Test
		@DisplayName("a group containing an AD role may be assigned without inheritance")
		void adRoleWithoutInheritanceIsAllowed() {
			// ---- Given ---- //
			RoleGroup adGroup = roleGroup(userRole(itSystem(ItSystemType.AD, true)));

			// ---- When ---- //
			ouAssignmentService.assign(orgUnit, adGroup, request());

			// ---- Then ---- //
			verify(orgUnitService).addRoleGroup(any(), any(), anyBoolean(), any(), any(), any(), any(),
				anyBoolean(), anyBoolean(), anyBoolean(), any(), any());
		}

		/** An excepted-ou choice inherits by rule, so the AD check must still see it. */
		@Test
		@DisplayName("a group containing an AD role is rejected when the scope inherits by rule")
		void adRoleRejectedForImplicitlyInheritingScope() {
			// ---- Given ---- //
			RoleGroup adGroup = roleGroup(userRole(itSystem(ItSystemType.AD, true)));

			// ---- When / Then ---- //
			assertThat(errorsOf(() -> ouAssignmentService.assign(orgUnit, adGroup, exceptedOusRequest())))
				.isEqualTo(List.of("html.page.ou.assign.error.roleGroup.inheritAdRole"));
		}
	}

	@Nested
	@DisplayName("assign - dates")
	class Dates {

		@Test
		@DisplayName("a stop date before the start date is rejected")
		void stopBeforeStart() {
			// ---- Given ---- //
			OrgUnitAssignmentRequest invalid = request(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 1));

			// ---- When / Then ---- //
			assertThat(errorsOf(() -> ouAssignmentService.assign(orgUnit, userRole, invalid)))
				.isEqualTo(List.of("html.page.ou.assign.receipt.stopDate.beforeStartDate"));
		}

		@Test
		@DisplayName("a stop date equal to the start date is rejected")
		void stopEqualsStart() {
			// ---- Given ---- //
			LocalDate sameDay = LocalDate.of(2026, 3, 10);

			// ---- When / Then ---- //
			assertThat(errorsOf(() -> ouAssignmentService.assign(orgUnit, userRole, request(sameDay, sameDay))))
				.isEqualTo(List.of("html.page.ou.assign.receipt.stopDate.beforeStartDate"));
		}

		@Test
		@DisplayName("an open-ended assignment is accepted")
		void missingStopDateIsAccepted() {
			// ---- Given ---- //
			OrgUnitAssignmentRequest openEnded = request(LocalDate.of(2026, 3, 10), null);

			// ---- When / Then ---- //
			assertThatCode(() -> ouAssignmentService.assign(orgUnit, userRole, openEnded))
				.doesNotThrowAnyException();
		}
	}

	@Nested
	@DisplayName("assign - scope resolution")
	class ScopeResolution {

		@Test
		@DisplayName("a request without scope fields is rejected")
		void missingScopeFields() {
			// ---- Given ---- //
			OrgUnitAssignmentRequest invalid = request(
				new ScopeChoice(AssignmentTarget.EVERYONE, AssignmentException.NONE, false), null, null, null);

			// ---- When / Then ---- //
			assertThat(errorsOf(() -> ouAssignmentService.assign(orgUnit, userRole, invalid)))
				.isEqualTo(List.of("html.page.ou.assign.error.unresolvableScope"));
		}

		@Test
		@DisplayName("a submission missing a set the choice reads is rejected")
		void missingRequiredSet() {
			// ---- Given ---- //
			OrgUnitAssignmentRequest invalid = request(
				new ScopeChoice(AssignmentTarget.TITLES, AssignmentException.NONE, false),
				fields(Set.of(), Set.of(), null, Set.of(), false), null, null);

			// ---- When / Then ---- //
			assertThat(errorsOf(() -> ouAssignmentService.assign(orgUnit, userRole, invalid)))
				.isEqualTo(List.of("html.page.ou.assign.error.unresolvableScope"));
		}

		/** The wizard omits the keys for steps the choice has no use for, so they arrive null. */
		@Test
		@DisplayName("a set the choice ignores may be absent while a set it reads is present")
		void absentIrrelevantSetAlongsideRequiredOne() {
			// ---- Given ---- //
			OrgUnitAssignmentRequest targetedTitles = request(
				new ScopeChoice(AssignmentTarget.TITLES, AssignmentException.NONE, false),
				fields(null, null, TITLE_UUIDS, null, false), null, null);

			// ---- When ---- //
			ouAssignmentService.assign(orgUnit, userRole, targetedTitles);

			// ---- Then ---- //
			verify(orgUnitService).addUserRole(eq(orgUnit), eq(userRole), eq(false), any(), any(), eq(Set.of()),
				eq(TITLE_UUIDS), eq(false), eq(false), eq(false), eq(Set.of()), eq(CASE_NUMBER));
		}

		/** A step the user never reached must not leak its selection into an unrelated scope. */
		@Test
		@DisplayName("a set the choice ignores is dropped even when submitted")
		void submittedIrrelevantSetIsDropped() {
			// ---- Given ---- //
			OrgUnitAssignmentRequest everyone = request(
				new ScopeChoice(AssignmentTarget.EVERYONE, AssignmentException.NONE, false),
				fields(Set.of(), USER_UUIDS, TITLE_UUIDS, FUNCTION_UUIDS, true), null, null);

			// ---- When ---- //
			ouAssignmentService.assign(orgUnit, userRole, everyone);

			// ---- Then ---- //
			verify(orgUnitService).addUserRole(eq(orgUnit), eq(userRole), eq(false), any(), any(), eq(Set.of()),
				eq(Set.of()), eq(false), eq(false), eq(false), eq(Set.of()), eq(CASE_NUMBER));
		}

		@Test
		@DisplayName("a choice matching no rule is rejected")
		void unknownScopeChoice() {
			// ---- Given ---- //
			OrgUnitAssignmentRequest invalid = request(
				new ScopeChoice(AssignmentTarget.MANAGER, AssignmentException.USERS, false),
				ScopeFields.empty(), null, null);

			// ---- When / Then ---- //
			assertThat(errorsOf(() -> ouAssignmentService.assign(orgUnit, userRole, invalid)))
				.isEqualTo(List.of("html.page.ou.assign.error.unresolvableScope"));
		}

		/** The rule fixes inheritance, so a choice contradicting it is a submission the wizard should not make. */
		@Test
		@DisplayName("a choice contradicting its rule's inherit constraint is rejected")
		void inheritConstraintViolation() {
			// ---- Given ---- //
			OrgUnitAssignmentRequest invalid = request(
				new ScopeChoice(AssignmentTarget.EVERYONE, AssignmentException.OUS, false),
				fields(OU_UUIDS, Set.of(), Set.of(), Set.of(), false), null, null);

			// ---- When / Then ---- //
			assertThat(errorsOf(() -> ouAssignmentService.assign(orgUnit, userRole, invalid)))
				.isEqualTo(List.of("html.page.ou.assign.error.unresolvableScope"));
		}

		@Test
		@DisplayName("excepted org units are persisted by the excepted-ou specific method")
		void exceptedOusUseTheirOwnMethod() {
			// ---- When ---- //
			ouAssignmentService.assign(orgUnit, userRole, exceptedOusRequest());

			// ---- Then ---- //
			verify(orgUnitService).addUserRoleWithInheritAndExceptedOus(eq(orgUnit), eq(userRole), any(), any(),
				eq(List.copyOf(OU_UUIDS)), eq(CASE_NUMBER));
			verify(orgUnitService, never()).addUserRole(any(), any(), anyBoolean(), any(), any(), any(), any(),
				anyBoolean(), anyBoolean(), anyBoolean(), any(), any());
		}

		/** Substitutes are meaningless off a manager assignment and must not survive into one. */
		@Test
		@DisplayName("substitutes are only set for a manager assignment")
		void substitutesRequireManagerTarget() {
			// ---- Given ---- //
			OrgUnitAssignmentRequest manager = request(
				new ScopeChoice(AssignmentTarget.MANAGER, AssignmentException.NONE, false),
				fields(Set.of(), Set.of(), Set.of(), Set.of(), true), null, null);

			// ---- When ---- //
			ouAssignmentService.assign(orgUnit, userRole, manager);

			// ---- Then ---- //
			verify(orgUnitService).addUserRole(eq(orgUnit), eq(userRole), eq(false), any(), any(), eq(Set.of()),
				eq(Set.of()), eq(false), eq(true), eq(true), eq(Set.of()), eq(CASE_NUMBER));
		}

		@Test
		@DisplayName("titles named as an exception are persisted as negative titles")
		void exceptedTitlesAreNegative() {
			// ---- Given ---- //
			OrgUnitAssignmentRequest exceptedTitles = request(
				new ScopeChoice(AssignmentTarget.EVERYONE, AssignmentException.TITLES, false),
				fields(Set.of(), Set.of(), TITLE_UUIDS, Set.of(), false), null, null);

			// ---- When ---- //
			ouAssignmentService.assign(orgUnit, userRole, exceptedTitles);

			// ---- Then ---- //
			verify(orgUnitService).addUserRole(eq(orgUnit), eq(userRole), eq(false), any(), any(), eq(Set.of()),
				eq(TITLE_UUIDS), eq(true), eq(false), eq(false), eq(Set.of()), eq(CASE_NUMBER));
		}
	}

	@Nested
	@DisplayName("update")
	class Update {

		@Test
		@DisplayName("an edit keeps its own persist method for excepted org units")
		void exceptedOusUseTheirOwnMethod() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment assignment =
				persistedAssignment(new ScopeChoice(AssignmentTarget.EVERYONE, AssignmentException.OUS, true));

			// ---- When ---- //
			ouAssignmentService.update(assignment, exceptedOusRequest());

			// ---- Then ---- //
			verify(orgUnitService).saveUserRoleWithInheritAndExceptedOus(eq(assignment), any(), any(),
				eq(List.copyOf(OU_UUIDS)));
			verify(orgUnitService, never()).updateUserRoleAssignment(any(), any(), anyBoolean(), any(), any(),
				any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any());
		}

		/** Validation runs before the case number is written, so a rejected edit leaves nothing behind. */
		@SuppressWarnings("deprecation")
		@Test
		@DisplayName("a rejected edit does not overwrite the case number")
		void rejectedEditLeavesCaseNumber() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment assignment =
				persistedAssignment(new ScopeChoice(AssignmentTarget.EVERYONE, AssignmentException.NONE, false));
			assignment.setCaseNumber("SAG-ORIGINAL");
			assignment.getUserRole().setUserOnly(true);

			// ---- When ---- //
			catchThrowableOfType(() -> ouAssignmentService.update(assignment, request()),
				AssignmentValidationException.class);

			// ---- Then ---- //
			assertThat(assignment.getCaseNumber()).isEqualTo("SAG-ORIGINAL");
		}

		@Test
		@DisplayName("an edit resolves against the persisted scope, ignoring the submitted choice")
		void submittedChoiceIsIgnored() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment assignment =
				persistedAssignment(new ScopeChoice(AssignmentTarget.TITLES, AssignmentException.NONE, false));
			OrgUnitAssignmentRequest everyone = request(
				new ScopeChoice(AssignmentTarget.EVERYONE, AssignmentException.NONE, false),
				fields(Set.of(), Set.of(), TITLE_UUIDS, Set.of(), false), null, null);

			// ---- When ---- //
			ouAssignmentService.update(assignment, everyone);

			// ---- Then ---- //
			verify(orgUnitService).updateUserRoleAssignment(eq(assignment.getOrgUnit()), eq(assignment), eq(false),
				any(), any(), eq(Set.of()), eq(TITLE_UUIDS), eq(false), eq(false), eq(false), eq(Set.of()));
		}

		@Test
		@DisplayName("an edit of an assignment whose persisted flags match no rule is rejected")
		void unresolvablePersistedScope() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment assignment =
				persistedAssignment(new ScopeChoice(AssignmentTarget.MANAGER, AssignmentException.NONE, false));
			assignment.setContainsExceptedUsers(true);
			assignment.setExceptedUsers(users(USER_UUIDS));

			// ---- When / Then ---- //
			assertThat(errorsOf(() -> ouAssignmentService.update(assignment, request())))
				.isEqualTo(List.of("html.page.ou.assign.error.unresolvableScope"));
		}
	}

	@Nested
	@DisplayName("findExisting")
	class FindExisting {

		/**
		 * Every valid choice must survive being persisted and read back, or the wizard
		 * would show a different scope than the one that was saved.
		 */
		@ParameterizedTest(name = "{0} + {1} + inherit={2}")
		@CsvSource({
			"EVERYONE, NONE,   true",
			"EVERYONE, NONE,   false",
			"EVERYONE, TITLES, true",
			"EVERYONE, TITLES, false",
			"EVERYONE, OUS,    true",
			"EVERYONE, USERS,  false",
			"TITLES,   NONE,   true",
			"TITLES,   NONE,   false",
			"TITLES,   USERS,  false",
			"FUNCTION, NONE,   true",
			"FUNCTION, NONE,   false",
			"MANAGER,  NONE,   true",
			"MANAGER,  NONE,   false",
		})
		@DisplayName("a valid choice round-trips through the persisted assignment unchanged")
		void roundTripsEveryValidChoice(AssignmentTarget target, AssignmentException exception, boolean inherit) {
			// ---- Given ---- //
			ScopeChoice choice = new ScopeChoice(target, exception, inherit);
			when(orgUnitAssignmentService.getOrgUnitUserRoleAssignment(ASSIGNMENT_ID, OU_UUID, ROLE_ID))
				.thenReturn(Optional.of(persistedAssignment(choice)));

			// ---- When ---- //
			Optional<ExistingAssignment> existing =
				ouAssignmentService.findExisting(RoleType.USERROLE, OU_UUID, ROLE_ID, ASSIGNMENT_ID);

			// ---- Then ---- //
			assertThat(existing).map(ExistingAssignment::scopeChoice).contains(choice);
		}

		/**
		 * The persisted flags are independent of each other while the rules are not, so a
		 * combination outside them must be reported as absent rather than approximated.
		 */
		@Test
		@DisplayName("persisted flags matching no rule are treated as absent")
		void persistedFlagsOutsideEveryRule() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment assignment =
				persistedAssignment(new ScopeChoice(AssignmentTarget.MANAGER, AssignmentException.NONE, false));
			assignment.setContainsExceptedUsers(true);
			assignment.setExceptedUsers(users(USER_UUIDS));
			when(orgUnitAssignmentService.getOrgUnitUserRoleAssignment(ASSIGNMENT_ID, OU_UUID, ROLE_ID))
				.thenReturn(Optional.of(assignment));

			// ---- When ---- //
			Optional<ExistingAssignment> existing =
				ouAssignmentService.findExisting(RoleType.USERROLE, OU_UUID, ROLE_ID, ASSIGNMENT_ID);

			// ---- Then ---- //
			assertThat(existing).isEmpty();
		}

		/** An excepted-ou assignment is always inherited, so a stored false matches no rule. */
		@Test
		@DisplayName("a persisted inherit flag contradicting its rule is treated as absent")
		void persistedInheritContradictsRule() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment assignment =
				persistedAssignment(new ScopeChoice(AssignmentTarget.EVERYONE, AssignmentException.OUS, true));
			assignment.setInherit(false);
			when(orgUnitAssignmentService.getOrgUnitUserRoleAssignment(ASSIGNMENT_ID, OU_UUID, ROLE_ID))
				.thenReturn(Optional.of(assignment));

			// ---- When ---- //
			Optional<ExistingAssignment> existing =
				ouAssignmentService.findExisting(RoleType.USERROLE, OU_UUID, ROLE_ID, ASSIGNMENT_ID);

			// ---- Then ---- //
			assertThat(existing).isEmpty();
		}

		@Test
		@DisplayName("an assignment reached through a mismatched org unit is treated as absent")
		void mismatchedOrgUnit() {
			// ---- Given ---- //
			when(orgUnitAssignmentService.getOrgUnitUserRoleAssignment(ASSIGNMENT_ID, OU_UUID, ROLE_ID))
				.thenReturn(Optional.empty());

			// ---- When ---- //
			Optional<ExistingAssignment> existing =
				ouAssignmentService.findExisting(RoleType.USERROLE, OU_UUID, ROLE_ID, ASSIGNMENT_ID);

			// ---- Then ---- //
			assertThat(existing).isEmpty();
		}

		@Test
		@DisplayName("a wizard step's pre-fill does not re-verify the org unit")
		void stepPrefillSkipsVerification() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment assignment =
				persistedAssignment(new ScopeChoice(AssignmentTarget.EVERYONE, AssignmentException.NONE, false));
			assignment.setOrgUnit(orgUnit("another-ou-uuid"));
			when(orgUnitAssignmentService.getOrgUnitUserRoleAssignment(ASSIGNMENT_ID))
				.thenReturn(Optional.of(assignment));

			// ---- When ---- //
			Optional<ExistingAssignment> existing = ouAssignmentService.findExisting(RoleType.USERROLE, ASSIGNMENT_ID);

			// ---- Then ---- //
			assertThat(existing).isPresent();
		}

		/** Sets whose contains-flag is false are still shown, or reopening drops an earlier selection. */
		@Test
		@DisplayName("the pre-fill carries every persisted uuid back to the wizard")
		void prefillCarriesPersistedSelections() {
			// ---- Given ---- //
			ScopeChoice choice = new ScopeChoice(AssignmentTarget.TITLES, AssignmentException.USERS, false);
			when(orgUnitAssignmentService.getOrgUnitUserRoleAssignment(ASSIGNMENT_ID, OU_UUID, ROLE_ID))
				.thenReturn(Optional.of(persistedAssignment(choice)));

			// ---- When ---- //
			Optional<ExistingAssignment> existing =
				ouAssignmentService.findExisting(RoleType.USERROLE, OU_UUID, ROLE_ID, ASSIGNMENT_ID);

			// ---- Then ---- //
			assertThat(existing).map(ExistingAssignment::scopeFields)
				.contains(fields(Set.of(), USER_UUIDS, TITLE_UUIDS, Set.of(), false));
		}
	}

	/** An assignment persisted exactly as {@code choice} would be written by the wizard. */
	private static OrgUnitUserRoleAssignment persistedAssignment(ScopeChoice choice) {
		boolean inherit = ScopeRules.ruleFor(choice).orElseThrow().inherit().appliedTo(choice.inherit());
		boolean manager = choice.target() == AssignmentTarget.MANAGER;
		boolean functions = choice.target() == AssignmentTarget.FUNCTION;
		boolean exceptedUsers = choice.exception() == AssignmentException.USERS;
		boolean exceptedOus = choice.exception() == AssignmentException.OUS;

		OrgUnitUserRoleAssignment assignment = new OrgUnitUserRoleAssignment();
		assignment.setId(ASSIGNMENT_ID);
		assignment.setOrgUnit(orgUnit(OU_UUID));
		assignment.setUserRole(userRole(itSystem(ItSystemType.SAML, false)));
		assignment.setCaseNumber(CASE_NUMBER);
		assignment.setInherit(inherit);
		assignment.setManager(manager);
		assignment.setSubstitutes(false);
		assignment.setContainsTitles(containsTitlesFor(choice));
		assignment.setTitles(ScopeRules.titleModeFor(choice).isPresent() ? titles(TITLE_UUIDS) : List.of());
		assignment.setContainsFunctions(functions);
		assignment.setFunctions(functions ? functions(FUNCTION_UUIDS) : List.of());
		assignment.setContainsExceptedUsers(exceptedUsers);
		assignment.setExceptedUsers(exceptedUsers ? users(USER_UUIDS) : List.of());
		assignment.setContainsExceptedOus(exceptedOus);
		assignment.setExceptedOus(exceptedOus ? orgUnits(OU_UUIDS) : List.of());
		return assignment;
	}

	private static ContainsTitles containsTitlesFor(ScopeChoice choice) {
		if (choice.exception() == AssignmentException.TITLES) {
			return ContainsTitles.NEGATIVE;
		}
		return choice.target() == AssignmentTarget.TITLES ? ContainsTitles.POSITIVE : ContainsTitles.NO;
	}

	private static List<Title> titles(Set<String> uuids) {
		return uuids.stream().map(uuid -> {
			Title title = new Title();
			title.setUuid(uuid);
			return title;
		}).toList();
	}

	private static List<Function> functions(Set<String> uuids) {
		return uuids.stream().map(uuid -> {
			Function function = new Function();
			function.setUuid(uuid);
			return function;
		}).toList();
	}

	private static List<User> users(Set<String> uuids) {
		return uuids.stream().map(uuid -> {
			User user = new User();
			user.setUuid(uuid);
			return user;
		}).toList();
	}

	private static List<OrgUnit> orgUnits(Set<String> uuids) {
		return uuids.stream().map(OuAssignmentServiceTest::orgUnit).toList();
	}
}
