package dk.digitalidentity.rc.controller.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import dk.digitalidentity.rc.controller.mvc.viewmodel.ReportForm;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "Report")
@JsonIgnoreProperties({"name", "itSystems", "orgUnits", "manager"})
public class ReportAM extends ReportForm {
	@Schema(description = "Date of the report to be generated (YYYY-MM-DD)")
	@NotEmpty
	private String date;
	@Schema(description = "Manager UUID to filter by")
	private String managerFilter;
	@Schema(description = "Array of orgUnit UUIDs(String) to filter by")
	private String[] unitFilter;
	@Schema(description = "Array of itSystem IDs(long) to filter by")
	private long[] itsystemFilter;

	@Schema(description = "Show Users column")
	@Builder.Default
	private boolean showUsers = true;
	@Schema(description = "Show OrgUnits column")
	@Builder.Default
	private boolean showOUs = false;
	@Schema(description = "Show UserRoles column")
	@Builder.Default
	private boolean showUserRoles = true;
	@Schema(description = "Show Negative Roles column")
	@Builder.Default
	private boolean showNegativeRoles = false;
	@Schema(description = "Show KLE column")
	@Builder.Default
	private boolean showKLE = true;
	@Schema(description = "Show ItSystems column")
	@Builder.Default
	private boolean showItSystems = true;
	@Schema(description = "Show Inactive Users column")
	@Builder.Default
	private boolean showInactiveUsers = false;
}
