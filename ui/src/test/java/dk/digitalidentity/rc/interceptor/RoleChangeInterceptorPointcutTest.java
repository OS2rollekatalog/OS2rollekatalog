package dk.digitalidentity.rc.interceptor;

import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RoleChangeInterceptor} matches on exact method names, so a new assignment method silently
 * fires no hooks at all - no recalculation, no provisioning, no history - and the assignment never
 * reaches the users. Both services and expectations are derived from the pointcuts themselves, so
 * adding a method to a hooked service is enough to be checked here.
 * <p>
 * Only the execution() clauses are evaluated; the args() bindings need advice parameter names, which
 * reflection cannot resolve reliably.
 */
class RoleChangeInterceptorPointcutTest {
	private static final String EXECUTION = "execution(";
	private static final Pattern DECLARING_SERVICE = Pattern.compile("execution\\(\\S+ (dk\\.digitalidentity\\.rc\\.service\\.\\w+)\\.");

	/** A method taking one of these mutates who has which role, and must therefore reach the hooks. */
	private static final Set<Class<?>> ROLE_ARGUMENTS = Set.of(
		UserRole.class, RoleGroup.class, SystemRoleAssignment.class,
		OrgUnitUserRoleAssignment.class, OrgUnitRoleGroupAssignment.class,
		UserUserRoleAssignment.class, UserRoleGroupAssignment.class);

	private static final Pattern MUTATING_NAME = Pattern.compile("^(add|update|edit|remove|delete).*");

	/** Overloads that forward through {@code self}; they must stay void, or @AfterReturning fires on both calls. */
	private static final Set<String> FORWARDS_TO_AN_INTERCEPTED_METHOD = Set.of(
		"OrgUnitService.addUserRole(OrgUnit, UserRole, boolean, LocalDate, LocalDate, Set, Set)",
		"OrgUnitService.addRoleGroup(OrgUnit, RoleGroup, boolean, LocalDate, LocalDate, Set, Set)");

	private static final List<Pointcut> POINTCUTS = pointcuts();

	@Test
	@DisplayName("every role assignment method on a hooked service is matched by a pointcut")
	void everyAssignmentMethodIsIntercepted() {
		List<String> unmatched = hookedServices().stream()
			.flatMap(service -> Arrays.stream(service.getDeclaredMethods())
				.filter(RoleChangeInterceptorPointcutTest::isRoleAssignmentMethod)
				.filter(method -> POINTCUTS.stream().noneMatch(pointcut -> pointcut.matches(method, service)))
				.map(RoleChangeInterceptorPointcutTest::describe)
				.filter(described -> !FORWARDS_TO_AN_INTERCEPTED_METHOD.contains(described)))
			.sorted()
			.toList();

		assertThat(unmatched)
			.as("methods that change role assignments without firing any RoleChangeHook")
			.isEmpty();
	}

	@Test
	@DisplayName("every pointcut matches a method on the service it names")
	void everyPointcutMatchesSomething() {
		List<String> dead = POINTCUTS.stream()
			.filter(pointcut -> Arrays.stream(pointcut.declaringService().getDeclaredMethods())
				.noneMatch(method -> pointcut.matches(method, pointcut.declaringService())))
			.map(Pointcut::expression)
			.sorted()
			.toList();

		assertThat(dead)
			.as("pointcuts that no longer match anything - the intercepted method was renamed or removed")
			.isEmpty();
	}

	private static boolean isRoleAssignmentMethod(Method method) {
		return Modifier.isPublic(method.getModifiers())
			&& !method.isSynthetic()
			&& MUTATING_NAME.matcher(method.getName()).matches()
			&& Arrays.stream(method.getParameterTypes()).anyMatch(ROLE_ARGUMENTS::contains);
	}

	private static Set<Class<?>> hookedServices() {
		return POINTCUTS.stream()
			.map(Pointcut::declaringService)
			.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private static List<Pointcut> pointcuts() {
		return Arrays.stream(RoleChangeInterceptor.class.getDeclaredMethods())
			.flatMap(adviceMethod -> adviceExpressions(adviceMethod).stream())
			.toList();
	}

	private static List<Pointcut> adviceExpressions(Method adviceMethod) {
		List<Pointcut> pointcuts = new ArrayList<>();

		Before before = adviceMethod.getAnnotation(Before.class);
		if (before != null) {
			executionClauses(before.value()).forEach(clause -> pointcuts.add(Pointcut.of(clause)));
		}

		AfterReturning afterReturning = adviceMethod.getAnnotation(AfterReturning.class);
		if (afterReturning != null) {
			String expression = afterReturning.pointcut().isEmpty() ? afterReturning.value() : afterReturning.pointcut();
			Class<?> returning = boundReturnType(adviceMethod, afterReturning);
			executionClauses(expression).forEach(clause -> pointcuts.add(Pointcut.of(clause, returning)));
		}

		Around around = adviceMethod.getAnnotation(Around.class);
		if (around != null) {
			executionClauses(around.value()).forEach(clause -> pointcuts.add(Pointcut.of(clause)));
		}

		return pointcuts;
	}

	/** The declared type of the advice parameter that receives the return value, if the advice binds one. */
	private static Class<?> boundReturnType(Method adviceMethod, AfterReturning afterReturning) {
		if (afterReturning.returning().isEmpty()) {
			return null;
		}

		// the parameter names are not in the class file, so the single non-bound parameter is the one
		return Arrays.stream(adviceMethod.getParameterTypes())
			.filter(type -> !JoinPoint.class.isAssignableFrom(type))
			.findFirst()
			.orElse(null);
	}

	/** Picks the execution(...) clauses out of an expression that may also contain args(...) and ||. */
	private static List<String> executionClauses(String expression) {
		List<String> clauses = new ArrayList<>();

		for (int start = expression.indexOf(EXECUTION); start >= 0; start = expression.indexOf(EXECUTION, start + EXECUTION.length())) {
			int depth = 0;
			for (int i = start + EXECUTION.length() - 1; i < expression.length(); i++) {
				char c = expression.charAt(i);
				if (c == '(') {
					depth++;
				}
				else if (c == ')' && --depth == 0) {
					clauses.add(expression.substring(start, i + 1));
					break;
				}
			}
		}

		return clauses;
	}

	private static String describe(Method method) {
		return method.getDeclaringClass().getSimpleName() + "." + method.getName() + "("
			+ Arrays.stream(method.getParameterTypes()).map(Class::getSimpleName).collect(Collectors.joining(", ")) + ")";
	}

	private record Pointcut(String expression, Class<?> declaringService, AspectJExpressionPointcut compiled, Class<?> returning) {

		static Pointcut of(String expression) {
			return of(expression, null);
		}

		static Pointcut of(String expression, Class<?> returning) {
			Matcher matcher = DECLARING_SERVICE.matcher(expression);
			if (!matcher.find()) {
				throw new IllegalStateException("Cannot read the service class out of pointcut: " + expression);
			}

			AspectJExpressionPointcut compiled = new AspectJExpressionPointcut();
			compiled.setExpression(expression);

			try {
				return new Pointcut(expression, Class.forName(matcher.group(1)), compiled, returning);
			}
			catch (ClassNotFoundException ex) {
				throw new IllegalStateException("Pointcut names an unknown service: " + expression, ex);
			}
		}

		boolean matches(Method method, Class<?> targetClass) {
			if (!compiled.matches(method, targetClass)) {
				return false;
			}
			// an @AfterReturning advice bound to a type never runs for a method that cannot return it
			return returning == null || returning.isAssignableFrom(method.getReturnType());
		}
	}
}
