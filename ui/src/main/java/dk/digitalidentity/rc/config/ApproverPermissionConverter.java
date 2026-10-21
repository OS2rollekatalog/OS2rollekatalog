package dk.digitalidentity.rc.config;

import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.model.permission.ApproverPermission;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * For the approver settings on roles and IT systems: NULL is the column default and means INHERIT,
 * while an empty value means nobody.
 */
@Slf4j
@Converter
public class ApproverPermissionConverter implements AttributeConverter<ApproverPermission, String> {

	@Override
	public String convertToDatabaseColumn(ApproverPermission attribute) {
		return (attribute == null ? ApproverPermission.inherit() : attribute).toString();
	}

	@Override
	public ApproverPermission convertToEntityAttribute(String dbData) {
		return dbData == null ? ApproverPermission.inherit() : parse(dbData);
	}

	static ApproverPermission parse(String dbData) {
		Set<ApprovableBy> values = ApproverPermission.parseValues(dbData);
		ApproverPermission permission = ApproverPermission.of(ApproverPermission.resolveConflicts(values));
		if (permission.getApprovableBy().size() != values.size()) {
			log.warn("Invalid approver permission '{}' read as '{}'", dbData, permission);
		}
		return permission;
	}
}
