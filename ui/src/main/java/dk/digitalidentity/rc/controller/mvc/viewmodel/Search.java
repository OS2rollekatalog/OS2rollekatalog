package dk.digitalidentity.rc.controller.mvc.viewmodel;

import lombok.Data;

@Data
public class Search {
	private boolean regex;
	private String value;
}
