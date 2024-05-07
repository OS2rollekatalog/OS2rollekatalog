package dk.digitalidentity.rc.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

	@Override
	public String convertToDatabaseColumn(List<String> list) {
		return String.join(",", list);
	}

	@Override
	public List<String> convertToEntityAttribute(String joined) {
		if ("".equals(joined)) {
			return Collections.emptyList();
		}
		return new ArrayList<>(Arrays.asList(joined.split(",")));
	}
}