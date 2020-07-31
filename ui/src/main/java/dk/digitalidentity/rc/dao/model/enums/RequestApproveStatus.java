package dk.digitalidentity.rc.dao.model.enums;

import lombok.Getter;

@Getter
public enum RequestApproveStatus {
	REQUESTED("html.enum.requestapprove.status.requested"),
	MANAGER_NOTIFIED("html.enum.requestapprove.status.notified"),
	MANAGER_APPROVED("html.enum.requestapprove.status.approved"),
	ASSIGNED("html.enum.requestapprove.status.assigned"),
	REJECTED("html.enum.requestapprove.status.rejected");

	private String message;
	
	private RequestApproveStatus(String message) {
		this.message = message;
	}
}
