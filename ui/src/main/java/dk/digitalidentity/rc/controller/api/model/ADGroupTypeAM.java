package dk.digitalidentity.rc.controller.api.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ADGroupType")
public enum ADGroupTypeAM {
	NONE, SECURITY, DISTRIBUTION;
}
