package dk.digitalidentity.rc.log;


import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Data
public class AuditLogContext {

    public static final String FIELD_NAVN = "Navn";
    public static final String FIELD_BESKRIVELSE = "Beskrivelse";
    public static final String FIELD_KUN_BRUGERE = "Kun brugere";
    public static final String FIELD_FOELSOM_ROLLE = "Følsom rolle";
    public static final String FIELD_EKSTRA_FOELSOM_ROLLE = "Ekstra følsom rolle";
    public static final String FIELD_ATTESTATION_AF_ATTESTATIONSANSVARLIG = "Attestation af attestationsansvarlig";
    public static final String FIELD_KRAEVER_LEDERHANDLING = "Kræver lederhandling";
    public static final String FIELD_SEND_TIL_BEMYNDIGELSESANSVARLIGE = "Send til bemyndigelsesansvarlige";
    public static final String FIELD_SEND_TIL_STEDFORTRAEDERE = "Send til stedfortrædere";
    public static final String FIELD_KAN_ANMODE = "Kan anmode";
    public static final String FIELD_KAN_GODKENDE = "Kan godkende";
    public static final String FIELD_ENHEDSFILTER = "Enhedsfilter";

    // UserId of the user that set stop date
    private String stopDateUserId;
    // Arguments that should be saved along with the auditlog
    private Map<String, String> arguments;
    // Before-state for diffing on UPDATE — stored as strings to avoid casts
    private Map<String, String> beforeState;

    public void addArgument(String key, String value) {
        if (arguments == null) {
            arguments = new TreeMap<>();
        }
        arguments.put(key, value);
    }

    public void putBefore(String label, String value) {
        if (beforeState == null) {
            beforeState = new TreeMap<>();
        }
        beforeState.put(label, value != null ? value : "");
    }

    public void putBefore(String label, List<?> values) {
        putBefore(label, values != null
                ? values.stream().map(Object::toString).sorted().collect(Collectors.joining(", "))
                : "");
    }

    public boolean hasBeforeState() {
        return beforeState != null && !beforeState.isEmpty();
    }

    public void diff(String label, String after) {
        if (beforeState == null || !beforeState.containsKey(label)) {
            return;
        }
        String before = beforeState.get(label);
        String safeAfter = after != null ? after : "";
        if (!Objects.equals(before, safeAfter)) {
            addArgument(label, (before.isEmpty() ? "(tom)" : before) + " → " + (safeAfter.isEmpty() ? "(tom)" : safeAfter));
        }
    }

    public void diff(String label, boolean after) {
        diff(label, String.valueOf(after));
    }

    public void diff(String label, List<?> after) {
        String afterStr = after != null
                ? after.stream().map(Object::toString).sorted().collect(Collectors.joining(", "))
                : "";
        if (beforeState == null || !beforeState.containsKey(label)) {
            return;
        }
        String before = beforeState.get(label);
        if (!Objects.equals(before, afterStr)) {
            addArgument(label, (before.isEmpty() ? "(ingen)" : before) + " → " + (afterStr.isEmpty() ? "(ingen)" : afterStr));
        }
    }
}
