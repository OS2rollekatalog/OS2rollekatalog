package dk.digitalidentity.rc.controller.rest.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class UserRoleForRoleGroupDto {
    private Long id;
    private String name;
    private String description;
    private String itSystemName;
    private Long rolegroupId;
    private Boolean selected;
    private Boolean readOnly;
}