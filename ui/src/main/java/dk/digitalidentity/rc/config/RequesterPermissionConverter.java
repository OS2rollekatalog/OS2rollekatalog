package dk.digitalidentity.rc.config;

import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import dk.digitalidentity.rc.rolerequest.model.permission.RequesterPermission;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

@Slf4j
@Converter
public class RequesterPermissionConverter implements AttributeConverter<RequesterPermission, String> {

	@Override
	public String convertToDatabaseColumn(RequesterPermission attribute) {
		return (attribute == null ? RequesterPermission.inherit() : attribute).toString();
	}

	@Override
	public RequesterPermission convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return RequesterPermission.inherit();
		}
		Set<RequestableBy> values = RequesterPermission.parseValues(dbData);
		RequesterPermission permission = RequesterPermission.of(RequesterPermission.resolveConflicts(values));
		if (permission.getRequestableBy().size() != values.size()) {
			log.warn("Invalid requester permission '{}' read as '{}'", dbData, permission);
		}
		return permission;
	}
}
