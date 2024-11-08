package dk.digitalidentity.rc.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.enums.ConstraintUIType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PostponedConstraintService {

    public boolean isValidConstraint(ConstraintType constraintType, String value, long systemRoleId) {
        // Check that it has a value
        if (!StringUtils.hasLength(value)) {
            log.warn("Value is null or empty for system role id  " + systemRoleId + " and constraint type " + constraintType.getUuid());
            return false;
        }

        // perform regex validation (if needed)
        if (constraintType.getUiType().equals(ConstraintUIType.REGEX) && constraintType.getRegex() != null && constraintType.getRegex().length() > 0) {
            try {
                Pattern pattern = Pattern.compile(constraintType.getRegex());
                Matcher matcher = pattern.matcher(value);
                if (!matcher.matches()) {
                    log.warn("Input does not match regular expression: " + value + " for regex: " + constraintType.getRegex());
                    return false;
                }
            } catch (Exception ex) {
                log.warn("Unable to perform regex validation (giving it a free pass) on '" + constraintType.getEntityId() + "'. Message = " + ex.getMessage());
            }
        }
        return true;
    }
}
