package dk.digitalidentity.rc.controller.api.model;

import dk.digitalidentity.rc.dao.model.enums.ConstraintUIType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "ConstraintType")
public class ConstraintTypeAM {
    @Schema
    private long id;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private String uuid;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private String entityId;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private String name;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private String description;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private ConstraintUIType uiType;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private String regex;
}
