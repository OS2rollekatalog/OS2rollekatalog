package dk.digitalidentity.rc.controller.api.v2;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

import org.jsoup.internal.StringUtil;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import dk.digitalidentity.rc.controller.api.model.ExceptionResponseAM;
import dk.digitalidentity.rc.controller.api.model.ReportAM;
import dk.digitalidentity.rc.controller.mvc.xlsview.ReportXlsxView;
import dk.digitalidentity.rc.security.RequireApiReadAccessRole;
import dk.digitalidentity.rc.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequireApiReadAccessRole
@SecurityRequirement(name = "ApiKey")
@RequiredArgsConstructor
@Tag(name = "Report API V2")
public class ReportApiV2 {

	private final ReportService reportService;

	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Generates report xlsx file."),
			@ApiResponse(responseCode = "400", description = "Bad request", content =
					{ @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) }),
			@ApiResponse(responseCode = "500", description = "Internal Server Error", content =
					{ @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) })
	})
	@Operation(summary = "Generate report", description = "Generates report xlsx file.")
	@PostMapping("/api/v2/report")
	public void generateReport(@RequestBody ReportAM reportForm, Locale loc, HttpServletRequest request, HttpServletResponse response) throws Exception {
		if (StringUtil.isBlank(reportForm.getDate())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date cannot be empty");
		}

		reportForm.setItSystems(reportForm.getItsystemFilter() != null ? Arrays.stream(reportForm.getItsystemFilter()).boxed().toList() : null);
		reportForm.setOrgUnits(reportForm.getUnitFilter() != null ? Arrays.asList(reportForm.getUnitFilter()) : null);
		reportForm.setManager(reportForm.getManagerFilter());

		Map<String, Object> model = reportService.getReportModel(reportForm, loc);

		response.setContentType("application/ms-excel");
		response.setHeader("Content-Disposition", "attachment; filename=\"Rapport.xlsx\"");

		new ReportXlsxView().render(model, request, response);
	}
}
