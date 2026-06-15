package dk.digitalidentity.rc.security.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.service.permission.PermissionService;
import dk.digitalidentity.saml.service.model.SamlGrantedAuthority;
import dk.digitalidentity.saml.service.model.TokenUser;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserPermissionContext.canManageSubstitutes")
class UserPermissionContextTest {

    @Mock
    private PermissionService permissionService;

    private UserPermissionContext context;

    @BeforeEach
    void setUp() {
        context = new UserPermissionContext(permissionService);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("returns true")
    class ReturnsTrue {

        @Test
        @DisplayName("for administrator")
        void admin() {
            authenticateWith(Constants.ROLE_ADMINISTRATOR);
            initWithManagerUpdateConstraint(null);

            assertThat(context.canManageSubstitutes()).isTrue();
        }

        @Test
        @DisplayName("for actual manager (constrained access)")
        void manager() {
            authenticateWith(Constants.ROLE_MANAGER);
            initWithManagerUpdateConstraint(new PermissionConstraint(null, java.util.Set.of("ou-a")));

            assertThat(context.canManageSubstitutes()).isTrue();
        }

        @Test
        @DisplayName("for substitute who also has ROLE_MANAGER_UPDATE_ID system role (unconstrained UPDATE)")
        void substituteWithManagerUpdateSystemRole() {
            authenticateWith(Constants.ROLE_SUBSTITUTE);
            initWithManagerUpdateConstraint(new PermissionConstraint(null, null));

            assertThat(context.canManageSubstitutes())
                    .as("a substitute who is also assigned the manager-update system role must keep the privilege")
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("returns false")
    class ReturnsFalse {

        @Test
        @DisplayName("for pure substitute (constrained UPDATE only)")
        void pureSubstitute() {
            authenticateWith(Constants.ROLE_SUBSTITUTE);
            initWithManagerUpdateConstraint(new PermissionConstraint(null, java.util.Set.of("ou-a")));

            assertThat(context.canManageSubstitutes()).isFalse();
        }

        @Test
        @DisplayName("for user with no MANAGER UPDATE permission at all")
        void noManagerPermissions() {
            authenticateWith();
            when(permissionService.getPermissionMap(anyString())).thenReturn(new EnumMap<>(Section.class));
            context.init();

            assertThat(context.canManageSubstitutes()).isFalse();
        }
    }

    private void initWithManagerUpdateConstraint(PermissionConstraint constraint) {
        Map<Section, Map<Permission, PermissionConstraint>> map = new EnumMap<>(Section.class);
        Map<Permission, PermissionConstraint> managerPermissions = new EnumMap<>(Permission.class);
        managerPermissions.put(Permission.UPDATE, constraint);
        map.put(Section.MANAGER, managerPermissions);

        when(permissionService.getPermissionMap(anyString())).thenReturn(map);
        context.init();
    }

    private static void authenticateWith(String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(SamlGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("ATTRIBUTE_USER_UUID", "test-user-uuid");
        TokenUser tokenUser = TokenUser.builder()
                .username("test-user")
                .attributes(attributes)
                .build();

        TestingAuthenticationToken auth = new TestingAuthenticationToken("test-user", null, authorities);
        auth.setDetails(tokenUser);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
