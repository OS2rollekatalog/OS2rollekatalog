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
let manager;
let itsystemFilter;
let unitFilter;
let templateId;
let allItSystems;
let allOrgUnits;

let reportService;
let templateService;
let columnService;
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
            showKLE: $('#realShowKLE').val(),
            showItSystems: $('#realShowItSystems').val(),
            showInactiveUsers: $('#realShowInactiveUsers').val(),
            itsystemFilter: $('#itSystemSelectPicker').val(),
            managerFilter: $('#managerSelect').val(),
            unitFilter: unitFilterValue,
            showSystemRoles: $('#realShowSystemRoles').val()
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
    manager = config.manager;
    itsystemFilter = config.itsystemFilter;
    unitFilter = config.unitFilter;
    templateId = config.templateId;
    allItSystems = config.allItSystems;
    allOrgUnits = config.allOrgUnits;

    window.token = $("meta[name='_csrf']").attr("content");

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
