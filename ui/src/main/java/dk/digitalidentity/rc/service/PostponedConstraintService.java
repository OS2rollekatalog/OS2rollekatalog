package dk.digitalidentity.rc.service;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.PostponedConstraintDao;
import dk.digitalidentity.rc.dao.model.PostponedConstraint;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.enums.ConstraintUIType;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
@Service
public class PostponedConstraintService {
    private final PostponedConstraintDao postPonedConstraintDao;

    public PostponedConstraint save(PostponedConstraint postponedConstraint) {
        return postPonedConstraintDao.save(postponedConstraint);
    }

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

    public List<PostponedConstraint> getPostPonedConstraintValues(User user, SystemRoleAssignment systemRoleAssignment) {
        return postPonedConstraintDao.findByUserUserRoleAssignment_User_UuidAndSystemRole_IdAndUserUserRoleAssignment_UserRole_Id(user.getUuid(), systemRoleAssignment.getSystemRole().getId(), systemRoleAssignment.getUserRole().getId());
    }

    public Set<PostponedConstraint> findAllForUserAndRoleCatalogue(User user) {
        return postPonedConstraintDao.findByUserUserRoleAssignment_UserAndConstraintType_EntityIdIn(user, Set.of(Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID, Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID));
    }

    public Set<PostponedConstraint> findAllForSystemRoleAndUserRole(String systemRoleIdentifier, UserRole userRole) {
        return postPonedConstraintDao.findBySystemRole_IdentifierAndUserUserRoleAssignment_UserRole(systemRoleIdentifier, userRole);
    }

}
