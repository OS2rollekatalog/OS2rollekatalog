package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.EmailTemplateDao;
import dk.digitalidentity.rc.dao.ManualAssignmentNotificationMapDao;
import dk.digitalidentity.rc.dao.ManualNotificationPendingUserDao;
import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ManualAssignmentNotificationMap;
import dk.digitalidentity.rc.dao.model.ManualNotificationPendingUser;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ManualRolesServiceTest {

	private static final long DOMAIN_ID = 1L;
	private static final long IT_SYSTEM_ID = 10L;
	private static final long ROLE_ID = 100L;
	private static final String IT_SYSTEM_EMAIL = "servicedesk@example.com";
	private static final String IT_SYSTEM_ADVIS_EMAIL = "advis@example.com";

	@Mock private ManualAssignmentNotificationMapDao manualAssignmentNotificationMapDao;
	@Mock private UserService userService;
	@Mock private EmailTemplateDao emailTemplateDao;
	@Mock private UserRoleService userRoleService;
	@Mock private ItSystemService itSystemService;
	@Mock private EmailService emailService;
	@Mock private ManagerSubstituteService managerSubstituteService;
	@Mock private SettingsService settingsService;
	@Mock private AssignmentService assignmentService;
	@Mock private ManualAssignmentNotificationMapService manualAssignmentNotificationMapService;
	@Mock private ManualNotificationPendingUserDao manualNotificationPendingUserDao;

	@Spy private EmailTemplateRenderer emailTemplateRenderer;

	// real service with mocked dao, so the byte-equality tests below guard the actual seeded defaults
	private EmailTemplateService emailTemplateService;

	@InjectMocks private ManualRolesService manualRolesService;

	@BeforeEach
	void wireEmailTemplateService() {
		emailTemplateService = new EmailTemplateService();
		ReflectionTestUtils.setField(emailTemplateService, "emailTemplateDao", emailTemplateDao);
		ReflectionTestUtils.setField(manualRolesService, "emailTemplateService", emailTemplateService);
	}

	@Test
	@DisplayName("Tom pending-tabel: ingen behandling overhovedet")
	void emptyPendingTableIsNoOp() {
		given(manualNotificationPendingUserDao.findAll()).willReturn(List.of());

		manualRolesService.processPendingUsers();

		verifyNoInteractions(userService, itSystemService, assignmentService, emailService, settingsService);
		verify(manualNotificationPendingUserDao, never()).deleteAll(any());
	}

	@Test
	@DisplayName("To ændrede brugere får ny rolle på samme it-system: én digest-mail + map-rækker oprettet + pending tømt")
	void twoUsersAddedSameItSystemProduceSingleDigest() {
		Domain domain = domain();
		ItSystem itSystem = itSystem(IT_SYSTEM_EMAIL);
		UserRole role = role(itSystem);
		User u1 = user("u1", "user1", domain);
		User u2 = user("u2", "user2", domain);

		stubPending(u1, u2);
		stubResolvableUsers(u1, u2);
		stubManualItSystems(itSystem);
		stubRolesForItSystem(itSystem, role);
		// first run well in the past -> emails are allowed
		given(settingsService.getFirstManualITSystemRun()).willReturn(LocalDateTime.now().minusDays(1));
		stubDefaultTemplates();

		// both users currently hold the role, neither was previously notified -> both are "added"
		given(assignmentService.getByUserAndItSystems(eq(u1), anyList())).willReturn(Set.of(currentAssignment(u1, role, itSystem)));
		given(assignmentService.getByUserAndItSystems(eq(u2), anyList())).willReturn(Set.of(currentAssignment(u2, role, itSystem)));
		given(manualAssignmentNotificationMapService.getForUser(DOMAIN_ID, "user1")).willReturn(List.of());
		given(manualAssignmentNotificationMapService.getForUser(DOMAIN_ID, "user2")).willReturn(List.of());
		given(userRoleService.findAllByIdIn(any())).willReturn(Set.of());
		given(manualAssignmentNotificationMapService.save(any())).willAnswer(inv -> inv.getArgument(0));

		manualRolesService.processPendingUsers();

		// batching: two changed users on the same it-system collapse into a single mail to the system contact
		verify(emailService, times(1)).sendMessage(eq(IT_SYSTEM_EMAIL), anyString(), anyString(), isNull());
		// a notification-map baseline row is recorded for each user
		verify(manualAssignmentNotificationMapService, times(2)).save(any());
		// the processed pending rows are cleared
		verify(manualNotificationPendingUserDao).deleteAll(any());
	}

	@Test
	@DisplayName("Default-skabelon: digest-mailens emne og brødtekst er byte-identisk med det gamle hardcodede format (tilføjet rolle)")
	void defaultTemplateProducesExactLegacyBodyForAddedRole() {
		Domain domain = domain();
		ItSystem itSystem = itSystem(IT_SYSTEM_EMAIL);
		UserRole role = role(itSystem);
		User u1 = user("u1", "user1", domain);

		stubPending(u1);
		stubResolvableUsers(u1);
		stubManualItSystems(itSystem);
		stubRolesForItSystem(itSystem, role);
		given(settingsService.getFirstManualITSystemRun()).willReturn(LocalDateTime.now().minusDays(1));
		stubDefaultTemplates();

		given(assignmentService.getByUserAndItSystems(eq(u1), anyList())).willReturn(Set.of(currentAssignment(u1, role, itSystem)));
		given(manualAssignmentNotificationMapService.getForUser(DOMAIN_ID, "user1")).willReturn(List.of());
		given(userRoleService.findAllByIdIn(any())).willReturn(Set.of());
		given(manualAssignmentNotificationMapService.save(any())).willAnswer(inv -> inv.getArgument(0));

		manualRolesService.processPendingUsers();

		// expected output computed from the legacy html.email.manual.* MessageFormat patterns
		// (no responsible orgUnit -> "<ukendt enhed>", no extUuid -> "null" as MessageFormat rendered nulls)
		String expectedTitle = "Rettighedsændringer i Test IT System";
		String expectedBody = "<!DOCTYPE html><html><body><h4>Bestilling af rettighedsændringer</h4>"
				+ "<p>Der er ændringer til nedenstående brugeres rettigheder i Test IT System. "
				+ "Gå venligst til det relevante rettighedsstyringssystem for Test IT System, og foretag den tilsvarende ændring derinde.</p>"
				+ "<b>Name user1</b><br/>Brugernavn: user1<br/>Enhed: <ukendt enhed><br/>Uuid: null"
				+ "<ul><li>Tilføj rolle: Test Role (desc), tildelt af Test (test)</li></ul>"
				+ "</body></html>";

		verify(emailService, times(1)).sendMessage(eq(IT_SYSTEM_EMAIL), eq(expectedTitle), eq(expectedBody), isNull());
	}

	@Test
	@DisplayName("Default-skabelon: fjernet rolle giver byte-identisk 'Fjern rolle'-linje")
	void defaultTemplateProducesExactLegacyBodyForRemovedRole() {
		Domain domain = domain();
		ItSystem itSystem = itSystem(IT_SYSTEM_EMAIL);
		UserRole role = role(itSystem);
		User u1 = user("u1", "user1", domain);
		ManualAssignmentNotificationMap mapRow = mapRow(1L, ROLE_ID, "user1");
		mapRow.setOrgUnitName("Afdeling X");
		mapRow.setAssignedBy("Test (test)");

		stubPending(u1);
		stubResolvableUsers(u1);
		stubManualItSystems(itSystem);
		stubRolesForItSystem(itSystem, role);
		given(settingsService.getFirstManualITSystemRun()).willReturn(LocalDateTime.now().minusDays(1));
		stubDefaultTemplates();

		// user no longer holds any assignment on the system, but a prior map row exists -> removal
		given(assignmentService.getByUserAndItSystems(eq(u1), anyList())).willReturn(Set.of());
		given(manualAssignmentNotificationMapService.getForUser(DOMAIN_ID, "user1")).willReturn(List.of(mapRow));
		given(userRoleService.findAllByIdIn(any())).willReturn(Set.of(role));

		manualRolesService.processPendingUsers();

		String expectedTitle = "Rettighedsændringer i Test IT System";
		String expectedBody = "<!DOCTYPE html><html><body><h4>Bestilling af rettighedsændringer</h4>"
				+ "<p>Der er ændringer til nedenstående brugeres rettigheder i Test IT System. "
				+ "Gå venligst til det relevante rettighedsstyringssystem for Test IT System, og foretag den tilsvarende ændring derinde.</p>"
				+ "<b>Name user1</b><br/>Brugernavn: user1<br/>Enhed: Afdeling X<br/>Uuid: null"
				+ "<ul><li>Fjern rolle: Test Role (desc), fjernet af Test (test)</li></ul>"
				+ "</body></html>";

		verify(emailService, times(1)).sendMessage(eq(IT_SYSTEM_EMAIL), eq(expectedTitle), eq(expectedBody), isNull());
		verify(manualAssignmentNotificationMapDao).deleteAll(any());
	}

	@Test
	@DisplayName("Advis-mail: sendes til advis-adressen når advis-skabelonen er aktiv")
	void advisMailSentWhenAdvisTemplateEnabled() {
		Domain domain = domain();
		ItSystem itSystem = itSystem(IT_SYSTEM_EMAIL);
		itSystem.setAdvisEmail(IT_SYSTEM_ADVIS_EMAIL);
		UserRole role = role(itSystem);
		User u1 = user("u1", "user1", domain);

		stubPending(u1);
		stubResolvableUsers(u1);
		stubManualItSystems(itSystem);
		stubRolesForItSystem(itSystem, role);
		given(settingsService.getFirstManualITSystemRun()).willReturn(LocalDateTime.now().minusDays(1));
		stubDefaultTemplates();
		enableTemplate(EmailTemplateType.MANUAL_SYSTEM_CONTACT_ADVIS);

		given(assignmentService.getByUserAndItSystems(eq(u1), anyList())).willReturn(Set.of(currentAssignment(u1, role, itSystem)));
		given(manualAssignmentNotificationMapService.getForUser(DOMAIN_ID, "user1")).willReturn(List.of());
		given(userRoleService.findAllByIdIn(any())).willReturn(Set.of());
		given(manualAssignmentNotificationMapService.save(any())).willAnswer(inv -> inv.getArgument(0));

		manualRolesService.processPendingUsers();

		verify(emailService, times(1)).sendMessage(eq(IT_SYSTEM_EMAIL), anyString(), anyString(), isNull());
		verify(emailService, times(1)).sendMessage(eq(IT_SYSTEM_ADVIS_EMAIL), anyString(), anyString(), isNull());
	}

	@Test
	@DisplayName("Advis-mail: sendes IKKE når advis-skabelonen er inaktiv (default)")
	void advisMailNotSentWhenAdvisTemplateDisabled() {
		Domain domain = domain();
		ItSystem itSystem = itSystem(IT_SYSTEM_EMAIL);
		itSystem.setAdvisEmail(IT_SYSTEM_ADVIS_EMAIL);
		UserRole role = role(itSystem);
		User u1 = user("u1", "user1", domain);

		stubPending(u1);
		stubResolvableUsers(u1);
		stubManualItSystems(itSystem);
		stubRolesForItSystem(itSystem, role);
		given(settingsService.getFirstManualITSystemRun()).willReturn(LocalDateTime.now().minusDays(1));
		stubDefaultTemplates();

		given(assignmentService.getByUserAndItSystems(eq(u1), anyList())).willReturn(Set.of(currentAssignment(u1, role, itSystem)));
		given(manualAssignmentNotificationMapService.getForUser(DOMAIN_ID, "user1")).willReturn(List.of());
		given(userRoleService.findAllByIdIn(any())).willReturn(Set.of());
		given(manualAssignmentNotificationMapService.save(any())).willAnswer(inv -> inv.getArgument(0));

		manualRolesService.processPendingUsers();

		verify(emailService, times(1)).sendMessage(eq(IT_SYSTEM_EMAIL), anyString(), anyString(), isNull());
		verify(emailService, never()).sendMessage(eq(IT_SYSTEM_ADVIS_EMAIL), anyString(), anyString(), isNull());
	}

	@Test
	@DisplayName("Rolle-kontaktmail: byte-identisk med det gamle rolle-grupperede format")
	void roleContactMailProducesExactLegacyBody() {
		Domain domain = domain();
		ItSystem itSystem = itSystem(IT_SYSTEM_EMAIL);
		UserRole role = role(itSystem);
		role.setContactEmail("rolle@example.com");
		User u1 = user("u1", "user1", domain);

		stubPending(u1);
		stubResolvableUsers(u1);
		stubManualItSystems(itSystem);
		stubRolesForItSystem(itSystem, role);
		given(settingsService.getFirstManualITSystemRun()).willReturn(LocalDateTime.now().minusDays(1));
		stubDefaultTemplates();

		given(assignmentService.getByUserAndItSystems(eq(u1), anyList())).willReturn(Set.of(currentAssignment(u1, role, itSystem)));
		given(manualAssignmentNotificationMapService.getForUser(DOMAIN_ID, "user1")).willReturn(List.of());
		given(userRoleService.findAllByIdIn(any())).willReturn(Set.of());
		given(manualAssignmentNotificationMapService.save(any())).willAnswer(inv -> inv.getArgument(0));

		manualRolesService.processPendingUsers();

		// expected output computed from the legacy html.email.manual.userRole/addUser MessageFormat patterns
		String expectedTitle = "Rettighedsændringer i Test IT System (Test Role)";
		String expectedBody = "<!DOCTYPE html><html><body><h4>Bestilling af rettighedsændringer</h4>"
				+ "<p>Der er ændringer til nedenstående brugeres rettigheder i Test Role. "
				+ "Gå venligst til det relevante rettighedsstyringssystem for Test Role, og foretag den tilsvarende ændring derinde.</p>"
				+ "Jobfunktionsrolle: Test Role<br/>(desc)"
				+ "<ul><li>Tilføj bruger: Name user1 (user1) Uuid: null</li></ul>"
				+ "</body></html>";

		verify(emailService, times(1)).sendMessage(eq("rolle@example.com"), eq(expectedTitle), eq(expectedBody), isNull());
	}

	@Test
	@DisplayName("Rolle-kontaktmail: adresse der allerede dækkes af it-systemets kontaktmail springes over")
	void roleContactMailDedupedAgainstItSystemEmail() {
		Domain domain = domain();
		ItSystem itSystem = itSystem(IT_SYSTEM_EMAIL);
		UserRole role = role(itSystem);
		role.setContactEmail(IT_SYSTEM_EMAIL);
		User u1 = user("u1", "user1", domain);

		stubPending(u1);
		stubResolvableUsers(u1);
		stubManualItSystems(itSystem);
		stubRolesForItSystem(itSystem, role);
		given(settingsService.getFirstManualITSystemRun()).willReturn(LocalDateTime.now().minusDays(1));
		stubDefaultTemplates();

		given(assignmentService.getByUserAndItSystems(eq(u1), anyList())).willReturn(Set.of(currentAssignment(u1, role, itSystem)));
		given(manualAssignmentNotificationMapService.getForUser(DOMAIN_ID, "user1")).willReturn(List.of());
		given(userRoleService.findAllByIdIn(any())).willReturn(Set.of());
		given(manualAssignmentNotificationMapService.save(any())).willAnswer(inv -> inv.getArgument(0));

		manualRolesService.processPendingUsers();

		// only the it-system digest mail - the role mail to the same address is skipped
		verify(emailService, times(1)).sendMessage(eq(IT_SYSTEM_EMAIL), anyString(), anyString(), isNull());
	}

	@Test
	@DisplayName("Rolle-advis: sendes til rollens advis-adresse når rolle-advis-skabelonen er aktiv, selvom rollen ingen contactEmail har")
	void roleAdvisMailSentForRoleWithOnlyAdvisEmail() {
		Domain domain = domain();
		ItSystem itSystem = itSystem(IT_SYSTEM_EMAIL);
		UserRole role = role(itSystem);
		role.setAdvisEmail("rolle-advis@example.com");
		User u1 = user("u1", "user1", domain);

		stubPending(u1);
		stubResolvableUsers(u1);
		stubManualItSystems(itSystem);
		stubRolesForItSystem(itSystem, role);
		given(settingsService.getFirstManualITSystemRun()).willReturn(LocalDateTime.now().minusDays(1));
		stubDefaultTemplates();
		enableTemplate(EmailTemplateType.MANUAL_ROLE_CONTACT_ADVIS);

		given(assignmentService.getByUserAndItSystems(eq(u1), anyList())).willReturn(Set.of(currentAssignment(u1, role, itSystem)));
		given(manualAssignmentNotificationMapService.getForUser(DOMAIN_ID, "user1")).willReturn(List.of());
		given(userRoleService.findAllByIdIn(any())).willReturn(Set.of());
		given(manualAssignmentNotificationMapService.save(any())).willAnswer(inv -> inv.getArgument(0));

		manualRolesService.processPendingUsers();

		verify(emailService, times(1)).sendMessage(eq("rolle-advis@example.com"), anyString(), anyString(), isNull());
	}

	@Test
	@DisplayName("It-system uden kontakt-mail og rolle uden contactEmail: springes over, ingen mail")
	void itSystemWithoutContactEmailIsSkipped() {
		Domain domain = domain();
		ItSystem itSystem = itSystem(null); // no system email
		UserRole role = role(itSystem); // no contact email
		User u1 = user("u1", "user1", domain);

		stubPending(u1);
		stubResolvableUsers(u1);
		stubManualItSystems(itSystem);
		stubRolesForItSystem(itSystem, role);
		given(settingsService.getFirstManualITSystemRun()).willReturn(LocalDateTime.now().minusDays(1));

		given(assignmentService.getByUserAndItSystems(eq(u1), anyList())).willReturn(Set.of(currentAssignment(u1, role, itSystem)));
		given(manualAssignmentNotificationMapService.getForUser(DOMAIN_ID, "user1")).willReturn(List.of());
		given(userRoleService.findAllByIdIn(any())).willReturn(Set.of());

		manualRolesService.processPendingUsers();

		verifyNoInteractions(emailService);
		verify(manualAssignmentNotificationMapService, never()).save(any());
		// pending is still cleared so the row does not pile up
		verify(manualNotificationPendingUserDao).deleteAll(any());
	}

	@Test
	@DisplayName("Inden for 3-timers cooling-off: ingen mail, men baseline-map vedligeholdes")
	void withinCoolingOffNoMailButMapMaintained() {
		Domain domain = domain();
		ItSystem itSystem = itSystem(IT_SYSTEM_EMAIL);
		UserRole role = role(itSystem);
		User u1 = user("u1", "user1", domain);

		stubPending(u1);
		stubResolvableUsers(u1);
		stubManualItSystems(itSystem);
		stubRolesForItSystem(itSystem, role);
		// first run just now -> still inside the 3 hour cooling-off window
		given(settingsService.getFirstManualITSystemRun()).willReturn(LocalDateTime.now());
		stubDefaultTemplates();

		given(assignmentService.getByUserAndItSystems(eq(u1), anyList())).willReturn(Set.of(currentAssignment(u1, role, itSystem)));
		given(manualAssignmentNotificationMapService.getForUser(DOMAIN_ID, "user1")).willReturn(List.of());
		given(userRoleService.findAllByIdIn(any())).willReturn(Set.of());
		given(manualAssignmentNotificationMapService.save(any())).willAnswer(inv -> inv.getArgument(0));

		manualRolesService.processPendingUsers();

		verifyNoInteractions(emailService);
		// baseline is still recorded so the user is not re-notified once the cooling-off passes
		verify(manualAssignmentNotificationMapService, times(1)).save(any());
		verify(manualNotificationPendingUserDao).deleteAll(any());
	}

	// ---- helpers ---- //

	private void stubPending(User... users) {
		given(manualNotificationPendingUserDao.findAll()).willReturn(
			java.util.Arrays.stream(users).map(u -> pendingRow(u.getUuid())).toList());
	}

	private void stubResolvableUsers(User... users) {
		for (User u : users) {
			given(userService.getOptionalByUuid(u.getUuid())).willReturn(Optional.of(u));
		}
	}

	private void stubManualItSystems(ItSystem... itSystems) {
		given(itSystemService.getBySystemTypeIn(anyList())).willReturn(List.of(itSystems));
	}

	private void stubRolesForItSystem(ItSystem itSystem, UserRole... roles) {
		given(userRoleService.getByItSystem(itSystem)).willReturn(List.of(roles));
	}

	/**
	 * Wires the real EmailTemplateService seeding through the mocked dao: every findByTemplateType
	 * call lazily seeds the actual defaults (performer enabled, advis disabled), so the byte-equality
	 * tests render the same content production seeds.
	 */
	private void stubDefaultTemplates() {
		given(emailTemplateDao.findByTemplateType(any())).willReturn(null);
		given(emailTemplateDao.save(any(EmailTemplate.class))).willAnswer(inv -> inv.getArgument(0));
	}

	/** Seeds the real default for the given type and flips it to enabled. */
	private void enableTemplate(EmailTemplateType type) {
		EmailTemplate template = emailTemplateService.findByTemplateType(type);
		template.setEnabled(true);
		given(emailTemplateDao.findByTemplateType(type)).willReturn(template);
	}

	private Domain domain() {
		Domain domain = new Domain();
		domain.setId(DOMAIN_ID);
		domain.setName("Administrativt");
		return domain;
	}

	private ItSystem itSystem(String email) {
		ItSystem itSystem = new ItSystem();
		itSystem.setId(IT_SYSTEM_ID);
		itSystem.setName("Test IT System");
		itSystem.setEmail(email);
		return itSystem;
	}

	private UserRole role(ItSystem itSystem) {
		UserRole role = new UserRole();
		role.setId(ROLE_ID);
		role.setName("Test Role");
		role.setDescription("desc");
		role.setItSystem(itSystem);
		return role;
	}

	private User user(String uuid, String userId, Domain domain) {
		User user = new User();
		user.setUuid(uuid);
		user.setUserId(userId);
		user.setName("Name " + userId);
		user.setDomain(domain);
		return user;
	}

	private CurrentAssignment currentAssignment(User user, UserRole role, ItSystem itSystem) {
		CurrentAssignment assignment = new CurrentAssignment();
		assignment.setUser(user);
		assignment.setUserRole(role);
		assignment.setItSystem(itSystem);
		assignment.setAssignedBy("Test (test)");
		return assignment;
	}

	private ManualAssignmentNotificationMap mapRow(long id, long roleId, String userUserId) {
		ManualAssignmentNotificationMap map = new ManualAssignmentNotificationMap();
		map.setId(id);
		map.setUserRoleId(roleId);
		map.setUserUserId(userUserId);
		map.setDomainId(DOMAIN_ID);
		return map;
	}

	private ManualNotificationPendingUser pendingRow(String userUuid) {
		ManualNotificationPendingUser pending = new ManualNotificationPendingUser();
		pending.setUserUuid(userUuid);
		pending.setCreatedAt(LocalDateTime.now());
		return pending;
	}
}
