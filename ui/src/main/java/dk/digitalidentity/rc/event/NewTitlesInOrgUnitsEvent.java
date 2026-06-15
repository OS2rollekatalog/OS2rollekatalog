package dk.digitalidentity.rc.event;

import java.util.Set;

import org.springframework.context.ApplicationEvent;

import dk.digitalidentity.rc.service.model.OrgUnitWithTitlesDTO;
import lombok.Getter;

@Getter
public class NewTitlesInOrgUnitsEvent extends ApplicationEvent {

	private final Set<OrgUnitWithTitlesDTO> orgUnitsWithNewTitles;

	public NewTitlesInOrgUnitsEvent(Object source, Set<OrgUnitWithTitlesDTO> orgUnitsWithNewTitles) {
		super(source);
		this.orgUnitsWithNewTitles = orgUnitsWithNewTitles;
	}
}
