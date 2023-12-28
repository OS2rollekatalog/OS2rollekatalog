package dk.digitalidentity.rc.log;

public class AuditLogContextHolder {
    private static final ThreadLocal<AuditLogContext> auditLogContextLocal = new ThreadLocal<>();

    public static void clearContext() {
        auditLogContextLocal.remove();
    }

    public static AuditLogContext getContext() {
        AuditLogContext ctx = auditLogContextLocal.get();
        if (ctx == null) {
            ctx = new AuditLogContext();
            auditLogContextLocal.set(ctx);
        }
        return ctx;
    }

}
