package dk.digitalidentity.rc.controller.mvc.xlsview;

import java.util.Locale;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.ConstraintTypeValueSet;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class XlsUtil {
	private static XlsUtil instance;
	
	@Autowired
	private MessageSource messageSource;
	
	@PostConstruct
	public void init() {
		if (instance == null) {
			instance = this;
		}
	}
	
	// TODO: actually not used by the ReportXlsView anymore, but used by UI instead
	public static String stringifyAssignment(SystemRoleAssignment assignment, boolean html) {
		StringBuilder builder = new StringBuilder();
		if (html) {
			builder.append("<ul>");
		}

		Locale locale = LocaleContextHolder.getLocale();
		
		if (assignment.getConstraintValues() != null) {
			for (SystemRoleAssignmentConstraintValue constraintValue : assignment.getConstraintValues()) {
				String name = constraintValue.getConstraintType().getName();
				String value = "";

				switch (constraintValue.getConstraintType().getEntityId()) {
					case Constants.KLE_CONSTRAINT_ENTITY_ID:
						switch (constraintValue.getConstraintValueType()) { 
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
								log.warn("An OrgUnitLevel constraint was used on KLE!");
								break;
							case VALUE:
								value = constraintValue.getConstraintValue();
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
							case VALUE:
								value = constraintValue.getConstraintValue();
								break;
						}
						break;
					default:
						switch (constraintValue.getConstraintType().getUiType()) {
							case REGEX:
								value = constraintValue.getConstraintValue();
								break;
							case COMBO_MULTI: {
								String[] constraintValues = constraintValue.getConstraintValue().split(",");

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