package dk.digitalidentity.rc.log;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class SlowExecutionAspect {

    @Around("@annotation(warnIfSlowerThan)")
    public Object measure(ProceedingJoinPoint pjp, WarnIfSlowerThan warnIfSlowerThan) throws Throwable {
        long startNanos = System.nanoTime();
        boolean failed = false;
        
        try {
            return pjp.proceed();
        }
        catch (Throwable t) {
            failed = true;
            throw t;
        }
        finally {
            long tookMillis = (System.nanoTime() - startNanos) / 1_000_000L;
            long threshold = warnIfSlowerThan.millis();

            if (tookMillis > threshold) {
                MethodSignature signature = (MethodSignature) pjp.getSignature();

                log.error("{}.{} took {} ms{} (threshold {} ms)",
                        signature.getDeclaringType().getSimpleName(),
                        signature.getName(),
                        tookMillis,
                        failed ? " and failed" : "",
                        threshold);
            }
            else if (log.isDebugEnabled()) {
            	MethodSignature signature = (MethodSignature) pjp.getSignature();

            	log.debug("{}.{} took {} ms",
                        signature.getDeclaringType().getSimpleName(),
                        signature.getName(),
                        tookMillis);
            }
        }
    }
}
