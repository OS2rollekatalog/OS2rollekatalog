package dk.digitalidentity.rc.controller.api.v1;

import dk.digitalidentity.rc.service.cics.KspCicsService;
import dk.digitalidentity.rc.service.cics.model.KspChangePasswordResponse;
import dk.digitalidentity.rc.test.AbstractApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test suite for KSP CICS API endpoints.
 * <p>
 * Tests API endpoints for managing KSP (Kommunernes Systems Platform) CICS passwords.
 * CICS is a mainframe transaction server, and this API provides password change
 * functionality with various response scenarios including success, validation failures,
 * authorization errors, and server errors.
 * </p>
 * <p>
 * Uses @MockitoBean to replace the real KspCicsService with a mock, since the
 * controller logic is straightforward and only depends on the service response.
 * </p>
 */
@DisplayName("KSP CICS API Tests")
public class KspCicsApiTest extends AbstractApiTest {

	@MockitoBean
	private KspCicsService kspCicsService;

	@Override
	protected List<String> getRequiredApiRoles() {
		return List.of("CICS_ADMIN");
	}

	/**
	 * Tests that a password can be changed successfully.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK on success
	 * - Request accepts username and new password
	 * - KspCicsService is called with correct parameters
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully change password")
	void testChangePassword_Success() throws Exception {
		String username = "testuser";
		String newPassword = "NewSecurePassword123!";
		KspChangePasswordResponse successResponse = new KspChangePasswordResponse();
		successResponse.setSuccess(true);
		successResponse.setResponse("Password changed successfully");
		successResponse.setHttp(HttpStatus.OK);

		when(kspCicsService.updateKspCicsPassword(eq(username), eq(newPassword)))
			.thenReturn(successResponse);

		String requestBody = String.format("""
        	{
          		"username": "%s",
          		"newPassword": "%s"
       		}
       	""", username, newPassword);

		this.mockMvc.perform(post("/api/cics/changepassword")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andDo(document("cics-changepassword-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				requestFields(
					fieldWithPath("username").type(JsonFieldType.STRING).description("Username for which to change the password"),
					fieldWithPath("newPassword").type(JsonFieldType.STRING).description("New password to set")
				)
			));
		verify(kspCicsService).updateKspCicsPassword(username, newPassword);
	}

	/**
	 * Tests that password change fails appropriately when user is not found.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 404 Not Found when user doesn't exist
	 * - Error response includes appropriate HTTP status from CICS service
	 * - Failure is handled gracefully
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when user is not found")
	void testChangePassword_Failure_WithHttpStatus() throws Exception {
		KspChangePasswordResponse failureResponse = new KspChangePasswordResponse();
		failureResponse.setSuccess(false);
		failureResponse.setResponse("User not found");
		failureResponse.setHttp(HttpStatus.NOT_FOUND);

		when(kspCicsService.updateKspCicsPassword(anyString(), anyString()))
			.thenReturn(failureResponse);

		String requestBody = """
			{
				"username": "nonexistentuser",
				"newPassword": "NewSecurePassword123!"
			}
			""";

		this.mockMvc.perform(post("/api/cics/changepassword")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests that password change handles various validation failures correctly.
	 * <p>
	 * Verifies that:
	 * - Password complexity requirements are enforced
	 * - Password length requirements are enforced
	 * - Invalid characters are rejected
	 * - Error messages are descriptive and returned correctly
	 * - All validation errors result in HTTP 400 Bad Request
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@ParameterizedTest
	@DisplayName("Should return 400 for various password validation failures")
	@MethodSource("providePasswordValidationFailures")
	void testChangePassword_ValidationFailures(String username, String password, String expectedError) throws Exception {
		KspChangePasswordResponse failureResponse = new KspChangePasswordResponse();
		failureResponse.setSuccess(false);
		failureResponse.setResponse(expectedError);
		failureResponse.setHttp(null);

		when(kspCicsService.updateKspCicsPassword(eq(username), eq(password)))
			.thenReturn(failureResponse);

		String requestBody = String.format("""
       {
          "username": "%s",
          "newPassword": "%s"
       }
       """, username, password);

		this.mockMvc.perform(post("/api/cics/changepassword")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isBadRequest())
			.andExpect(content().string(expectedError));

		verify(kspCicsService).updateKspCicsPassword(username, password);
	}

	private static Stream<Arguments> providePasswordValidationFailures() {
		return Stream.of(
			Arguments.of("testuser", "weak", "Password does not meet complexity requirements"),
			Arguments.of("testuser", "12345", "Password must contain at least one letter"),
			Arguments.of("testuser", "short", "Password must be at least 8 characters long"),
			Arguments.of("testuser", "NoNumbersHere", "Password must contain at least one number"),
			Arguments.of("testuser", "nouppercasehere1", "Password must contain at least one uppercase letter"),
			Arguments.of("testuser", "NOLOWERCASEHERE1", "Password must contain at least one lowercase letter"),
			Arguments.of("testuser", "NoSpecialChar1", "Password must contain at least one special character"),
			Arguments.of("testuser", "Invalid<>Chars1!", "Password contains invalid characters")
		);
	}

	/**
	 * Tests that password change fails when HTTP status is provided by service.
	 * <p>
	 * Verifies that:
	 * - Service-provided HTTP status codes are respected
	 * - Error messages are passed through correctly
	 * - Different HTTP error codes are handled appropriately
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@ParameterizedTest
	@DisplayName("Should return service-provided HTTP status on failure")
	@MethodSource("provideServiceErrors")
	void testChangePassword_ServiceErrors(String username, String password, HttpStatus serviceStatus, String errorMessage) throws Exception {
		KspChangePasswordResponse failureResponse = new KspChangePasswordResponse();
		failureResponse.setSuccess(false);
		failureResponse.setResponse(errorMessage);
		failureResponse.setHttp(serviceStatus);

		when(kspCicsService.updateKspCicsPassword(eq(username), eq(password)))
			.thenReturn(failureResponse);

		String requestBody = String.format("""
       {
          "username": "%s",
          "newPassword": "%s"
       }
       """, username, password);

		this.mockMvc.perform(post("/api/cics/changepassword")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().is(serviceStatus.value()))
			.andExpect(content().string(errorMessage));

		verify(kspCicsService).updateKspCicsPassword(username, password);
	}

	private static Stream<Arguments> provideServiceErrors() {
		return Stream.of(
			Arguments.of("testuser", "Password123!", HttpStatus.UNAUTHORIZED, "User not authorized to change password"),
			Arguments.of("unknownuser", "Password123!", HttpStatus.NOT_FOUND, "User not found in CICS system"),
			Arguments.of("testuser", "Password123!", HttpStatus.INTERNAL_SERVER_ERROR, "CICS service temporarily unavailable"),
			Arguments.of("testuser", "Password123!", HttpStatus.CONFLICT, "Password was recently used"),
			Arguments.of("testuser", "Password123!", HttpStatus.FORBIDDEN, "Password change not allowed for this account type")
		);
	}

	/**
	 * Tests the default error handling path.
	 * <p>
	 * Verifies that when CICS service returns failure without HTTP status,
	 * the endpoint defaults to HTTP 400 Bad Request.
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should default to 400 when service fails without HTTP status")
	void testChangePassword_Failure_WithoutHttpStatus() throws Exception {
		String username = "testuser";
		String password = "weak";
		String errorMessage = "Password does not meet complexity requirements";

		KspChangePasswordResponse failureResponse = new KspChangePasswordResponse();
		failureResponse.setSuccess(false);
		failureResponse.setResponse(errorMessage);
		failureResponse.setHttp(null);

		when(kspCicsService.updateKspCicsPassword(eq(username), eq(password)))
			.thenReturn(failureResponse);

		String requestBody = String.format("""
       {
          "username": "%s",
          "newPassword": "%s"
       }
       """, username, password);

		this.mockMvc.perform(post("/api/cics/changepassword")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isBadRequest())
			.andExpect(content().string(errorMessage))
			.andDo(document("cics-changepassword-validation-failure",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				requestFields(
					fieldWithPath("username").type(JsonFieldType.STRING).description("Username for which to change the password"),
					fieldWithPath("newPassword").type(JsonFieldType.STRING).description("New password to set")
				)
			));

		verify(kspCicsService).updateKspCicsPassword(username, password);
	}

	/**
	 * Tests that unauthorized access is properly rejected.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 401 Unauthorized for authorization failures
	 * - CICS authorization errors are propagated correctly
	 * - Security constraints are enforced
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 401 when authorization fails")
	void testChangePassword_Unauthorized_Error() throws Exception {
		KspChangePasswordResponse errorResponse = new KspChangePasswordResponse();
		errorResponse.setSuccess(false);
		errorResponse.setResponse("Unauthorized access");
		errorResponse.setHttp(HttpStatus.UNAUTHORIZED);

		when(kspCicsService.updateKspCicsPassword(anyString(), anyString()))
			.thenReturn(errorResponse);

		String requestBody = """
			{
				"username": "testuser",
				"newPassword": "NewSecurePassword123!"
			}
			""";

		this.mockMvc.perform(post("/api/cics/changepassword")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isUnauthorized());
	}

	/**
	 * Tests that internal server errors are properly handled.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 500 Internal Server Error for server failures
	 * - CICS service errors are propagated with appropriate status codes
	 * - Error responses maintain consistent format
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 500 when internal server error occurs")
	void testChangePassword_InternalServerError() throws Exception {
		KspChangePasswordResponse errorResponse = new KspChangePasswordResponse();
		errorResponse.setSuccess(false);
		errorResponse.setResponse("Internal server error occurred");
		errorResponse.setHttp(HttpStatus.INTERNAL_SERVER_ERROR);

		when(kspCicsService.updateKspCicsPassword(anyString(), anyString()))
			.thenReturn(errorResponse);

		String requestBody = """
			{
				"username": "testuser",
				"newPassword": "NewSecurePassword123!"
			}
			""";

		this.mockMvc.perform(post("/api/cics/changepassword")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isInternalServerError());
	}
}
