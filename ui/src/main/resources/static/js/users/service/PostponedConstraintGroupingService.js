/**
 * Adds an "apply same value to all roles" checkbox for postponed-constraint
 * types that repeat across multiple system roles on the same role, so the
 * user doesn't have to enter the same data scope value many times.
 *
 * The master row shown when the checkbox is checked is a clone of the first
 * system role's own row for that constraint type, so it reuses the exact
 * same widget (KLE picker, OU tree picker, select2, plain input) without any
 * of this file needing to know how each constraint type renders.
 */
class PostponedConstraintGroupingService {
    constructor() {
        this.MIN_ROLES_FOR_GROUPING = 2;
        this.dropdownParent = null;
    }

    init(scope = document, dropdownParent = null) {
        this.scope = scope;
        this.dropdownParent = dropdownParent ?? null;

        const headerContainer = scope.querySelector('#postponedConstraintsGroupHeaders');
        if (!headerContainer) {
            return;
        }

        headerContainer.innerHTML = '';

        const rowsByType = this.groupRowsByConstraintType();
        for (const [constraintTypeUuid, rows] of rowsByType) {
            if (rows.length < this.MIN_ROLES_FOR_GROUPING) {
                continue;
            }

            headerContainer.appendChild(this.buildGroupHeader(constraintTypeUuid, rows));
        }
    }

    groupRowsByConstraintType() {
        const rowsByType = new Map();

        this.scope.querySelectorAll('.postponed-constraint-row').forEach((row) => {
            const constraintTypeUuid = row.dataset.constrainttype;
            if (!constraintTypeUuid) {
                return;
            }

            if (!rowsByType.has(constraintTypeUuid)) {
                rowsByType.set(constraintTypeUuid, []);
            }
            rowsByType.get(constraintTypeUuid).push(row);
        });

        return rowsByType;
    }

    buildGroupHeader(constraintTypeUuid, rows) {
        const label = rows[0]?.querySelector('label')?.textContent ?? '';

        const wrapper = document.createElement('div');
        wrapper.className = 'row postponed-constraint-group';
        wrapper.dataset.constrainttype = constraintTypeUuid;

        const checkboxCol = document.createElement('div');
        checkboxCol.className = 'col-sm-12';

        const checkboxLabel = document.createElement('label');
        checkboxLabel.className = 'postponed-constraint-group-toggle-label';

        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.className = 'postponed-constraint-group-toggle';

        checkboxLabel.appendChild(checkbox);
        checkboxLabel.append(` ${label} - Anvend samme dataafgrænsningsværdi for alle roller`);
        checkboxCol.appendChild(checkboxLabel);
        wrapper.appendChild(checkboxCol);

        const masterCol = document.createElement('div');
        masterCol.className = 'col-sm-12';

        const masterContainer = document.createElement('div');
        masterContainer.className = 'postponed-constraint-group-master';
        masterContainer.hidden = true;
        masterCol.appendChild(masterContainer);
        wrapper.appendChild(masterCol);

        checkbox.addEventListener('change', () => {
            if (checkbox.checked) {
                this.collapseGroup(constraintTypeUuid, masterContainer);
            } else {
                this.expandGroup(constraintTypeUuid, masterContainer);
            }
        });

        return wrapper;
    }

    collapseGroup(constraintTypeUuid, masterContainer) {
        const rows = this.getRowsForType(constraintTypeUuid);
        if (rows.length === 0) {
            return;
        }

        masterContainer.innerHTML = '';

        const masterRoleMarker = 'master';
        const masterRow = rows[0].cloneNode(true);
        masterRow.classList.remove('postponed-constraint-row');
        masterRow.classList.add('postponed-constraint-master-row');

        const idPrefix = `postponed${rows[0].dataset.systemroleid}`;
        const masterIdPrefix = `postponed${masterRoleMarker}`;
        masterRow.querySelectorAll('[id]').forEach((el) => {
            if (el.id.startsWith(idPrefix)) {
                el.id = masterIdPrefix + el.id.slice(idPrefix.length);
            }
        });
        masterRow.querySelectorAll('[data-systemroleid]').forEach((el) => {
            el.dataset.systemroleid = masterRoleMarker;
        });
        masterRow.querySelectorAll('.select2, .select2-container').forEach((el) => { el.remove(); });

        masterRow.querySelectorAll('.constraint').forEach((field) => {
            field.classList.remove('constraint');
            field.classList.add('postponed-constraint-master-field');

            if (field.classList.contains('select2-hidden-accessible')) {
                field.classList.remove('select2-hidden-accessible');
                field.removeAttribute('tabindex');
                field.removeAttribute('aria-hidden');
                field.style.display = '';
            }
        });

        masterContainer.appendChild(masterRow);
        masterContainer.hidden = false;

        rows.forEach((row) => { row.hidden = true; });

        window.postponedConstraintsService?.initGroupMasterRow(masterContainer, this.dropdownParent);

        masterContainer.querySelectorAll('.postponed-constraint-master-field').forEach((masterField) => {
            $(masterField).on('change', () => {
                this.propagateValue(masterField, rows);
            });
        });

        this.updateSystemRoleBlockVisibility();
    }

    expandGroup(constraintTypeUuid, masterContainer) {
        const rows = this.getRowsForType(constraintTypeUuid);
        rows.forEach((row) => { row.hidden = false; });

        masterContainer.hidden = true;
        masterContainer.innerHTML = '';

        // rows[0] was cloned to build the master row; cloning a select2-bound <select> leaves
        // the original element's own select2 instance in a torn-down state, so it must be re-initialized
        window.postponedConstraintsService?.reinitRowFields(rows[0], this.dropdownParent);

        this.updateSystemRoleBlockVisibility();
    }

    updateSystemRoleBlockVisibility() {
        this.scope.querySelectorAll('.postponed-systemrole-block').forEach((block) => {
            const rows = Array.from(block.querySelectorAll('.postponed-constraint-row'));
            const allRowsCollapsed = rows.length > 0 && rows.every((row) => row.hidden);
            block.hidden = allRowsCollapsed;
        });
    }

    getRowsForType(constraintTypeUuid) {
        return Array.from(this.scope.querySelectorAll(`.postponed-constraint-row[data-constrainttype="${constraintTypeUuid}"]`));
    }

    propagateValue(masterField, rows) {
        const value = $(masterField).val();

        rows.forEach((row) => {
            const targetField = row.querySelector('.constraint');
            if (!targetField) {
                return;
            }

            $(targetField).val(value).trigger('change');
        });
    }
}
