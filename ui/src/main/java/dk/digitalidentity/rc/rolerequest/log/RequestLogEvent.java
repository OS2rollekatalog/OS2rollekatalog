package dk.digitalidentity.rc.rolerequest.log;

import dk.digitalidentity.rc.dao.model.enums.EventType;
import lombok.Getter;

@Getter
public enum RequestLogEvent {
	REQUEST("requestmodule.log.event.request.request", EventType.REQUEST_ROLE_FOR),
	REMOVE("requestmodule.log.event.request.remove", EventType.REQUEST_ROLE_REMOVAL_FOR),
	CANCEL("requestmodule.log.event.request.cancel", EventType.CANCEL_REQUEST),
	APPROVE("requestmodule.log.event.request.approve",EventType.APPROVE_REQUEST),
	DENY("requestmodule.log.event.request.deny", EventType.REJECT_REQUEST);

	private String message;
	private EventType eventType;

	private RequestLogEvent(String message, EventType eventType) {
		this.message = message;
		this.eventType =eventType;
	}

}
