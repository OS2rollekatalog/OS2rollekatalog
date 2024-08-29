package dk.digitalidentity.rc.log;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

@Slf4j
@Aspect
@Component
public class AuditLogArgumentsInterceptor {

    @Before(value = "execution(* dk.digitalidentity.rc.controller.mvc..*(..)) || execution(* dk.digitalidentity.rc.controller.rest..*(..))")
    public void interceptMvcControllers(JoinPoint jp) {
        enrichAuditlog(jp);
    }

    private static void enrichAuditlog(JoinPoint jp) {
        final Object[] args = jp.getArgs();
        MethodSignature ms = (MethodSignature) jp.getSignature();
        Method m = ms.getMethod();

        Annotation[][] parameterAnnotations = m.getParameterAnnotations();
        for (int i = 0; i < parameterAnnotations.length; i++) {
            Annotation[] annotations = parameterAnnotations[i];
            for (Annotation annotation : annotations) {
                if (annotation.annotationType() == AuditLogArgument.class) {
                    final Object currentArgument = args[i];
                    final AuditLogArgument argumentAnnotation = (AuditLogArgument) annotation;
                    final String name = StringUtils.isNotEmpty(argumentAnnotation.name()) ? argumentAnnotation.name() : ms.getName();
                    AuditLogContextHolder.getContext().addArgument(name, objectToString(currentArgument, ((AuditLogArgument) annotation).defaultValue()));
                }
            }
        }
    }

    private static String objectToString(final Object object, final String defaultValue) {
        return switch (object) {
            case null -> defaultValue;
            case String s -> s;
            case Boolean b -> b ? "ja" : "nej";
            case Integer i -> i.toString();
            case Long l -> l.toString();
            case Enum<?> anEnum -> anEnum.name();
            default -> "?";
        };
    }

}
