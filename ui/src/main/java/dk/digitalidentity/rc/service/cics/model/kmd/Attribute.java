package dk.digitalidentity.rc.service.cics.model.kmd;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Attribute {
	private String name;
	
	// TODO: try with localName so we can call it values() here
	@JacksonXmlElementWrapper(useWrapping = false)
	private List<String> value;
}
