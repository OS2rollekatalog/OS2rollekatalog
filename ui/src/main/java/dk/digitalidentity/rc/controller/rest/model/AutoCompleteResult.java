package dk.digitalidentity.rc.controller.rest.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AutoCompleteResult {
	private List<ValueData> suggestions = new ArrayList<ValueData>();
}
