package dk.digitalidentity.rc.dao.history.model;

import java.time.LocalDate;

public interface GenericRoleAssignment {
    public long getRoleId();
    public LocalDate getStartDate();
    public LocalDate getStopDate();
}
