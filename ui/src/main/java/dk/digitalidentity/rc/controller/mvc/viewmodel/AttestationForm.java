package dk.digitalidentity.rc.controller.mvc.viewmodel;

import java.io.Serializable;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttestationForm implements Serializable {
	private static final long serialVersionUID = -4953132828946237008L;

	private String orgUnitUuid;
	private Map<String, AttestationRemovalSet> data;
}
