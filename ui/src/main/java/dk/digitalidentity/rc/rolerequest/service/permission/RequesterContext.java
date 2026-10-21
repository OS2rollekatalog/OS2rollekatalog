package dk.digitalidentity.rc.rolerequest.service.permission;

import java.util.List;
import java.util.Set;

/**
 * What is true about a requester relative to one receiver in one of the receiver's OUs.
 * @param authorized             has the request-authorized role with a scope covering the receiver's OU
 * @param authorizedItSystemIds  IT systems allowed in the receiver's OU, or {@code null} for all IT systems
 * @param receiverOuUuid         {@code null} when the receiver has no OU
 * @param receiverOuAncestorUuids the receiver's OU and all its ancestors
 */
public record RequesterContext(
	boolean admin,
	boolean self,
	boolean managerOrSubstitute,
	boolean authResponsible,
	boolean authorized,
	Set<Long> authorizedItSystemIds,
	String receiverOuUuid,
	List<String> receiverOuAncestorUuids,
	boolean enforceOuFilter
) {

	public RequesterContext withoutOuFilter() {
		return new RequesterContext(admin, self, managerOrSubstitute, authResponsible, authorized, authorizedItSystemIds,
			receiverOuUuid, receiverOuAncestorUuids, false);
	}

	/**
	 * Keeps only the flag behind the given authority, to find out which authority a request relies on.
	 */
	public RequesterContext only(RequestAuthority authority) {
		return new RequesterContext(
			authority == RequestAuthority.ADMIN && admin,
			authority == RequestAuthority.SELF && self,
			authority == RequestAuthority.MANAGER && managerOrSubstitute,
			authority == RequestAuthority.AUTH_RESPONSIBLE && authResponsible,
			authority == RequestAuthority.REQUEST_AUTHORIZED && authorized,
			authorizedItSystemIds,
			receiverOuUuid,
			receiverOuAncestorUuids,
			enforceOuFilter
		);
	}
}
