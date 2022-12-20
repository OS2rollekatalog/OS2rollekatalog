package dk.digitalidentity.rc.controller.rest.model;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OUFilterDTO {
	private long id;
	private List<String> selectedOUs;
}
	