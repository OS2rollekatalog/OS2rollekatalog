package dk.digitalidentity.rc.controller.rest;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.AuditLogViewDao;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.AuditLogView;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.dto.AuditLogDTO;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@RequireAdministratorRole
@RestController
public class AuditLogRestController {

	@Autowired
	private AuditLogViewDao auditLogViewDao;

	@Autowired
	private MessageSource messageSource;

	@PostMapping("/rest/logs/audit")
	public DataTablesOutput<AuditLogDTO> list(@Valid @RequestBody DataTablesInput input, BindingResult bindingResult, Locale locale) {
		if (bindingResult.hasErrors()) {
			DataTablesOutput<AuditLogDTO> error = new DataTablesOutput<>();
			error.setError(bindingResult.toString());

			return error;
		}

		DataTablesOutput<AuditLogView> output = auditLogViewDao.findAll(input);

		return convertAuditLogDataTablesModelToDTO(output, locale);
	}

	private DataTablesOutput<AuditLogDTO> convertAuditLogDataTablesModelToDTO(DataTablesOutput<AuditLogView> output, Locale locale) {
		List<AuditLogDTO> dataWithMessages = output.getData().stream().map(auditlog -> new AuditLogDTO(auditlog, messageSource, locale)).collect(Collectors.toList());

		DataTablesOutput<AuditLogDTO> result = new DataTablesOutput<>();
		result.setData(dataWithMessages);
		result.setDraw(output.getDraw());
		result.setError(output.getError());
		result.setRecordsFiltered(output.getRecordsFiltered());
		result.setRecordsTotal(output.getRecordsTotal());

		return result;
	}
}
