package dk.digitalidentity.rc.controller.api.v2;

import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.service.ConstraintTypeService;
import dk.digitalidentity.rc.test.AbstractApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.restdocs.payload.JsonFieldType;

import java.util.List;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test suite for Constraint API v2 endpoints.
 * <p>
 * Tests API endpoints for retrieving constraint types, which define validation rules
 * and UI behavior for constraints applied to roles and other entities.
 * </p>
 * <p>
 * Note: All tests verify both the HTTP status and REST documentation generation.
 * </p>
 */
@DisplayName("Constraint API v2 Tests")
public class ConstraintApiV2Test extends AbstractApiTest {

	@Autowired
	private ConstraintTypeService constraintTypeService;

	@Override
	protected List<String> getRequiredApiRoles() {
		return List.of(AccessRole.READ_ACCESS.toString());
	}

	/**
	 * Tests retrieval of all constraint types.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response is an array of constraint types
	 * - All expected fields are present with correct types
	 * - REST documentation includes complete field descriptions
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return all constraint types")
	void testGetAllConstraintTypes() throws Exception {

		int expectedConstraintTypes = constraintTypeService.getAll().size();

		this.mockMvc.perform(get("/api/v2/constraint")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$.length()").value(expectedConstraintTypes))
			.andDo(document("constraint-v2-list",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				responseFields(
					fieldWithPath("[]").description("Array of constraint types"),
					fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("Unique ID of the constraint type"),
					fieldWithPath("[].uuid").type(JsonFieldType.STRING).description("UUID of the constraint type"),
					fieldWithPath("[].name").type(JsonFieldType.STRING).description("Name of the constraint type"),
					fieldWithPath("[].description").type(JsonFieldType.STRING).description("Description of the constraint type").optional(),
					fieldWithPath("[].entityId").type(JsonFieldType.STRING).description("Entity ID of the constraint type"),
					fieldWithPath("[].uiType").type(JsonFieldType.STRING).description("UI type for the constraint"),
					fieldWithPath("[].regex").type(JsonFieldType.STRING).description("Regex pattern for validation").optional()
				)
			));
	}

	/**
	 * Tests retrieval of a specific constraint type by ID.
	 * <p>
	 * Uses the first available constraint type from the database to ensure test data exists.
	 * See {@link #testGetAllConstraintTypes()} for full response field documentation.
	 * </p>
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK for existing constraint type
	 * - Response contains the correct constraint type ID
	 * - REST documentation includes path parameter description
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or no constraint types exist in test database
	 */
	@Test
	@DisplayName("Should return specific constraint type by ID")
	void testGetConstraintTypeById() throws Exception {
		ConstraintType constraintType = constraintTypeService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No constraint type found"));

		this.mockMvc.perform(get("/api/v2/constraint/{id}", constraintType.getId())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(constraintType.getId()))
			.andDo(document("constraint-v2-get",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("Unique ID of the constraint type")
				)
			));
	}

	/**
	 * Tests that requesting a non-existent constraint type returns 404.
	 * <p>
	 * Verifies proper error handling when attempting to retrieve a constraint type
	 * with an ID that doesn't exist in the database.
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when constraint type ID does not exist")
	void testGetConstraintTypeById_NotFound() throws Exception {
		this.mockMvc.perform(get("/api/v2/constraint/{id}", 999999)
				.header("ApiKey", API_KEY))
			.andExpect(status().isNotFound());
	}
}
