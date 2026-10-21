package dk.digitalidentity.rc.log;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceAuditInterceptorTest {

	@Mock
	private AuditLogger auditLogger;

	@InjectMocks
	private UserServiceAuditInterceptor interceptor;

	@Mock
	private JoinPoint joinPoint;

	@Mock
	private Signature signature;

	private void stubMethodName(String name) {
		given(joinPoint.getSignature()).willReturn(signature);
		given(signature.getName()).willReturn(name);
	}

	@Test
	void auditLogsAddRoleGroupSevenArgOverload() {
		stubMethodName("addRoleGroup");
		User user = new User();
		RoleGroup roleGroup = new RoleGroup();
		given(joinPoint.getArgs()).willReturn(new Object[] { user, roleGroup, null, null, null, "case-1", "fordi jeg sagde det" });

		interceptor.interceptAfter(joinPoint);

		verify(auditLogger).log(user, EventType.ASSIGN_ROLE_GROUP, roleGroup);
	}
}
