package dk.digitalidentity.rc.controller.mvc.viewmodel;

import lombok.Data;

@Data
public class Assignment {
	private AssignmentType assignmentType;
	private String position = "";
}