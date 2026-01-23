package dk.digitalidentity.rc.config;

import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Converter
public class ApprovableByListConverter implements AttributeConverter<List<ApprovableBy>, String> {

	@Override
	public String convertToDatabaseColumn(List<ApprovableBy> attribute) {
		if (attribute == null || attribute.isEmpty()) {
			return "";
		}
		return attribute.stream()
			.map(Enum::name)
			.collect(Collectors.joining(","));
	}

	@Override
	public List<ApprovableBy> convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return Collections.emptyList();
		}
		return Arrays.stream(dbData.split(","))
			.map(String::trim)
			.filter(StringUtils::isNotEmpty)
			.map(ApprovableBy::valueOf)
			.collect(Collectors.toList());
	}
}
