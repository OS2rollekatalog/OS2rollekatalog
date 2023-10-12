package dk.digitalidentity.rc.controller.api.dto.read;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class UserReadConstraintTypeValueSetDTO {
    private String constraintKey;
    private String constraintValue;
}
