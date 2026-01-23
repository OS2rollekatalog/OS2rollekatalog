package dk.digitalidentity.rc.config;

import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Converter
public class ApprovableBySetConverter implements AttributeConverter<Set<ApprovableBy>, String> {

	@Override
	public String convertToDatabaseColumn(Set<ApprovableBy> attribute) {
		if (attribute == null || attribute.isEmpty()) {
			return "";
		}
		return attribute.stream()
			.map(Enum::name)
			.collect(Collectors.joining(","));
	}

	@Override
	public Set<ApprovableBy> convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return Collections.emptySet();
		}
		return Arrays.stream(dbData.split(","))
			.map(String::trim)
			.filter(StringUtils::isNotEmpty)
			.map(ApprovableBy::valueOf)
			.collect(Collectors.toSet());
	}
}
