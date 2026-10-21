import {registerOUAssignmentDelegation} from './assign/ou-assignment_wizard.js';

const config = JSON.parse(document.getElementById('ou-manage-config').textContent);

registerOUAssignmentDelegation(document, {
	selector: '.js-add-role-assignment',
	resolve: (el) => ({ouUuid: config.ou, roleType: el.dataset.objtype, roleId: el.dataset.roleid}),
	onAssigned: () => window.rolesService.loadRolesFragment(),
});

registerOUAssignmentDelegation(document, {
	selector: '.js-edit-role-assignment',
	resolve: (el) => {
		const cell = el.closest('td');

		return {
			ouUuid: config.ou,
			roleType: cell.dataset.objtype.toLowerCase(),
			roleId: cell.dataset.roleid,
			assignmentId: cell.dataset.assignmentid,
		};
	},
	onAssigned: () => window.rolesService.loadRolesFragment(),
});
