package dk.digitalidentity.rc.controller.rest.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ItemPermissionDTO {
	private boolean isDuplicateable = false;
	private boolean isReadable = false;
	private boolean isEditable = false;
	private boolean isDeletable = false;

	public ItemPermissionDTO(ItemPermissionDTO itemPermissionDTO) {
		isDuplicateable = itemPermissionDTO.isDuplicateable();
		isReadable = itemPermissionDTO.isReadable();
		isEditable = itemPermissionDTO.isEditable();
		isDeletable = itemPermissionDTO.isDeletable();
	}
}
