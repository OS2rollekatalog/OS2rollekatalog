package dk.digitalidentity.rc.dao.model.enums;

public enum ConstraintUIType {
	COMBO_SINGLE,   // dropdown with a fixed set of possible values, only one can be selected
	COMBO_MULTI,    // dropdown with a fixed set of possible values, multiple can be selected
	REGEX           // input field, with a regular expression for input validation
}
