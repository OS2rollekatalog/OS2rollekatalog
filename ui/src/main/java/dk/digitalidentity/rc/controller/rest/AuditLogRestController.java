package dk.digitalidentity.rc.controller.rest;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.controller.rest.model.AuditLogDatatableSpecificationBuilder;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.AuditLogViewDao;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.AuditLogView;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.dto.AuditLogDTO;
import jakarta.validation.Valid;

@RequiredArgsConstructor
@RequireControllerPermission(section = Section.LOG, permission = Permission.READ)
@RestController
public class AuditLogRestController {
	private final AuditLogViewDao auditLogViewDao;
	private final MessageSource messageSource;

	@PostMapping("/rest/logs/audit")
	public DataTablesOutput<AuditLogDTO> list(@Valid @RequestBody DataTablesInput input, BindingResult bindingResult,
			@RequestParam(required = false) List<String> eventTypes,
			@RequestParam(required = false) String entityType,
			@RequestParam(required = false) String entityId,
			Locale locale) {
		if (bindingResult.hasErrors()) {
			DataTablesOutput<AuditLogDTO> error = new DataTablesOutput<>();
			error.setError(bindingResult.toString());

			return error;
		}

		Specification<AuditLogView> spec = AuditLogDatatableSpecificationBuilder.build(eventTypes, entityType, entityId);

		DataTablesOutput<AuditLogView> output = spec != null
				? auditLogViewDao.findAll(input, spec)
				: auditLogViewDao.findAll(input);

		return convertAuditLogDataTablesModelToDTO(output, locale);
	}

	private DataTablesOutput<AuditLogDTO> convertAuditLogDataTablesModelToDTO(DataTablesOutput<AuditLogView> output, Locale locale) {
		List<AuditLogDTO> dataWithMessages = output.getData().stream()
				.map(auditlog -> new AuditLogDTO(auditlog, messageSource, locale))
				.collect(Collectors.toList());

		DataTablesOutput<AuditLogDTO> result = new DataTablesOutput<>();
		result.setData(dataWithMessages);
		result.setDraw(output.getDraw());
		result.setError(output.getError());
		result.setRecordsFiltered(output.getRecordsFiltered());
		result.setRecordsTotal(output.getRecordsTotal());

		return result;
	}
}
