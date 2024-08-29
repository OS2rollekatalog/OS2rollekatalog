package dk.digitalidentity.rc.log;


import lombok.Data;

import java.util.Map;
import java.util.TreeMap;

@Data
public class AuditLogContext {
    // UserId of the user that set stop date
    private String stopDateUserId;
    // Arguments that should be saved along with the auditlog
    private Map<String, String> arguments;

    public void addArgument(String key, String value) {
        if (arguments == null) {
            arguments = new TreeMap<>();
        }
        arguments.put(key, value);
    }
}
