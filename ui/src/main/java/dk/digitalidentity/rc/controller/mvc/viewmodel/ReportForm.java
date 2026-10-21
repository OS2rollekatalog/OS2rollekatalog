package dk.digitalidentity.rc.controller.mvc.viewmodel;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportForm {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private String name;
    private String date;
    private List<Long> itSystems;
    private List<String> orgUnits;
    private String manager;
    private String managerFilter;
    private String[] unitFilter;
    private long[] itsystemFilter;

    private boolean showUsers;
    private boolean showOUs;
    private boolean showUserRoles;
    private boolean showNegativeRoles;
    private boolean showItSystems;
    private boolean showInactiveUsers;
    private boolean showSystemRoles;
    private boolean showOrgUnitAllocations;

    /**
     * Per content-type column selection (key = content-type identifier such as "ShowUsers", value =
     * selected ReportColumn keys). Null, a missing key, or an empty list for a type all mean
     * "not specified - use the full column set" (backward-compat default).
     */
    private Map<String, List<String>> selectedColumns;

    // Lets the classic HTML form submit column selections as one JSON string field.
    @JsonIgnore
    public void setSelectedColumnsJson(String json) {
        if (json == null || json.isBlank()) {
            this.selectedColumns = null;
            return;
        }

        try {
            this.selectedColumns = OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, List<String>>>() {});
        }
        catch (JsonProcessingException e) {
            this.selectedColumns = null;
        }
    }

    // The reverse of setSelectedColumnsJson - fills in the hidden field when the form already
    // has a selection, e.g. loaded from a saved template.
    @JsonIgnore
    public String getSelectedColumnsJson() {
        if (selectedColumns == null || selectedColumns.isEmpty()) {
            return null;
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(selectedColumns);
        }
        catch (JsonProcessingException e) {
            return null;
        }
    }
}
