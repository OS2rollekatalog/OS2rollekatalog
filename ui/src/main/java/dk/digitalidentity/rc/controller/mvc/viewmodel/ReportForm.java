package dk.digitalidentity.rc.controller.mvc.viewmodel;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportForm {
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
    private boolean showKLE;
    private boolean showItSystems;
    private boolean showInactiveUsers;
    private boolean showSystemRoles;
}
