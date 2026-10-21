package dk.digitalidentity.rc.config;

import dk.digitalidentity.rc.rolerequest.model.permission.ApproverPermission;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * For the resolved approver setting stored on a request. It is never INHERIT, and a missing value means nobody.
 */
@Converter
public class RequestApproverOptionConverter implements AttributeConverter<ApproverPermission, String> {

	@Override
	public String convertToDatabaseColumn(ApproverPermission attribute) {
		return (attribute == null ? ApproverPermission.nobody() : attribute).toString();
	}

	@Override
	public ApproverPermission convertToEntityAttribute(String dbData) {
		return dbData == null ? ApproverPermission.nobody() : ApproverPermissionConverter.parse(dbData);
	}
}
