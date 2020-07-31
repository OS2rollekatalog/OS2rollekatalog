package dk.digitalidentity.rc.service.model;

import lombok.Getter;

@Getter
public enum RequestApproveManagerAction {
	NONE("html.setting.requestapprove.manager.action.none"),
	NOTIFY("html.setting.requestapprove.manager.action.notify"),
	APPROVE("html.setting.requestapprove.manager.action.approve");
	
	private String message;
	
	private RequestApproveManagerAction(String message) {
		this.message = message;
	}
}
