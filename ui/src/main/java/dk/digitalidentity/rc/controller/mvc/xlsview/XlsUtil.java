package dk.digitalidentity.rc.controller.mvc.xlsview;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.ConstraintTypeValueSet;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.OrgUnitService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class XlsUtil {
	private static XlsUtil instance;
	
	@Autowired
	private MessageSource messageSource;

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private ItSystemService itSystemService;
	
	@PostConstruct
	public void init() {
		if (instance == null) {
			instance = this;
		}
	}
	
	// TODO: actually not used by the ReportXlsView anymore, but used by UI instead
	public String stringifyAssignment(SystemRoleAssignment assignment, boolean html) {
		StringBuilder builder = new StringBuilder();
		if (html) {
			builder.append("<ul>");
		}

		Locale locale = LocaleContextHolder.getLocale();

		if (assignment.getConstraintValues() != null) {
			for (SystemRoleAssignmentConstraintValue constraintValue : assignment.getConstraintValues()) {
				String name = constraintValue.getConstraintType().getName();
				String value = "";

				String[] constraintValues;
				List<String> values;
				switch (constraintValue.getConstraintType().getEntityId()) {
					case Constants.KLE_CONSTRAINT_ENTITY_ID:
						switch (constraintValue.getConstraintValueType()) {
							case READ_AND_WRITE:
								value = instance.messageSource.getMessage("html.constraint.kle.read_and_write", null, locale);
								break;
							case EXTENDED_INHERITED:
								value = instance.messageSource.getMessage("html.constraint.kle.extended", null, locale);
								break;
							case INHERITED:
								value = instance.messageSource.getMessage("html.constraint.kle.inherited", null, locale);
								break;
							case LEVEL_1:
							case LEVEL_2:
							case LEVEL_3:
							case LEVEL_4:
							case LEVEL_5:
							case LEVEL_6:
								log.warn("An OrgUnitLevel constraint was used on KLE!");
								break;
							case VALUE:
								value = constraintValue.getConstraintValue();
								break;
							case POSTPONED:
								value = "Udskudt";
								break;
						}
						break;
					case Constants.OU_CONSTRAINT_ENTITY_ID:
						switch (constraintValue.getConstraintValueType()) {
							case EXTENDED_INHERITED:
								value = instance.messageSource.getMessage("html.constraint.organisation.extended", null, locale);
								break;
							case INHERITED:
								value = instance.messageSource.getMessage("html.constraint.organisation.inherited", null, locale);
								break;
							case LEVEL_1:
								value = instance.messageSource.getMessage("html.constraint.organisation.level.1", null, locale);
								break;
							case LEVEL_2:
								value = instance.messageSource.getMessage("html.constraint.organisation.level.2", null, locale);
								break;
							case LEVEL_3:
								value = instance.messageSource.getMessage("html.constraint.organisation.level.3", null, locale);
								break;
							case LEVEL_4:
								value = instance.messageSource.getMessage("html.constraint.organisation.level.4", null, locale);
								break;
							case LEVEL_5:
								value = instance.messageSource.getMessage("html.constraint.organisation.level.5", null, locale);
								break;
							case LEVEL_6:
								value = instance.messageSource.getMessage("html.constraint.organisation.level.6", null, locale);
								break;
							case READ_AND_WRITE:
								log.warn("An READ/WRITE was assigned as a constraint on OrgUnit");
								break;
							case POSTPONED:
								value = "udskudt";
								break;
							case VALUE:								
								values = new ArrayList<>();
								constraintValues = constraintValue.getConstraintValue().split(",");
								for (String uuid : constraintValues) {
									var orgUnit = instance.orgUnitService.getByUuid(uuid);
									if (orgUnit == null) {
										values.add(uuid);
									}
									else {
										values.add(orgUnit.getName());
									}
								}
								value = values.stream().collect(Collectors.joining(", "));								
								break;
						}
						break;
					case Constants.KOMBIT_ITSYSTEM_CONSTRAINT_ENTITY_ID:
						values = new ArrayList<>();
						constraintValues = constraintValue.getConstraintValue().split(",");

						for (String id : constraintValues) {
							ItSystem itSystem = null;
							try {
								itSystem = instance.itSystemService.getById(Long.parseLong(id));
							}
							catch (Exception ex) {
								; // ignore bad values ;)
							}

							if (itSystem == null) {
								values.add(id);
							}
							else {
								values.add(itSystem.getName());
							}
						}
						value = values.stream().collect(Collectors.joining(", "));
						break;
					default:
						switch (constraintValue.getConstraintType().getUiType()) {
							case REGEX:
								value = constraintValue.getConstraintValue();
								break;
							case COMBO_MULTI: {
								constraintValues = constraintValue.getConstraintValue().split(",");

								for (ConstraintTypeValueSet valueEntry : constraintValue.getConstraintType().getValueSet()) {
									for (String val : constraintValues) {
										if (val.equals(valueEntry.getConstraintKey())) {
											if (value.length() > 0) {
												value += ",";
											}
											
											value += valueEntry.getConstraintValue();
										}
									}
								}
								break;
							}
							case COMBO_SINGLE:
								for (ConstraintTypeValueSet valueEntry : constraintValue.getConstraintType().getValueSet()) {
									if (constraintValue.getConstraintValue().equals(valueEntry.getConstraintKey())) {
										value = valueEntry.getConstraintValue();
									}
								}
								break;
						}
						break;
				}
				
				if (html) {
					builder.append("<li><b>" + name + "</b>: " + value + "</li>");
				}
				else {
					if (builder.length() > 0) {
						builder.append("\n");
					}

					builder.append(" * " + name + ": " + value);
				}
			}
		}

		if (html) {
			builder.append("</ul>");
		}

		return builder.toString();
	}
}