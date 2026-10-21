import {registerOUAssignmentDelegation} from '../ous/assign/ou-assignment_wizard.js';

const config = JSON.parse(document.getElementById('rolegroup-edit-config').textContent);

registerOUAssignmentDelegation(document.getElementById('ous_menu'), {
	selector: '.js-add-ou-role-assignment',
	resolve: (el) => ({ouUuid: el.dataset.orgunit, roleType: 'rolegroup', roleId: config.roleId}),
	onAssigned: () => window.orgUnitService.loadRolesFragment(),
});

registerOUAssignmentDelegation(document.getElementById('ous_menu'), {
	selector: '.js-edit-ou-assignment',
	resolve: (el) => {
		const cell = el.closest('td');

		return {
			ouUuid: cell.dataset.orgunit,
			roleType: 'rolegroup',
			roleId: config.roleId,
			assignmentId: cell.dataset.assignmentid,
		};
	},
	onAssigned: () => window.orgUnitService.loadRolesFragment(),
});
