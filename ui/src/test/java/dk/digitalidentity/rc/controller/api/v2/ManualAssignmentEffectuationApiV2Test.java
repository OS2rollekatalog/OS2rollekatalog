package dk.digitalidentity.rc.controller.api.v2;

import dk.digitalidentity.rc.dao.ManualAssignmentEffectuationDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ManualAssignmentEffectuation;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.ManualAssignmentEffectuationOperation;
import dk.digitalidentity.rc.dao.model.enums.ManualAssignmentEffectuationStatus;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.test.AbstractApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test suite for the Manual Assignment Effectuation API v2 endpoints, allowing an external
 * script/robot to be the "rolletildelingsudfører" instead of a human via the UI.
 */
@DisplayName("Manual Assignment Effectuation API v2 Tests")
class ManualAssignmentEffectuationApiV2Test extends AbstractApiTest {

	@Value("${tests.username}")
	private String username;

	@Autowired
	private UserService userService;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private ManualAssignmentEffectuationDao manualAssignmentEffectuationDao;

	@Override
	protected List<String> getRequiredApiRoles() {
		return List.of(AccessRole.ROLE_MANAGEMENT.toString());
	}

	private ItSystem createManualItSystem() {
		ItSystem itSystem = new ItSystem();
		itSystem.setName("Manual test system " + UUID.randomUUID());
		itSystem.setIdentifier("manual-test-" + UUID.randomUUID());
		itSystem.setSystemType(ItSystemType.MANUAL);
		itSystem.setManualEffectuationEnabled(true);
		return itSystemService.save(itSystem);
	}

	private UserRole createUserRole(ItSystem itSystem) {
		UserRole userRole = new UserRole();
		userRole.setName("Test role " + UUID.randomUUID());
		userRole.setIdentifier("test-role-" + UUID.randomUUID());
		userRole.setItSystem(itSystem);
		return userRoleService.save(userRole);
	}

	private ManualAssignmentEffectuation createPendingEffectuation(User user, UserRole userRole, ItSystem itSystem) {
		ManualAssignmentEffectuation effectuation = new ManualAssignmentEffectuation();
		effectuation.setUser(user);
		effectuation.setUserRole(userRole);
		effectuation.setItSystem(itSystem);
		effectuation.setOperation(ManualAssignmentEffectuationOperation.ASSIGN);
		effectuation.setStatus(ManualAssignmentEffectuationStatus.PENDING);
		effectuation.setCreatedAt(LocalDateTime.now());
		return manualAssignmentEffectuationDao.save(effectuation);
	}

	@Test
	@DisplayName("Should return all pending effectuation tasks")
	void testGetPendingEffectuations() throws Exception {
		User user = userService.getByUserId(username);
		ItSystem itSystem = createManualItSystem();
		UserRole userRole = createUserRole(itSystem);
		createPendingEffectuation(user, userRole, itSystem);

		MvcResult result = this.mockMvc.perform(get("/api/v2/manualeffectuation")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$").isNotEmpty())
			.andDo(document("manualeffectuation-v2-list",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				responseFields(
					fieldWithPath("[]").description("Array of pending effectuation tasks"),
					fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("Unique ID of the effectuation task"),
					fieldWithPath("[].userUuid").type(JsonFieldType.STRING).description("UUID of the user the assignment concerns"),
					fieldWithPath("[].itSystemId").type(JsonFieldType.NUMBER).description("ID of the it-system the assignment concerns"),
					fieldWithPath("[].itSystemName").type(JsonFieldType.STRING).description("Name of the it-system the assignment concerns"),
					fieldWithPath("[].userRoleId").type(JsonFieldType.NUMBER).description("ID of the user-role").optional(),
					fieldWithPath("[].userRoleName").type(JsonFieldType.STRING).description("Name of the user-role").optional(),
					fieldWithPath("[].operation").type(JsonFieldType.STRING).description("ASSIGN or REMOVE"),
					fieldWithPath("[].createdAt").type(JsonFieldType.STRING).description("When the task was created")
				)
			))
			.andReturn();

		String body = result.getResponse().getContentAsString();
		assertThat(body).contains(itSystem.getName());
	}

	@Test
	@DisplayName("Should mark a pending effectuation task as executed")
	void testCompleteEffectuation() throws Exception {
		User user = userService.getByUserId(username);
		ItSystem itSystem = createManualItSystem();
		UserRole userRole = createUserRole(itSystem);
		ManualAssignmentEffectuation effectuation = createPendingEffectuation(user, userRole, itSystem);

		this.mockMvc.perform(post("/api/v2/manualeffectuation/{id}/complete", effectuation.getId())
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"performerIdentifier\": \"integration-robot\", \"comment\": \"Done via script\"}"))
			.andExpect(status().isOk())
			.andDo(document("manualeffectuation-v2-complete",
				preprocessRequest(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("Unique ID of the effectuation task")
				),
				requestFields(
					fieldWithPath("performerIdentifier").type(JsonFieldType.STRING).description("Identifier of the performer completing the task - a user UUID, or any identifier for the calling integration/robot"),
					fieldWithPath("comment").type(JsonFieldType.STRING).description("Optional free-text comment").optional()
				)
			))
			.andReturn();

		ManualAssignmentEffectuation updated = manualAssignmentEffectuationDao.findById(effectuation.getId()).orElseThrow();
		assertThat(updated.getStatus()).isEqualTo(ManualAssignmentEffectuationStatus.COMPLETED);
		assertThat(updated.getCompletedByUserUuid()).isEqualTo("integration-robot");
		assertThat(updated.getComment()).isEqualTo("Done via script");
	}

	@Test
	@DisplayName("Should be idempotent when completing an already-effectuated task")
	void testCompleteEffectuationIsIdempotent() throws Exception {
		User user = userService.getByUserId(username);
		ItSystem itSystem = createManualItSystem();
		UserRole userRole = createUserRole(itSystem);
		ManualAssignmentEffectuation effectuation = createPendingEffectuation(user, userRole, itSystem);
		effectuation.setStatus(ManualAssignmentEffectuationStatus.COMPLETED);
		effectuation.setCompletedByUserUuid("someone-else");
		manualAssignmentEffectuationDao.save(effectuation);

		this.mockMvc.perform(post("/api/v2/manualeffectuation/{id}/complete", effectuation.getId())
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"performerIdentifier\": \"integration-robot\"}"))
			.andExpect(status().isOk());

		ManualAssignmentEffectuation stillOriginal = manualAssignmentEffectuationDao.findById(effectuation.getId()).orElseThrow();
		assertThat(stillOriginal.getCompletedByUserUuid()).isEqualTo("someone-else");
	}

	@Test
	@DisplayName("Should return 404 when completing a non-existent task")
	void testCompleteEffectuationNotFound() throws Exception {
		this.mockMvc.perform(post("/api/v2/manualeffectuation/{id}/complete", 999_999_999L)
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"performerIdentifier\": \"integration-robot\"}"))
			.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("Should reject a blank performerIdentifier")
	void testCompleteEffectuationRequiresPerformerIdentifier() throws Exception {
		User user = userService.getByUserId(username);
		ItSystem itSystem = createManualItSystem();
		UserRole userRole = createUserRole(itSystem);
		ManualAssignmentEffectuation effectuation = createPendingEffectuation(user, userRole, itSystem);

		this.mockMvc.perform(post("/api/v2/manualeffectuation/{id}/complete", effectuation.getId())
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"performerIdentifier\": \"\"}"))
			.andExpect(status().isBadRequest());
	}

}
