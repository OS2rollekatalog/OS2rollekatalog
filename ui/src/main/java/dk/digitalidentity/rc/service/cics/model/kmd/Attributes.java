package dk.digitalidentity.rc.service.cics.model.kmd;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Attributes {

	@JacksonXmlElementWrapper(useWrapping = false)
	private List<Attribute> attr;
	
	// relevant methods when the search result is a user
	
	public String getUserCpr() {
		List<String> values = getAttributeValues("PersonCivilRegistrationIdentifier");
		
		if (values != null && values.size() > 0) {
			return values.get(0);
		}
		
		return null;
	}
	
	public List<String> getUserAuthorisations() {
		List<String> values = getAttributeValues("authorisations");
		
		if (values != null) {
			return values;
		}
		
		return new ArrayList<String>();
	}

	// relevant methods when search result is a userProfile
	
	public String getUserProfileName() {
		List<String> values = getAttributeValues("userProfileName");
		
		if (values != null && values.size() > 0) {
			return values.get(0);
		}
		
		return null;
	}

	public String getUserProfileDescription() {
		List<String> values = getAttributeValues("userProfileDescription");
		
		if (values != null && values.size() > 0) {
			return values.get(0);
		}
		
		return null;
	}
	
	public String getUserProfileDepartmentNumber() {
		List<String> values = getAttributeValues("departmentNumber");
		
		if (values != null && values.size() > 0) {
			return values.get(0);
		}
		
		return null;
	}
	
	public List<String> getUserProfileUsers() {
		List<String> values = getAttributeValues("userProfileUsers");
		
		if (values != null) {
			return values;
		}
		
		return new ArrayList<String>();
	}

	// private helper methods
	private List<String> getAttributeValues(String attributeName) {
		if (attr != null && attr.size() > 0 && attributeName != null) {
			for (Attribute attribute : attr) {
				if (attributeName.equals(attribute.getName())) {
					return attribute.getValue();
				}
			}
		}
		
		return null;
	}
}
