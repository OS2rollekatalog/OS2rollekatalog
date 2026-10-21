// Handles the report full/template form page (report/full.html)

// Page-level config values, populated in DOMContentLoaded below.
// Kept as plain top-level variables (mirroring the original inline script) since
// several services read/write them across each other (e.g. allOrgUnits is
// refreshed by ReportDatePickerService and read by FilterService).
let url;
let restUrl;
let restUrlOus;
let fieldUpdatedMsg;
let fieldNotUpdatedMsg;
let columnSelectedCountMsg;
let rememberedSettingsMsg;
let manager;
let itsystemFilter;
let unitFilter;
let templateId;
let allItSystems;
let allOrgUnits;

let reportService;
let templateService;
let columnService;
let columnPanelService;
let reportSettingsMemoryService;
let reportDatePickerService;
let filterService;

class ReportService {
    init() {
        $('#reportForm').submit(function (event) {
            event.preventDefault();

            const request = new XMLHttpRequest();
            request.open('POST', url + '/download', true);
            request.setRequestHeader('X-CSRF-TOKEN', window.token);
            request.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
            request.responseType = 'blob';

            request.onload = function () {
                $('body').removeClass('loading');

                if (request.status === 200) {
                    reportSettingsMemoryService.write();

                    const disposition = request.getResponseHeader('content-disposition');
                    const matches = /"([^"]*)"/.exec(disposition);
                    const filename = (matches !== null && matches[1]) ? matches[1] : 'report.xls';

                    // The actual download
                    const blob = new Blob([request.response], { type: 'application/ms-excel' });
                    const link = document.createElement('a');
                    link.href = window.URL.createObjectURL(blob);
                    link.download = filename;

                    document.body.appendChild(link);
                    link.click();
                    document.body.removeChild(link);
                }
            };

            request.send($(this).serialize());

            $('body').addClass('loading');
        });
    }
}

class TemplateService {
    init() {
        $('#saveTemplateModal').on('hidden.bs.modal', () => {
            $('#templateName').val('');
        });

        $('#saveTemplateModal').on('shown.bs.modal', () => {
            $('#templateName').focus();
        });

        document.addEventListener('click', (event) => {
            if (event.target.closest('.js-open-save-template-modal')) {
                templateService.openSaveTemplateModal();
            } else if (event.target.closest('.js-save-template')) {
                templateService.saveTemplate();
            } else if (event.target.closest('.js-close-save-template')) {
                templateService.closeSaveTemplate();
            }
        });
    }

    openSaveTemplateModal() {
        $('#saveTemplateModal').modal('show');
    }

    closeSaveTemplate() {
        $('#saveTemplateModal').modal('hide');
    }

    saveTemplate() {
        let unitFilterValue = [];
        const ous = $('#realOUs').val();
        if (ous !== '') {
            unitFilterValue = ous.split(',');
        }

        const dataObj = {
            name: $('#templateName').val(),
            showUsers: $('#realShowUsers').val(),
            showOUs: $('#realShowOUs').val(),
            showUserRoles: $('#realShowUserRoles').val(),
            showNegativeRoles: $('#realShowNegativeRoles').val(),
            showItSystems: $('#realShowItSystems').val(),
            showInactiveUsers: $('#realShowInactiveUsers').val(),
            itsystemFilter: $('#itSystemSelectPicker').val(),
            managerFilter: $('#managerSelect').val(),
            unitFilter: unitFilterValue,
            showSystemRoles: $('#realShowSystemRoles').val(),
            showOrgUnitAllocations: $('#realShowOrgUnitAllocations').val(),
            selectedColumns: columnPanelService.getColumnsState()
        };

        templateService.closeSaveTemplate();

        $.ajax({
            contentType: 'application/json',
            url: restUrl + "/save-template",
            method: "POST",
            headers: {
                'X-CSRF-TOKEN': window.token
            },
            success: () => {
                reportSettingsMemoryService.write();

                $.notify({
                    message: fieldUpdatedMsg
                }, {
                    status: 'success',
                    autoHideDelay: 4000
                });
            },
            error: errorHandler(fieldNotUpdatedMsg),
            data: JSON.stringify(dataObj)
        });
    }
}

class ColumnService {
    init() {
        $('.column-choice').trigger('change');

        document.addEventListener('change', (event) => {
            const checkbox = event.target.closest('.js-column-choice');
            if (checkbox) {
                this.columnChoiceTrigger(checkbox, checkbox.dataset.columnName);
            }
        });
    }

    columnChoiceTrigger(checkbox, name) {
        $('#real' + name).val(checkbox.checked);
    }
}

// Handles the per-content-type "Vælg kolonner" panels: expand/collapse, select-all/default
// shortcuts, the "X of Y" status tag, and writing the current selection into the hidden
// #selectedColumnsJson input so it gets submitted with the rest of the form.
class ColumnPanelService {
    init() {
        document.addEventListener('click', (event) => {
            const toggle = event.target.closest('.js-toggle-column-panel');
            if (toggle) {
                event.preventDefault();
                this.togglePanel(toggle);
                return;
            }

            const selectAll = event.target.closest('.js-select-all-columns');
            if (selectAll) {
                event.preventDefault();
                this.setAllChecked(selectAll.dataset.contentType, true);
                return;
            }

            const deselectAll = event.target.closest('.js-deselect-all-columns');
            if (deselectAll) {
                event.preventDefault();
                this.setAllChecked(deselectAll.dataset.contentType, false);
                return;
            }

            const reset = event.target.closest('.js-reset-default-columns');
            if (reset) {
                event.preventDefault();
                this.resetToDefault(reset.dataset.contentType);
                return;
            }
        });

        document.addEventListener('change', (event) => {
            const checkbox = event.target.closest('.js-column-detail-checkbox');
            if (checkbox) {
                this.refreshCountTag(this.panelIdForContentType(checkbox.dataset.contentType));
                this.serialize();
            }
        });

        document.querySelectorAll('.column-panel-toggle').forEach((toggle) => {
            this.refreshCountTag(toggle.dataset.panelId);
        });
        this.serialize();
    }

    /**
     * A panel (e.g. "Enheder") can contain checkboxes from more than one content type (e.g.
     * ShowOUs + ShowOUKLE as separate sub-groups) - this finds the panel a given content type's
     * checkboxes live in, via DOM containment, so a single-checkbox change can refresh the right
     * panel's combined count tag.
     */
    panelIdForContentType(contentType) {
        const checkbox = document.querySelector('.js-column-detail-checkbox[data-content-type="' + contentType + '"]');
        const panel = checkbox ? checkbox.closest('.column-panel') : null;
        return panel ? panel.dataset.panelId : null;
    }

    togglePanel(link) {
        const panelId = link.dataset.panelId;
        const panel = document.querySelector('.column-panel[data-panel-id="' + panelId + '"]');
        const expanding = (panel.style.display === 'none' || panel.style.display === '');

        panel.style.display = expanding ? 'block' : 'none';
        link.textContent = expanding ? link.dataset.hideText : link.dataset.selectText;
    }

    setAllChecked(contentType, checked) {
        document.querySelectorAll('.js-column-detail-checkbox[data-content-type="' + contentType + '"]').forEach((checkbox) => {
            checkbox.checked = checked;
        });
        this.refreshCountTag(this.panelIdForContentType(contentType));
        this.serialize();
    }

    resetToDefault(contentType) {
        document.querySelectorAll('.js-column-detail-checkbox[data-content-type="' + contentType + '"]').forEach((checkbox) => {
            checkbox.checked = checkbox.dataset.defaultSelected === 'true';
        });
        this.refreshCountTag(this.panelIdForContentType(contentType));
        this.serialize();
    }

    getCheckboxState(contentType) {
        const checkboxes = document.querySelectorAll('.js-column-detail-checkbox[data-content-type="' + contentType + '"]');
        const selected = [];
        let checkedCount = 0;

        checkboxes.forEach((checkbox) => {
            if (checkbox.checked) {
                checkedCount++;
                selected.push(checkbox.dataset.columnKey);
            }
        });

        return { total: checkboxes.length, checkedCount, selected };
    }

    /**
     * Sums checkbox state across every content-type sub-group inside one panel (a panel may
     * cover 1 or 2 sheets, e.g. "Roller" covers both User-Roles and User-KLE).
     */
    getPanelCheckboxState(panelId) {
        const panel = document.querySelector('.column-panel[data-panel-id="' + panelId + '"]');
        if (!panel) {
            return { total: 0, checkedCount: 0 };
        }

        const checkboxes = panel.querySelectorAll('.js-column-detail-checkbox');
        let checkedCount = 0;
        checkboxes.forEach((checkbox) => {
            if (checkbox.checked) {
                checkedCount++;
            }
        });

        return { total: checkboxes.length, checkedCount };
    }

    refreshCountTag(panelId) {
        if (!panelId) {
            return;
        }

        const toggle = document.querySelector('.column-panel-toggle[data-panel-id="' + panelId + '"]');
        if (!toggle) {
            return;
        }

        const tag = toggle.querySelector('.js-column-count-tag');
        const { total, checkedCount } = this.getPanelCheckboxState(panelId);

        tag.textContent = columnSelectedCountMsg.replace('{0}', checkedCount).replace('{1}', total);
    }

    /**
     * Serializes every content type's selection into the hidden #selectedColumnsJson input.
     * A content type where everything is checked is omitted entirely (server treats a missing
     * key as "all columns"). A content type with nothing checked is still included, as an empty
     * array (server treats that as "exclude this sheet"). Content types are discovered directly
     * from the checkboxes present, not from panel toggles.
     */
    serialize() {
        const result = {};
        const contentTypes = new Set();

        document.querySelectorAll('.js-column-detail-checkbox').forEach((checkbox) => {
            contentTypes.add(checkbox.dataset.contentType);
        });

        contentTypes.forEach((contentType) => {
            const { total, checkedCount, selected } = this.getCheckboxState(contentType);

            if (checkedCount < total) {
                result[contentType] = selected;
            }
        });

        $('#selectedColumnsJson').val(JSON.stringify(result));
        return result;
    }

    getColumnsState() {
        return this.serialize();
    }

    /**
     * Applies a previously-remembered selection (from localStorage). A missing
     * entry for a content type means "all columns"; an empty array means "exclude this sheet" -
     * same as what the server expects in the request.
     */
    applyColumnsState(columnsState) {
        const contentTypes = new Set();
        document.querySelectorAll('.js-column-detail-checkbox').forEach((checkbox) => {
            contentTypes.add(checkbox.dataset.contentType);
        });

        contentTypes.forEach((contentType) => {
            const selected = columnsState ? columnsState[contentType] : null;

            document.querySelectorAll('.js-column-detail-checkbox[data-content-type="' + contentType + '"]').forEach((checkbox) => {
                checkbox.checked = !selected || selected.indexOf(checkbox.dataset.columnKey) !== -1;
            });
        });

        document.querySelectorAll('.column-panel-toggle').forEach((toggle) => {
            this.refreshCountTag(toggle.dataset.panelId);
        });

        this.serialize();
    }
}

// Remembers the user's last-used report settings (filters, content-type toggles, column
// selections - never the date, which always defaults to today) in localStorage, and restores
// them on the next visit unless an explicit template is being loaded.
class ReportSettingsMemoryService {
    constructor() {
        this.storageKey = 'ReportConfigure_' + window.location.pathname;
    }

    init() {
        if (templateId && templateId != 0) {
            // don't restore when an explicit template is selected
            return;
        }

        const saved = this.read();
        if (!saved) {
            return;
        }

        this.applySettings(saved);
        this.showBar(saved.savedAt);

        const resetLink = document.getElementById('resetToDefaultLink');
        if (resetLink) {
            resetLink.addEventListener('click', (event) => {
                event.preventDefault();
                this.clear();
                window.location.reload();
            });
        }
    }

    read() {
        try {
            const raw = localStorage.getItem(this.storageKey);
            return raw ? JSON.parse(raw) : null;
        }
        catch (e) {
            return null;
        }
    }

    write() {
        try {
            const settings = {
                savedAt: new Date().toISOString(),
                filters: this.collectFilters(),
                showFlags: this.collectShowFlags(),
                selectedColumns: columnPanelService ? columnPanelService.getColumnsState() : {}
            };
            localStorage.setItem(this.storageKey, JSON.stringify(settings));
        }
        catch (e) {
            // localStorage unavailable
        }
    }

    clear() {
        try {
            localStorage.removeItem(this.storageKey);
        }
        catch (e) {
            // ignore
        }
    }

    collectShowFlags() {
        const flags = {};
        document.querySelectorAll('.js-column-choice').forEach((checkbox) => {
            flags[checkbox.dataset.columnName] = checkbox.checked;
        });
        return flags;
    }

    collectFilters() {
        return {
            managerFilter: $('#managerSelect').val(),
            itsystemFilter: $('#itSystemSelectPicker').val(),
            unitFilter: $('#realOUs').val(),
            activeRadioId: $('input[name="radioFilter"]:checked').attr('id')
        };
    }

    applySettings(saved) {
        // Must run before reportDatePickerService.init(), which reads these variables.
        if (saved.filters) {
            if (saved.filters.activeRadioId === 'radioFilter3' && saved.filters.managerFilter) {
                manager = saved.filters.managerFilter;
            }
            if (saved.filters.itsystemFilter) {
                itsystemFilter = saved.filters.itsystemFilter;
            }
            if (saved.filters.activeRadioId === 'radioFilter2' && saved.filters.unitFilter) {
                unitFilter = typeof saved.filters.unitFilter === 'string'
                    ? saved.filters.unitFilter.split(',').filter((v) => v !== '')
                    : saved.filters.unitFilter;
            }
        }

        if (saved.showFlags) {
            Object.keys(saved.showFlags).forEach((name) => {
                const checkbox = document.querySelector('.js-column-choice[data-column-name="' + name + '"]');
                if (checkbox) {
                    checkbox.checked = saved.showFlags[name];
                    // ColumnService isn't listening yet at this point, so update the hidden input ourselves.
                    $('#real' + name).val(checkbox.checked);
                }
            });
        }

        if (saved.selectedColumns) {
            columnPanelService.applyColumnsState(saved.selectedColumns);
        }
    }

    formatDanishDate(savedAt) {
        const danishMonths = ['januar', 'februar', 'marts', 'april', 'maj', 'juni',
            'juli', 'august', 'september', 'oktober', 'november', 'december'];
        const date = new Date(savedAt);
        return date.getDate() + '. ' + danishMonths[date.getMonth()] + ' ' + date.getFullYear();
    }

    showBar(savedAt) {
        const bar = document.getElementById('rememberedSettingsBar');
        const text = document.getElementById('rememberedSettingsText');
        if (!bar || !text) {
            return;
        }

        const formatted = savedAt ? this.formatDanishDate(savedAt) : '';
        text.textContent = rememberedSettingsMsg.replace('{0}', formatted);
        bar.style.display = 'block';
    }
}

Date.prototype.addDays = function (days) {
    const dat = new Date(this.valueOf());
    dat.setDate(dat.getDate() + days);
    return dat;
};

function getDates(startDate, stopDate) {
    const dateArray = [];
    let currentDate = startDate;
    while (currentDate <= stopDate) {
        dateArray.push(currentDate);
        currentDate = currentDate.addDays(1);
    }
    return dateArray;
}

// Renamed from the original inline "DatePickerService" to avoid a naming collision
// with the shared /js/service/DatePickerService.js class used elsewhere in the app.
class ReportDatePickerService {
    init() {
        const date2MonthsAgo = new Date(new Date().setMonth(new Date().getMonth() - 2));
        const date6MonthsAgo = new Date(new Date().setMonth(new Date().getMonth() - 6));
        const date12MonthsFromNow = new Date(new Date().setMonth(new Date().getMonth() + 12));

        // Enable last two months
        const enabledDates = getDates(date2MonthsAgo, new Date());

        // Enable 7th, 14th etc. from 4 months prior to 2 months ago
        const olderDates = getDates(date6MonthsAgo, date2MonthsAgo);
        for (let i = 0; i < olderDates.length; i++) {
            if (olderDates[i].getDate() !== 7
                && olderDates[i].getDate() !== 14
                && olderDates[i].getDate() !== 21
                && olderDates[i].getDate() !== 28) {
                continue;
            }

            enabledDates.push(olderDates[i]);
        }

        // Enable future dates
        const futureDates = getDates(new Date(), date12MonthsFromNow);
        for (let i = 0; i < futureDates.length; i++) {
            enabledDates.push(futureDates[i]);
        }

        // Initialize DatePicker
        $('#datePicker').datetimepicker({
            inline: true,
            format: 'YYYY-MM-DD',
            minDate: moment().subtract(6, 'months'),
            maxDate: moment().add(12, 'months'),
            enabledDates: enabledDates,
            defaultDate: new Date()
        });

        // On Change event for DatePicker
        $('#datePicker').on('dp.change', function () {
            const date = $('#datePicker').data('date');

            // sometimes date is undefined. thanks javascript
            if (date) {
                // Remember date
                $('#realDateField').val(date);

                // ajax call to load
                $("#filterOptions").load(url + "/configure/" + date + "?templateId=" + templateId, function () {
                    reportDatePickerService.getOrgUnits(date);
                    filterService.init();
                });
            }
        });

        $('#datePicker').trigger('dp.change');
    }

    getOrgUnits(date) {
        $.ajax({
            contentType: 'application/json',
            url: restUrl + "/getous/" + date,
            method: "POST",
            headers: {
                'X-CSRF-TOKEN': window.token
            },
            success: function (response) {
                allOrgUnits = response;
                window.allOrgUnits = response;

                $('#hierarchy').jstree(true).settings.core.data = response;
                $('#hierarchy').jstree(true).refresh();

                filterService.refreshRadioButtons();
                filterService.refreshCheckbox();
                filterService.units();
            },
            error: defaultErrorHandler
        });
    }
}

class FilterService {
    init() {
        filterService.initOptionsFragment();
        filterService.units();
        filterService.manager();
        filterService.itSystem();
    }

    units() {
        if (unitFilter != null && $("#radioFilter2").prop("disabled") == false) {
            $("#radioFilter2").attr("checked", true).change();

            setTimeout(function () {
                for (let i = 0; i < unitFilter.length; i++) {
                    $('#hierarchy').jstree("select_node", unitFilter[i]);
                }
            }, 100);
        }
    }

    manager() {
        if (manager != "" && manager != null && $("#radioFilter3").prop("disabled") == false) {
            $("#radioFilter3").attr("checked", true).change();
            $("#managerSelect").val(manager);
            $("#managerSelect").selectpicker('refresh');
        }
    }

    itSystem() {
        if (itsystemFilter != null && $("#itsystemCheckbox").prop("disabled") == false) {
            $("#itsystemCheckbox").attr("checked", true).change();
            $("#itSystemSelectPicker").val(itsystemFilter);
            $("#itSystemSelectPicker").selectpicker('refresh');
        }
    }

    refreshRadioButtons() {
        if (allOrgUnits == null || allOrgUnits.length < 1) {
            if ($('#radioFilter2').prop("checked")) {
                $('#radioFilter1').prop("checked", true).change();
                $("#orgUnitToggle").hide();
            }

            $('#radioFilter2').prop('disabled', true);
        } else {
            $('#radioFilter2').prop('disabled', false);
        }
    }

    refreshCheckbox() {
        if (allItSystems == null || allItSystems.length < 1 || templateId == 0) {
            $('#itsystemCheckbox').prop("checked", false);
        }
    }

    initOUTree() {
        this.refreshRadioButtons();
        this.refreshCheckbox();

        $("#hierarchy").jstree({
            core: {
                data: allOrgUnits,
                themes: {
                    icons: false
                }
            },
            search: {
                show_only_matches: true,
                search_callback: function (str, node) {
                    return (node.text.toUpperCase().startsWith(str.toUpperCase()));
                }
            },
            checkbox: {
                three_state: false,
                cascade: "down"
            },
            plugins: [
                "wholerow", "search", "checkbox"
            ]
        });

        // selecting and deselecting in the JSTree
        $("#hierarchy").on("changed.jstree", function () {
            const ous = $('#hierarchy').jstree('get_selected');

            // Remember date
            $('#realOUs').val(ous);
        });

        // searching in the JSTree
        let to = false;
        $('#searchField').keyup(function () {
            if (to) {
                clearTimeout(to);
            }

            to = setTimeout(function () {
                const v = $('#searchField').val();

                $('#hierarchy').jstree(true).search(v);
            }, 400);
        });
    }

    initItSystemPicker() {
        $('select.selectpicker').selectpicker({
            actionsBox: true,
            deselectAllText: "Fravælg alle",
            selectAllText: "Vælg alle",
            iconBase: 'fa',
            tickIcon: 'fa-check text-success'
        });
    }

    onRadioChange(id) {
        // Clean toggles
        $('.radio-toggle').hide();
        $('#hierarchy').jstree('deselect_all');
        $("#managerSelect option:selected").prop("selected", false);

        $('#' + id).show();
    }

    initOptionsFragment() {
        // Toggle filter it system
        $('#itsystemCheckbox').change(function () {
            if (this.checked) {
                $('#itsystemToggle').fadeIn('fast');
                $('#realShowUsers').val(false);
                $('#realShowUsers').parent().hide();
            } else {
                $('#itsystemToggle').fadeOut('fast');
                $('#itSystemSelectPicker').selectpicker('val', '');
                $('#realShowUsers').val($('#UserColumnCheckbox').prop("checked"));
                $('#realShowUsers').parent().show();
            }
        });
        $('#itsystemCheckbox').trigger('change');
        $('input[name="radioFilter"]').change(function () {
            filterService.onRadioChange($(this).data("id"));
        });
        filterService.initItSystemPicker();
        filterService.initOUTree();
    }
}

document.addEventListener("DOMContentLoaded", () => {
    const config = JSON.parse(document.getElementById("report-full-config").textContent);

    url = config.url;
    restUrl = config.restUrl;
    restUrlOus = config.restUrlOus;
    fieldUpdatedMsg = config.fieldUpdatedMsg;
    fieldNotUpdatedMsg = config.fieldNotUpdatedMsg;
    columnSelectedCountMsg = config.columnSelectedCountMsg;
    rememberedSettingsMsg = config.rememberedSettingsMsg;
    manager = config.manager;
    itsystemFilter = config.itsystemFilter;
    unitFilter = config.unitFilter;
    templateId = config.templateId;
    allItSystems = config.allItSystems;
    allOrgUnits = config.allOrgUnits;

    window.token = $("meta[name='_csrf']").attr("content");

    columnPanelService = new ColumnPanelService();
    columnPanelService.init();

    // Overrides manager/itsystemFilter/unitFilter (and pre-checks the show/column checkboxes)
    // from localStorage before the date picker's initial load reads those module-level
    // variables - must run before reportDatePickerService.init().
    reportSettingsMemoryService = new ReportSettingsMemoryService();
    reportSettingsMemoryService.init();

    reportDatePickerService = new ReportDatePickerService();
    reportDatePickerService.init();

    columnService = new ColumnService();
    columnService.init();

    templateService = new TemplateService();
    templateService.init();

    reportService = new ReportService();
    reportService.init();

    filterService = new FilterService();
});
