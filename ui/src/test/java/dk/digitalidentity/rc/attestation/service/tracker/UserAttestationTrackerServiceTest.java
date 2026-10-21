package dk.digitalidentity.rc.attestation.service.tracker;

import dk.digitalidentity.rc.attestation.config.AttestationConfig;
import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationOuAssignmentsDao;
import dk.digitalidentity.rc.attestation.dao.AttestationResponsibleCollectionDao;
import dk.digitalidentity.rc.attestation.dao.AttestationUserDao;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationResponsibleCollection;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import dk.digitalidentity.rc.attestation.model.entity.AttestationUser;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AssignedThroughType;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationOuRoleAssignment;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationUserRoleAssignment;
import dk.digitalidentity.rc.attestation.service.AttestationCachedItSystemService;
import dk.digitalidentity.rc.attestation.service.HistoricAssignmentAttestationService;
import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.UserDao;
import dk.digitalidentity.rc.dao.history.HistoryAttestationManagerDelegateDao;
import dk.digitalidentity.rc.dao.history.model.HistoryAttestationManagerDelegate;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserAttestationTrackerServiceTest {

	@Mock
	private RoleCatalogueConfiguration configuration;

	@Mock
	private AttestationRunTrackerService runTrackerService;

	@Mock
	private HistoricAssignmentAttestationService historicAssignmentService;

	@Mock
	private AttestationOuAssignmentsDao ouAssignmentsDao;

	@Mock
	private AttestationDao attestationDao;

	@Mock
	private AttestationUserDao attestationUserDao;

	@Mock
	private AttestationResponsibleCollectionDao attestationResponsibleCollectionDao;

	@Mock
	private AttestationCachedItSystemService cachedItSystemService;

	@Mock
	private SettingsService settingsService;

	@Mock
	private HistoryAttestationManagerDelegateDao historyAttestationManagerDelegateDao;

	@Mock
	private OrgUnitDao orgUnitDao;

	@Mock
	private UserDao userDao;

	@Mock
	private OrgUnitService orgUnitService;

	@Mock
	private UserService userService;

	@Mock
	private EntityManager entityManager;

	@InjectMocks
	private UserAttestationTrackerService service;

	private final LocalDate when = LocalDate.of(2026, 6, 1);
	private AttestationRun run;

	@BeforeEach
	void setup() {
		run = AttestationRun.builder()
			.id(1L)
			.deadline(when.plusDays(10))
			.sensitive(false)
			.extraSensitive(false)
			.build();
		lenient().when(runTrackerService.getAttestationRunToMaintain(when)).thenReturn(Optional.of(run));
		lenient().when(configuration.getAttestation()).thenReturn(new AttestationConfig());
	}

	private AttestationUserRoleAssignment assignmentWithCollection(Long collectionId) {
		return AttestationUserRoleAssignment.builder()
			.itSystemId(10L)
			.itSystemName("Test IT System")
			.userUuid("user-uuid")
			.responsibleCollectionId(collectionId)
			.build();
	}

	@Test
	@DisplayName("creates an IT system users attestation for an assignment whose collection has responsibles")
	void createsAttestationWhenCollectionHasResponsibles() {
		given(historicAssignmentService.findValidGroupByResponsibleCollectionIdAndUserUuidAndSensitiveRoleAndItSystem(when))
			.willReturn(List.of(assignmentWithCollection(42L)));
		given(historicAssignmentService.findValidGroupByResponsibleCollectionIdAndSensitiveRole(when))
			.willReturn(List.of());
		given(attestationResponsibleCollectionDao.findById(42L))
			.willReturn(Optional.of(new AttestationResponsibleCollection(42L, 10L, List.of("responsible-uuid"))));
		given(attestationDao.findFirstByAttestationTypeAndItSystemIdAndResponsibleCollectionIdAndAttestationRunOrderByIdAsc(
			Attestation.AttestationType.IT_SYSTEM_ATTESTATION, 10L, 42L, run)).willReturn(Optional.empty());
		given(attestationDao.save(any(Attestation.class))).willAnswer(invocation -> invocation.getArgument(0));
		given(attestationUserDao.save(any(AttestationUser.class))).willAnswer(invocation -> invocation.getArgument(0));

		service.updateSystemUserAttestations(when);

		verify(attestationDao).save(any(Attestation.class));
	}

	@Test
	@DisplayName("skips assignments whose collection has no responsibles — the attestation would be invisible and unfinishable")
	void skipsAssignmentWithEmptyCollection() {
		given(historicAssignmentService.findValidGroupByResponsibleCollectionIdAndUserUuidAndSensitiveRoleAndItSystem(when))
			.willReturn(List.of(assignmentWithCollection(42L)));
		given(historicAssignmentService.findValidGroupByResponsibleCollectionIdAndSensitiveRole(when))
			.willReturn(List.of(assignmentWithCollection(42L)));
		given(attestationResponsibleCollectionDao.findById(42L))
			.willReturn(Optional.of(new AttestationResponsibleCollection(42L, 10L, List.of())));

		service.updateSystemUserAttestations(when);

		verify(attestationDao, never()).save(any(Attestation.class));
		// Tom-tjekket caches pr. kørsel — én lookup selvom collectionen optræder i begge streams.
		verify(attestationResponsibleCollectionDao).findById(42L);
	}

	@Test
	@DisplayName("skips assignments whose collection no longer exists")
	void skipsAssignmentWithMissingCollection() {
		given(historicAssignmentService.findValidGroupByResponsibleCollectionIdAndUserUuidAndSensitiveRoleAndItSystem(when))
			.willReturn(List.of(assignmentWithCollection(42L)));
		given(historicAssignmentService.findValidGroupByResponsibleCollectionIdAndSensitiveRole(when))
			.willReturn(List.of());
		given(attestationResponsibleCollectionDao.findById(42L)).willReturn(Optional.empty());

		service.updateSystemUserAttestations(when);

		verify(attestationDao, never()).save(any(Attestation.class));
	}

	/**
	 * Which org units get an organisation attestation. The closing side only counts org unit roles assigned on the
	 * unit itself and DIRECT user assignments, so the tracker must select units by the same rule - otherwise a unit
	 * whose users only hold roles inherited from above is created and auto-closed the following night.
	 */
	@Nested
	class OrganisationAttestationSelection {
		private static final String PARENT_OU = "parent-ou-uuid";
		private static final String CHILD_OU = "child-ou-uuid";
		private static final String OTHER_OU = "other-ou-uuid";
		private static final String USER = "user-uuid";
		private static final String MANAGER = "manager-uuid";

		private final List<Attestation> saved = new ArrayList<>();

		@BeforeEach
		void stubOrganisationRun() {
			lenient().when(settingsService.isAttestationOrgUnitSelectionOptIn()).thenReturn(false);
			lenient().when(settingsService.getScheduledAttestationFilter()).thenReturn(Set.of());
			lenient().when(settingsService.isADAttestationEnabled()).thenReturn(false);
			lenient().when(historyAttestationManagerDelegateDao.findAllByDate(when)).thenReturn(List.of());
			lenient().when(cachedItSystemService.isItSystemExempt(any())).thenReturn(false);
			lenient().when(ouAssignmentsDao.findValidGroupByResponsibleOuUuidAndSensitiveRole(when)).thenReturn(List.of());
			lenient().when(historicAssignmentService.findValidGroupByResponsibleOuAndUserUuidAndSensitiveRole(when)).thenReturn(List.of());
			lenient().when(attestationUserDao.save(any(AttestationUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
			// In-memory attestation store so a second row for the same org unit finds the attestation created by the first
			lenient().when(attestationDao.save(any(Attestation.class))).thenAnswer(invocation -> {
				final Attestation attestation = invocation.getArgument(0);
				saved.add(attestation);
				return attestation;
			});
			lenient().when(attestationDao.findFirstByAttestationTypeAndResponsibleOuUuidAndAttestationRunOrderByIdAsc(any(), any(), any()))
				.thenAnswer(invocation -> saved.stream()
					.filter(a -> a.getAttestationType() == invocation.getArgument(0))
					.filter(a -> Objects.equals(a.getResponsibleOuUuid(), invocation.getArgument(1)))
					.findFirst());
		}

		private AttestationUserRoleAssignment userAssignment(final String userUuid, final String responsibleOu,
															 final AssignedThroughType assignedThrough, final boolean sensitive) {
			return AttestationUserRoleAssignment.builder()
				.itSystemId(10L)
				.itSystemName("Test IT System")
				.userUuid(userUuid)
				.responsibleOuUuid(responsibleOu)
				.responsibleOuName(responsibleOu + " name")
				.assignedThroughType(assignedThrough)
				.sensitiveRole(sensitive)
				.build();
		}

		/** Org unit role assigned on the unit itself; the OU updater sets responsibleOu to the assigning unit. */
		private AttestationOuRoleAssignment ouAssignment(final String ouUuid, final boolean sensitive) {
			return AttestationOuRoleAssignment.builder()
				.itSystemId(10L)
				.itSystemName("Test IT System")
				.roleId(100L)
				.ouUuid(ouUuid)
				.responsibleOuUuid(ouUuid)
				.responsibleOuName(ouUuid + " name")
				.assignedThroughType(AssignedThroughType.ORGUNIT)
				.assignedThroughUuid(ouUuid)
				.sensitiveRole(sensitive)
				.build();
		}

		@Test
		@DisplayName("an org unit whose users only hold roles inherited from above gets no attestation")
		void inheritedOnlyOrgUnitGetsNoAttestation() {
			given(historicAssignmentService.findValidGroupByResponsibleOuAndUserUuidAndSensitiveRole(when)).willReturn(List.of(
				userAssignment(USER, CHILD_OU, AssignedThroughType.ORGUNIT, false),
				userAssignment("other-user-uuid", CHILD_OU, AssignedThroughType.TITLE, false)));

			service.updateOrganisationUserAttestations(when);

			assertThat(saved).isEmpty();
			verify(attestationUserDao, never()).save(any(AttestationUser.class));
		}

		@Test
		@DisplayName("the org unit the inherited role is assigned on gets the attestation, without the child's users")
		void assigningParentGetsTheAttestation() {
			given(ouAssignmentsDao.findValidGroupByResponsibleOuUuidAndSensitiveRole(when))
				.willReturn(List.of(ouAssignment(PARENT_OU, false)));
			given(historicAssignmentService.findValidGroupByResponsibleOuAndUserUuidAndSensitiveRole(when))
				.willReturn(List.of(userAssignment(USER, CHILD_OU, AssignedThroughType.ORGUNIT, false)));

			service.updateOrganisationUserAttestations(when);

			assertThat(saved).singleElement().satisfies(a -> {
				assertThat(a.getAttestationType()).isEqualTo(Attestation.AttestationType.ORGANISATION_ATTESTATION);
				assertThat(a.getResponsibleOuUuid()).isEqualTo(PARENT_OU);
				assertThat(a.getResponsibleOuName()).isEqualTo(PARENT_OU + " name");
				assertThat(a.getUsersForAttestation()).isEmpty();
			});
		}

		@Test
		@DisplayName("an org unit with both an inherited and a direct assignment gets exactly one attestation with the user")
		void inheritedAndDirectGetsOneAttestation() {
			// The user-assignment query groups by assigned_through_type, so both rows arrive; a coarser
			// representative row must never decide for the whole org unit.
			given(historicAssignmentService.findValidGroupByResponsibleOuAndUserUuidAndSensitiveRole(when)).willReturn(List.of(
				userAssignment(USER, CHILD_OU, AssignedThroughType.ORGUNIT, false),
				userAssignment(USER, CHILD_OU, AssignedThroughType.DIRECT, false)));

			service.updateOrganisationUserAttestations(when);

			assertThat(saved).singleElement().satisfies(a -> {
				assertThat(a.getResponsibleOuUuid()).isEqualTo(CHILD_OU);
				assertThat(a.getUsersForAttestation()).extracting(AttestationUser::getUserUuid).containsExactly(USER);
			});
		}

		@Test
		@DisplayName("an org unit role assigned on the unit itself is enough, even when no user has a direct assignment")
		void pureOrgUnitRoleGetsAttestation() {
			given(ouAssignmentsDao.findValidGroupByResponsibleOuUuidAndSensitiveRole(when))
				.willReturn(List.of(ouAssignment(CHILD_OU, false)));

			service.updateOrganisationUserAttestations(when);

			assertThat(saved).singleElement().satisfies(a -> {
				assertThat(a.getAttestationType()).isEqualTo(Attestation.AttestationType.ORGANISATION_ATTESTATION);
				assertThat(a.getResponsibleOuUuid()).isEqualTo(CHILD_OU);
			});
			verify(attestationUserDao, never()).save(any(AttestationUser.class));
		}

		@Test
		@DisplayName("with AD attestation enabled, org units with employees still get an attestation without any assignments")
		void adAttestationCreatesAttestationForUnitsWithEmployees() {
			final OrgUnit orgUnit = new OrgUnit();
			orgUnit.setUuid(CHILD_OU);
			orgUnit.setName("Unit with employees");
			final User employee = new User();
			employee.setUuid(USER);
			final Position position = new Position();
			position.setOrgUnit(orgUnit);
			position.setPrimary(true);
			employee.setPositions(List.of(position));
			given(settingsService.isADAttestationEnabled()).willReturn(true);
			given(userDao.findByDeletedFalse()).willReturn(List.of(employee));
			given(orgUnitService.getByUuid(CHILD_OU)).willReturn(orgUnit);
			given(orgUnitDao.findById(CHILD_OU)).willReturn(Optional.of(orgUnit));

			service.updateOrganisationUserAttestations(when);

			assertThat(saved).singleElement().satisfies(a -> {
				assertThat(a.getAttestationType()).isEqualTo(Attestation.AttestationType.ORGANISATION_ATTESTATION);
				assertThat(a.getResponsibleOuUuid()).isEqualTo(CHILD_OU);
				assertThat(a.getResponsibleOuName()).isEqualTo("Unit with employees");
			});
			verify(attestationUserDao).saveAll(argThat(users -> {
				final List<AttestationUser> list = new ArrayList<>();
				users.forEach(list::add);
				return list.size() == 1 && USER.equals(list.get(0).getUserUuid());
			}));
		}

		@Test
		@DisplayName("a sensitive run only creates attestations for sensitive assignments, and never AD attestations")
		void sensitiveRunOnlyCoversSensitiveAssignments() {
			run.setSensitive(true);
			given(settingsService.isADAttestationEnabled()).willReturn(true);
			given(ouAssignmentsDao.findValidGroupByResponsibleOuUuidAndSensitiveRole(when))
				.willReturn(List.of(ouAssignment(PARENT_OU, false)));
			given(historicAssignmentService.findValidGroupByResponsibleOuAndUserUuidAndSensitiveRole(when)).willReturn(List.of(
				userAssignment(USER, CHILD_OU, AssignedThroughType.DIRECT, false),
				userAssignment(USER, OTHER_OU, AssignedThroughType.DIRECT, true)));

			service.updateOrganisationUserAttestations(when);

			assertThat(saved).singleElement().satisfies(a -> {
				assertThat(a.getResponsibleOuUuid()).isEqualTo(OTHER_OU);
				assertThat(a.isSensitive()).isTrue();
			});
			verify(userDao, never()).findByDeletedFalse();
		}

		@Test
		@DisplayName("delegated managers follow the same rule: inherited roles create nothing, direct ones a delegated attestation")
		void delegatedManagerFollowsSameRule() {
			final HistoryAttestationManagerDelegate delegate = mock(HistoryAttestationManagerDelegate.class);
			given(delegate.getManagerUuid()).willReturn(MANAGER);
			given(historyAttestationManagerDelegateDao.findAllByDate(when)).willReturn(List.of(delegate));
			final User manager = new User();
			manager.setUuid(MANAGER);
			given(userService.getOptionalByUuid(MANAGER)).willReturn(Optional.of(manager));
			given(orgUnitService.getOrgUnitsForUser(manager)).willReturn(List.of());
			given(historicAssignmentService.findValidGroupByResponsibleOuAndUserUuidAndSensitiveRole(when)).willReturn(List.of(
				userAssignment(MANAGER, PARENT_OU, AssignedThroughType.ORGUNIT, false),
				userAssignment(MANAGER, CHILD_OU, AssignedThroughType.DIRECT, false)));

			service.updateOrganisationUserAttestations(when);

			assertThat(saved).singleElement().satisfies(a -> {
				assertThat(a.getAttestationType()).isEqualTo(Attestation.AttestationType.MANAGER_DELEGATED_ATTESTATION);
				assertThat(a.getResponsibleOuUuid()).isEqualTo(CHILD_OU);
				assertThat(a.getUsersForAttestation()).extracting(AttestationUser::getUserUuid).containsExactly(MANAGER);
			});
		}
	}
}
