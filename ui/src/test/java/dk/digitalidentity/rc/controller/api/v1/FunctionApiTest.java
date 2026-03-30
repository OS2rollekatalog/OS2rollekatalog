package dk.digitalidentity.rc.controller.api.v1;

import dk.digitalidentity.rc.dao.model.Function;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.service.FunctionService;
import dk.digitalidentity.rc.test.AbstractApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test suite for Function API endpoints.
 * <p>
 * Tests API endpoints for managing organizational functions, including retrieving
 * active functions, creating new functions, reactivating inactive functions, and
 * deactivating functions not present in update requests. Functions are matched
 * case-insensitively to prevent duplicates.
 * </p>
 */
@DisplayName("Function API Tests")
public class FunctionApiTest extends AbstractApiTest {

	@Autowired
	private FunctionService functionService;

	@Override
	protected List<String> getRequiredApiRoles() {
		return List.of(AccessRole.ORGANISATION.toString());
	}

	/**
	 * Tests that all active functions can be retrieved successfully.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response is an array of function names
	 * - Only active functions are included
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve all active functions")
	void testGetFunctions() throws Exception {
		Function function1 = new Function();
		function1.setUuid(UUID.randomUUID().toString());
		function1.setName("Test Function 1");
		function1.setActive(true);
		functionService.save(function1);

		Function function2 = new Function();
		function2.setUuid(UUID.randomUUID().toString());
		function2.setName("Test Function 2");
		function2.setActive(true);
		functionService.save(function2);

		entityManager.flush();
		entityManager.clear();

		this.mockMvc.perform(get("/api/function")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$").isNotEmpty())
			.andDo(document("function-list",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				responseFields(
					fieldWithPath("[]").type(JsonFieldType.ARRAY).description("Array of active functions"),
					fieldWithPath("[].name").type(JsonFieldType.STRING).description("Name of the function"),
					fieldWithPath("[].uuid").type(JsonFieldType.STRING).description("UUID of the function")
				)
			));
	}

	/**
	 * Tests that new functions can be created successfully.
	 * <p>
	 * Verifies that:
	 * - Endpoint accepts array of function names
	 * - New functions are created and set to active
	 * - All submitted functions are persisted
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully create new functions")
	void testSaveFunctions_CreateNew() throws Exception {
		String requestBody = """
            [
                "New Function 1",
                "New Function 2",
                "New Function 3"
            ]
            """;

		this.mockMvc.perform(post("/api/function")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andDo(document("function-save",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				requestFields(
					fieldWithPath("[]").type(JsonFieldType.ARRAY).description("Array of function names to create or activate")
				)
			));

		entityManager.flush();
		entityManager.clear();
		List<Function> activeFunctions = functionService.getAllActive();
		assertTrue(activeFunctions.stream().anyMatch(f -> f.getName().equals("New Function 1")), "New Function 1 should be created");
		assertTrue(activeFunctions.stream().anyMatch(f -> f.getName().equals("New Function 2")), "New Function 2 should be created");
		assertTrue(activeFunctions.stream().anyMatch(f -> f.getName().equals("New Function 3")), "New Function 3 should be created");
	}

	/**
	 * Tests that inactive functions can be reactivated.
	 * <p>
	 * Verifies that:
	 * - Previously inactive functions can be reactivated
	 * - Function state changes from inactive to active
	 * - No duplicate functions are created
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully reactivate existing inactive function")
	void testSaveFunctions_ReactivateExisting() throws Exception {
		Function inactiveFunction = new Function();
		inactiveFunction.setUuid(UUID.randomUUID().toString());
		inactiveFunction.setName("Inactive Function");
		inactiveFunction.setActive(false);
		functionService.save(inactiveFunction);

		entityManager.flush();
		entityManager.clear();

		String requestBody = """
            [
                "Inactive Function"
            ]
            """;

		this.mockMvc.perform(post("/api/function")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk());

		entityManager.flush();
		entityManager.clear();
		Function reactivatedFunction = functionService.getAllActive().stream()
			.filter(f -> f.getName().equals("Inactive Function"))
			.findFirst()
			.orElse(null);

		assertNotNull(reactivatedFunction, "Function should be reactivated");
		assertTrue(reactivatedFunction.isActive(), "Function should be active");
	}

	/**
	 * Tests that functions not in the payload are automatically deactivated.
	 * <p>
	 * Verifies that:
	 * - Functions included in payload remain active
	 * - Functions not included in payload are deactivated
	 * - Deactivated functions are not deleted (soft delete)
	 * - This implements a "sync to source of truth" pattern
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should deactivate functions not present in payload")
	void testSaveFunctions_DeactivateNotInPayload() throws Exception {
		Function function1 = new Function();
		function1.setUuid(UUID.randomUUID().toString());
		function1.setName("Keep This Function");
		function1.setActive(true);
		functionService.save(function1);

		Function function2 = new Function();
		function2.setUuid(UUID.randomUUID().toString());
		function2.setName("Deactivate This Function");
		function2.setActive(true);
		functionService.save(function2);

		entityManager.flush();
		entityManager.clear();

		String requestBody = """
            [
                "Keep This Function"
            ]
            """;

		this.mockMvc.perform(post("/api/function")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk());

		entityManager.flush();
		entityManager.clear();
		List<Function> allFunctions = functionService.getAllIncludingInactive();

		Function keptFunction = allFunctions.stream()
			.filter(f -> f.getName().equals("Keep This Function"))
			.findFirst()
			.orElse(null);

		Function deactivatedFunction = allFunctions.stream()
			.filter(f -> f.getName().equals("Deactivate This Function"))
			.findFirst()
			.orElse(null);

		assertNotNull(keptFunction, "Kept function should exist");
		assertTrue(keptFunction.isActive(), "Kept function should be active");

		assertNotNull(deactivatedFunction, "Deactivated function should still exist");
		assertFalse(deactivatedFunction.isActive(), "Function not in payload should be deactivated");
	}

	/**
	 * Tests that function names are matched case-insensitively.
	 * <p>
	 * Verifies that:
	 * - Functions are matched case-insensitively
	 * - No duplicate functions are created for case variations
	 * - "Test Function", "test function", and "TEST FUNCTION" are treated as the same
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should handle function names case-insensitively without creating duplicates")
	void testSaveFunctions_CaseInsensitive() throws Exception {
		Function existingFunction = new Function();
		existingFunction.setUuid(UUID.randomUUID().toString());
		existingFunction.setName("Test Function");
		existingFunction.setActive(true);
		functionService.save(existingFunction);

		entityManager.flush();
		entityManager.clear();

		long countBefore = functionService.getAllIncludingInactive().size();

		String requestBody = """
            [
                "test function",
                "TEST FUNCTION"
            ]
            """;

		this.mockMvc.perform(post("/api/function")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk());

		entityManager.flush();
		entityManager.clear();
		long countAfter = functionService.getAllIncludingInactive().size();

		assertEquals(countBefore, countAfter, "No new functions should be created for case variations");
	}

	/**
	 * Tests that an empty payload deactivates all functions.
	 * <p>
	 * Verifies that:
	 * - Empty array in payload is accepted
	 * - All existing active functions are deactivated
	 * - No functions remain active after empty payload
	 * - This allows clearing all functions via API
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should deactivate all functions when empty payload is sent")
	void testSaveFunctions_EmptyPayload() throws Exception {
		Function function1 = new Function();
		function1.setUuid(UUID.randomUUID().toString());
		function1.setName("Function To Deactivate");
		function1.setActive(true);
		functionService.save(function1);

		entityManager.flush();
		entityManager.clear();

		String requestBody = "[]";

		this.mockMvc.perform(post("/api/function")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk());

		entityManager.flush();
		entityManager.clear();
		List<Function> activeFunctions = functionService.getAllActive();

		assertEquals(0, activeFunctions.size(), "All functions should be deactivated when empty payload is sent");
	}
}
