package dk.digitalidentity.rc.controller.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "Title")
public class TitleAM {
    @Schema(description = "The titles UUID")
    private String uuid;
    @Schema(description = "Title name")
    private String name;
    @Schema(description = "When was the title last updated")
    private Date lastUpdated;
    @Schema(description = "Is the title active")
    private boolean active;
}
