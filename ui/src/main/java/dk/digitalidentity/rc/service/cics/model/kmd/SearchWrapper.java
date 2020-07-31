package dk.digitalidentity.rc.service.cics.model.kmd;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SearchWrapper {
	private String result;
	
	@JacksonXmlElementWrapper(useWrapping = false)
	private List<SearchEntry> searchResultEntry;
}
