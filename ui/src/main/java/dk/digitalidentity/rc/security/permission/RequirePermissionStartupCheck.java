package dk.digitalidentity.rc.security.permission;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import dk.digitalidentity.rc.security.RequireRoleAnnotation;

@Component
public class RequirePermissionStartupCheck implements SmartInitializingSingleton {
	private final RequestMappingHandlerMapping handlerMapping;

	public RequirePermissionStartupCheck(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
		this.handlerMapping = handlerMapping;
	}

	@Override
	public void afterSingletonsInstantiated() {
		List<String> violations = new ArrayList<String>();

		handlerMapping.getHandlerMethods().forEach((mappingInfo, handlerMethod) -> {
			if (publicEndpoint(mappingInfo)) {
				return;
			}

			boolean onMethod = AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), RequirePermission.class) || hasTransativeAnnotation(handlerMethod.getMethod(), RequireRoleAnnotation.class);
			boolean onClass = AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), RequireControllerPermission.class) || hasTransativeAnnotation(handlerMethod.getBeanType(), RequireRoleAnnotation.class);

			if (!onMethod && !onClass) {
				violations.add(handlerMethod.getBeanType().getSimpleName() + "#" + handlerMethod.getMethod().getName() + " (" + mappingInfo + ")");
			}
		});

		if (!violations.isEmpty()) {
			throw new IllegalStateException("The following endpoints lack a permission annotation "
					+ "(@RequireControllerPermission on the class or @RequirePermission on the method):\n  "
					+ String.join("\n  ", violations));
		}
	}

	private boolean hasTransativeAnnotation(AnnotatedElement element, Class<? extends Annotation> annotationType) {
		for (Annotation annotation : element.getAnnotations()) {
			boolean found = AnnotatedElementUtils.hasAnnotation(annotation.getClass(), annotationType);
			if (found) {
				return true;
			}
		}

		return false;
	}

	private boolean publicEndpoint(RequestMappingInfo mappingInfo) {
		Set<String> patterns = mappingInfo.getPatternValues();

        return !patterns.isEmpty() && patterns.stream().allMatch(p ->
			p.equals("/") || p.equals("/error") || p.equals("/info") || p.equals("/debug") || p.equals("/swagger-ui.html") ||
			p.startsWith("/api/") ||
			p.startsWith("/saml/") ||
			p.startsWith("/v3/") || p.startsWith("/swagger-ui/")
        );
	}
}
