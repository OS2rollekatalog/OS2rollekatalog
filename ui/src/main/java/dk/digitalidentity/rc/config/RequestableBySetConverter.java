package dk.digitalidentity.rc.config;

import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Converter
public class RequestableBySetConverter implements AttributeConverter<Set<RequestableBy>, String> {

	@Override
	public String convertToDatabaseColumn(Set<RequestableBy> attribute) {
		if (attribute == null || attribute.isEmpty()) {
			return "";
		}
		return attribute.stream()
			.map(Enum::name) // store the enum constant name
			.collect(Collectors.joining(","));
	}

	@Override
	public Set<RequestableBy> convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return Collections.emptySet();
		}
		return Arrays.stream(dbData.split(","))
			.map(String::trim)
			.map(RequestableBy::valueOf) // reconstruct enum from name
			.collect(Collectors.toSet());
	}
}
