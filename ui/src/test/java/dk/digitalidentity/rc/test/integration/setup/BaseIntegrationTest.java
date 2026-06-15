package dk.digitalidentity.rc.test.integration.setup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.TestContainersConfiguration;
import dk.digitalidentity.rc.config.TestInterceptorConfiguration;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.PermissionConstraint;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.service.permission.PermissionService;
import dk.digitalidentity.saml.service.model.SamlGrantedAuthority;
import dk.digitalidentity.saml.service.model.TokenUser;
import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
@Import({TestContainersConfiguration.class, TestInterceptorConfiguration.class})
public abstract class BaseIntegrationTest {

    @Autowired
    protected EntityManager entityManager;

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	private PermissionService permissionService;

	public static final String ATTRIBUTE_USERID = "ATTRIBUTE_USERID";
	public static final String ATTRIBUTE_NAME = "ATTRIBUTE_NAME";
	public static final String ATTRIBUTE_SUBSTITUTE_FOR = "ATTRIBUTE_SUBSTITUTE_FOR";
	public static final String ATTRIBUTE_CLIENT = "ATTRIBUTE_CLIENT";
	public static final String ATTRIBUTE_USER_UUID = "ATTRIBUTE_USER_UUID";

    /**
     * Flush pending changes and clear persistence context.
     * Use this to simulate detached entities and test lazy loading.
     */
    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

	protected void grantCRUDAccess(String uuid, Section... sections) {
		for (Section section : sections) {
			permissionService.updateConstraintFor(section ,Permission.READ, uuid, new PermissionConstraint(null, null));
			permissionService.updateConstraintFor( section ,Permission.CREATE, uuid, new PermissionConstraint(null, null));
			permissionService.updateConstraintFor( section ,Permission.UPDATE, uuid, new PermissionConstraint(null, null));
			permissionService.updateConstraintFor( section ,Permission.DELETE, uuid, new PermissionConstraint(null, null));
		}
	}

	protected void grantAssigningAccess(String uuid, Section... sections) {
		for (Section section : sections) {
			permissionService.updateConstraintFor(section ,Permission.ASSIGN, uuid, new PermissionConstraint(null, null));
		}
	}

	protected void grantPermission(String userUuid, Section section, Permission permission) {
		permissionService.updateConstraintFor(section, permission, userUuid, new PermissionConstraint(null, null));
	}

	protected static RequestPostProcessor mockLogin(String userUUID, String userId, String userName,
													List<String> roles, List<String> substituteFor) {
		SecurityContext context = SecurityContextHolder.createEmptyContext();

		TokenUser principal = TokenUser.builder()
			.cvr("12345678")
			.username(userId)
			.authorities(roles.stream().map(SamlGrantedAuthority::new).toList())
			.attributes(new HashMap<>())
			.build();

		principal.getAttributes().put(ATTRIBUTE_USERID, userId);
		principal.getAttributes().put(ATTRIBUTE_USER_UUID, userUUID);
		principal.getAttributes().put(ATTRIBUTE_NAME, userName);
		principal.getAttributes().put(ATTRIBUTE_SUBSTITUTE_FOR, new ArrayList<>(substituteFor));

		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
			principal, "", principal.getAuthorities()
		);
		auth.setDetails(principal);
		context.setAuthentication(auth);
		SecurityContextHolder.setContext(context);

		return SecurityMockMvcRequestPostProcessors.securityContext(context);
	}

	protected static RequestPostProcessor mockLogin(String userUUID, String userId, List<String> roles) {
		return mockLogin(userUUID, userId, "Test user", roles, List.of());
	}

	protected static RequestPostProcessor mockLogin(User user, List<String> roles) {
		return mockLogin(user.getUuid(), user.getUserId(), user.getName(), roles, List.of());
	}
}
