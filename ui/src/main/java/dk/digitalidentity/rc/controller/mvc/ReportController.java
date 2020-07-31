package dk.digitalidentity.rc.controller.mvc;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.rc.controller.mvc.viewmodel.OUListForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.ReportForm;
import dk.digitalidentity.rc.controller.mvc.xlsview.ReportXlsView;
import dk.digitalidentity.rc.dao.history.model.HistoryItSystem;
import dk.digitalidentity.rc.dao.model.ReportTemplate;
import dk.digitalidentity.rc.security.RequireReadAccessRole;
import dk.digitalidentity.rc.service.HistoryService;
import dk.digitalidentity.rc.service.ReportService;
import dk.digitalidentity.rc.service.ReportTemplateService;

@RequireReadAccessRole
@Controller
public class ReportController {

	@Autowired
	private ReportService reportService;

	@Autowired
	private HistoryService historyService;

	@Autowired
	private ReportTemplateService reportTemplateService;

	@GetMapping("/ui/report/templates")
	public String templatesReport(Model model) {
		model.addAttribute("reportTemplates", reportTemplateService.getAll());

		return "reports/templates/list";
	}

	@GetMapping("/ui/report/configure")
	public String configureReport(Model model) {
		LocalDate now = LocalDate.now();

		// default column choices
		ReportForm reportForm = new ReportForm();
		reportForm.setShowUsers(true);
		reportForm.setShowTitles(false);
		reportForm.setShowOUs(false);
		reportForm.setShowUserRoles(true);
		reportForm.setShowKLE(true);
		reportForm.setShowItSystems(true);
		reportForm.setShowInactiveUsers(false);

		model.addAttribute("reportForm", reportForm);
		model.addAttribute("allItSystems", parseItSystems(now));
		model.addAttribute("allOrgUnits", parseOuTree(now));
		model.addAttribute("allManagers", historyService.getManagers(now));

		return "reports/configure";
	}

	@GetMapping("/ui/report/configure/template/{id}")
	public String getFilterOptions(Model model, @PathVariable("id") Long id) {
		LocalDate now = LocalDate.now();
		ReportTemplate reportTemplate = reportTemplateService.getById(id);

		ReportForm reportForm = new ReportForm();
		reportForm.setShowUsers(reportTemplate.isShowUsers());
		reportForm.setShowTitles(reportTemplate.isShowTitles());
		reportForm.setShowOUs(reportTemplate.isShowOUs());
		reportForm.setShowUserRoles(reportTemplate.isShowUserRoles());
		reportForm.setShowKLE(reportTemplate.isShowKLE());
		reportForm.setShowItSystems(reportTemplate.isShowItSystems());
		reportForm.setShowInactiveUsers(reportTemplate.isShowInactiveUsers());

		model.addAttribute("reportForm", reportForm);
		model.addAttribute("allItSystems", parseItSystems(now));
		model.addAttribute("allOrgUnits", parseOuTree(now));
		model.addAttribute("allManagers", historyService.getManagers(now));

		return "reports/configure";
	}

	@GetMapping("/ui/report/configure/{date}")
	public String getFilterOptions(Model model, @PathVariable("date") String dateStr) {
		LocalDate date = null;
		try {
			date = LocalDate.parse(dateStr);
		}
		catch (DateTimeException e) {
			return "reports/fragments/filterOptionsFragment :: filterOptions";
		}

		model.addAttribute("reportForm", new ReportForm());
		model.addAttribute("allItSystems", parseItSystems(date));
		model.addAttribute("allOrgUnits", parseOuTree(date));
		model.addAttribute("allManagers", historyService.getManagers(date));

		return "reports/fragments/filterOptionsFragment :: filterOptions";
	}

	@PostMapping(value = "/ui/report/download")
	public ModelAndView downloadReport(ReportForm reportForm, HttpServletResponse response, Locale loc) {
		Map<String, Object> model = reportService.getReportModel(reportForm, loc);

		response.setContentType("application/ms-excel");
		response.setHeader("Content-Disposition", "attachment; filename=\"Rapport.xls\"");

		return new ModelAndView(new ReportXlsView(), model);
	}
	
	// fragment
	@GetMapping(value = "/ui/report/time")
	public String timeReport() {
		return "reports/time";
	}
	
	private List<HistoryItSystem> parseItSystems(LocalDate localDate) {
		List<HistoryItSystem> itSystems = historyService.getItSystems(localDate);
		itSystems.sort((sys1, sys2) -> sys1.getItSystemName().compareToIgnoreCase(sys2.getItSystemName()));

		return itSystems;
	}

	private List<OUListForm> parseOuTree(LocalDate localDate) {
		return historyService
				.getOUs(localDate)
				.values()
				.stream()
				.map(OUListForm::new)
				.collect(Collectors.toList());
	}
}
