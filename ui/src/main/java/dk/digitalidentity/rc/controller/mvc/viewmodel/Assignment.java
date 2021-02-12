package dk.digitalidentity.rc.controller.mvc.viewmodel;

import java.time.LocalDate;

import lombok.Data;

@Data
public class Assignment {
	private AssignmentType assignmentType;
	private String position = "";
	private LocalDate startDate;
	private LocalDate stopDate;
}