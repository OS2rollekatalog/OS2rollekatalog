package dk.digitalidentity.rc.controller.rest.model;

import java.util.Set;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StringArrayWrapper {
	private Set<String> titleUuids;
	private Set<String> exceptedUserUuids;
}
