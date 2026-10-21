package dk.digitalidentity.rc.rolerequest.service.permission;

import java.util.List;
import java.util.Set;

/**
 * What is true about an approver, independent of any single request.
 * @param authorizedScopes scopes for authorized roles
 * @param approveAuthorizedScopes scopes for approve authorized roles
 */
public record ApproverContext(
	String approverUuid,
	boolean admin,
	boolean deleted,
	boolean allowSelfApproval,
	Set<String> authorizationManagerOuUuids,
	Set<Long> systemResponsibleItSystemIds,
	List<AuthorizedScope> authorizedScopes,
	List<AuthorizedScope> approveAuthorizedScopes
) {

	/**
	 * OUs and the IT systems that may be approved within them.
	 * @param ouUuids     the OUs, or {@code null} for all OUs
	 * @param itSystemIds the IT systems, or {@code null} for all IT systems
	 */
	public record AuthorizedScope(Set<String> ouUuids, Set<Long> itSystemIds) {}

	public ApproverContext withoutAdminBypass() {
		return new ApproverContext(approverUuid, false, deleted, allowSelfApproval, authorizationManagerOuUuids,
			systemResponsibleItSystemIds, authorizedScopes, approveAuthorizedScopes);
	}
}
