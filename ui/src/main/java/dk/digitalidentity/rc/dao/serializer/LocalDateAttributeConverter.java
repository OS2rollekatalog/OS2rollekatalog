package dk.digitalidentity.rc.dao.serializer;

import jakarta.persistence.AttributeConverter;

import java.time.LocalDate;

public class LocalDateAttributeConverter implements AttributeConverter<LocalDate, String> {

	@Override
	public String convertToDatabaseColumn(LocalDate attribute) {
		return attribute == null ? null : attribute.toString();
	}

	@Override
	public LocalDate convertToEntityAttribute(String dbData) {
		return dbData == null ? null : LocalDate.parse(dbData);
	}
}