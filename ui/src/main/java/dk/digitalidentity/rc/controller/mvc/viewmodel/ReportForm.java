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

    private boolean showUsers;
    private boolean showTitles;
    private boolean showOUs;
    private boolean showUserRoles;
    private boolean showKLE;
    private boolean showItSystems;
    private boolean showInactiveUsers;
}
