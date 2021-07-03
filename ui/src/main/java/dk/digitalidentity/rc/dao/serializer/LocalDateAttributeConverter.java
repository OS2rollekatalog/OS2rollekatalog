package dk.digitalidentity.rc.dao.serializer;

import java.time.LocalDate;

import javax.persistence.AttributeConverter;

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