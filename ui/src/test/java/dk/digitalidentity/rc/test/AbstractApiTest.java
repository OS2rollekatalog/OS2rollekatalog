package dk.digitalidentity.rc.test;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import dk.digitalidentity.rc.TestContainersConfiguration;
import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.config.TestInterceptorConfiguration;
import dk.digitalidentity.rc.security.RolePostProcessor;
import dk.digitalidentity.rc.util.BootstrapDevMode;
import dk.digitalidentity.saml.service.model.SamlGrantedAuthority;
import dk.digitalidentity.saml.service.model.TokenUser;
import jakarta.persistence.EntityManager;

/**
 * Abstract base class for API integration tests.
 * Provides common setup for security context, MockMvc, and test containers.
 */
@ExtendWith({SpringExtension.class, RestDocumentationExtension.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:test.properties")
@ActiveProfiles({"test"})
@Transactional(rollbackFor = Exception.class)
@Import({TestContainersConfiguration.class, TestInterceptorConfiguration.class})
public abstract class AbstractApiTest {

	protected static final String API_KEY = "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb";

	protected MockMvc mockMvc;

	@Autowired
	protected BootstrapDevMode bootstrapper;

	@Autowired
	protected WebApplicationContext context;

	@Autowired
	protected EntityManager entityManager;

	/**
	 * Override this method to provide the required API roles for the test.
	 * Return the role strings to be added after "ROLE_API_" prefix.
	 * Example: return List.of(AccessRole.READ_ACCESS.toString(), AccessRole.ROLE_MANAGEMENT.toString());
	 * For non-AccessRole roles, return the full role name (e.g., "AD_SYNC_SERVICE").
	 */
	protected abstract List<String> getRequiredApiRoles();

	@BeforeEach
	public void setUp(final RestDocumentationContextProvider restDocumentation) throws Exception {
		bootstrapper.init(false);

		List<SamlGrantedAuthority> authorities = new ArrayList<>();
		authorities.add(new SamlGrantedAuthority(Constants.ROLE_SYSTEM));

		// Add API roles based on subclass requirements
		for (String role : getRequiredApiRoles()) {
			authorities.add(new SamlGrantedAuthority("ROLE_API_" + role));
		}

		Map<String, Object> attributes = new HashMap<>();
		attributes.put(RolePostProcessor.ATTRIBUTE_NAME, "system");
		attributes.put(RolePostProcessor.ATTRIBUTE_USERID, "system");
		attributes.put(RolePostProcessor.ATTRIBUTE_CLIENT, "system-uuid");

		TokenUser tokenUser = TokenUser.builder()
			.cvr("N/A")
			.attributes(attributes)
			.authorities(authorities)
			.username("System")
			.build();

		UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken("System", "N/A", tokenUser.getAuthorities());
		token.setDetails(tokenUser);
		SecurityContextHolder.getContext().setAuthentication(token);

		entityManager.flush();
		entityManager.clear();

		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
			.apply(documentationConfiguration(restDocumentation)
				.uris()
				.withHost("www.rollekatalog.dk")
				.withPort(443)
				.withScheme("https")
			)
			.build();
	}
}
