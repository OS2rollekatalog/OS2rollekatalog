package dk.digitalidentity.rc.controller.api.mapper;

import dk.digitalidentity.rc.controller.api.dto.FunctionDTO;
import dk.digitalidentity.rc.dao.model.Function;

public class FunctionMapper {

	public static FunctionDTO functionToApi(final Function function) {
		return FunctionDTO.builder()
				.name(function.getName())
				.uuid(function.getUuid())
				.build();
	}

	public static Function functionToEntity(FunctionDTO function) {
		Function f = new Function();
		f.setName(function.getName());
		f.setUuid(function.getUuid());
		return f;
	}

}
