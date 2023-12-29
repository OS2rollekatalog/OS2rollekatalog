package dk.digitalidentity.rc.log;


import lombok.Data;

@Data
public class AuditLogContext {
    // UserId of the user that set stop date
    private String stopDateUserId;
}
