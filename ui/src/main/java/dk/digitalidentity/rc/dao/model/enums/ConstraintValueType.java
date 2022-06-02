package dk.digitalidentity.rc.dao.model.enums;

import dk.digitalidentity.rc.dao.model.ConstraintTypeValueSet;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum ConstraintValueType {
	LEVEL_1,                 // level 1-4 is only used for Organisation constraint - we should probably refactor this
	LEVEL_2,                 // at some point, as we are also going to add new dynamic constraints that are KLE only
	LEVEL_3,
	LEVEL_4,
	LEVEL_5,
	LEVEL_6,

	INHERITED,               // the user inherits constraint values from the OU(s) he holds positions in
	EXTENDED_INHERITED,      // as above, but according to the extension rules (depends on the type of constraint)
	READ_AND_WRITE,			 // the sum of the two above
	VALUE,                   // an actual hardcoded value is used
	POSTPONED;				 // the constraint is postponed to the role assignment time
	
	public static String getUIText(SystemRoleAssignmentConstraintValue assignment) throws Exception {
		switch (assignment.getConstraintValueType()) {
			case VALUE:
				return assignment.getConstraintValue();
			case EXTENDED_INHERITED:
				if (assignment.getConstraintType().getValueSet() != null) {
					for (ConstraintTypeValueSet valueSet : assignment.getConstraintType().getValueSet()) {
						if (valueSet.getConstraintKey().equals(EXTENDED_INHERITED.toString())) {
							return valueSet.getConstraintValue();
						}
					}
				}

				log.warn("Failed to find a ConstraintTypeValueSet that matches EXTENDED_INHERITED for constraintType: " + assignment.getConstraintType().getEntityId());
				break;
			case INHERITED:
				if (assignment.getConstraintType().getValueSet() != null) {
					for (ConstraintTypeValueSet valueSet : assignment.getConstraintType().getValueSet()) {
						if (valueSet.getConstraintKey().equals(INHERITED.toString())) {
							return valueSet.getConstraintValue();
						}
					}
				}

				log.warn("Failed to find a CosntraintTypeValueSet that matches INHERITED for constraintType: " + assignment.getConstraintType().getEntityId());
				break;
			default:
				throw new Exception("Unknown constraintValueType: " + assignment.getConstraintValueType());
		}
		
		return "";
	}
}
