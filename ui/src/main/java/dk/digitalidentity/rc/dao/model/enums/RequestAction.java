package dk.digitalidentity.rc.dao.model.enums;

public enum RequestAction {
	ADD("html.enum.requestapprove.action.add"),
	REMOVE("html.enum.requestapprove.action.remove");
	
	
	public String title = "";
	
	RequestAction(String msg) {
		title = msg;
	}
}