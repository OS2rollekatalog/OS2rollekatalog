/**
 * Initializes all date pickers shared across the application (user roles,
 * role groups, OU assignments, and the attestation settings page), and links
 * each start/stop pair so the stop picker's min date follows the start
 * picker, and vice versa for a sensible default.
 */
const datePickerService = new DatePickerService();

const icons = {
    time: "fa fa-clock-o",
    date: "fa fa-calendar",
    up: "fa fa-chevron-up",
    down: "fa fa-chevron-down",
    previous: "fa fa-chevron-left",
    next: "fa fa-chevron-right",
    today: "fa fa-calendar-check-o",
    clear: "fa fa-trash",
    close: "fa fa-times"
};

const defaultStartDatePickerOptions = {
    format: "YYYY-MM-DD",
    locale: "da",
    allowInputToggle: true,
    showClear: true,
    icons,
    minDate: new Date(new Date(new Date().toISOString().substr(0, 10)).getTime() - (4 * 60 * 60 * 1000))
};

const attestationDatePickerOptions = {
    format: "YYYY-MM-DD",
    locale: "da",
    allowInputToggle: true,
    showClear: true,
    icons
};

const defaultStopDatePickerOptions = {
    format: "YYYY-MM-DD",
    locale: "da",
    allowInputToggle: true,
    showClear: true,
    icons,
    minDate: new Date(),
    useCurrent: false // Important! See issue #1075
};

document.addEventListener("DOMContentLoaded", () => {
    initDatePickers();
    initDatePickerPairs();
});

function initDatePickers() {
    datePickerService.initDatePicker("startDatePicker", defaultStartDatePickerOptions);
    datePickerService.initDatePicker("stopDatePicker", defaultStopDatePickerOptions);
    datePickerService.initDatePicker("groupStartDatePicker", defaultStartDatePickerOptions);
    datePickerService.initDatePicker("groupStopDatePicker", defaultStopDatePickerOptions);
    datePickerService.initDatePicker("startDatePickerOU", defaultStartDatePickerOptions);
    datePickerService.initDatePicker("stopDatePickerOU", defaultStopDatePickerOptions);
    // Edit dialog for userRoles and RoleGroup assignments in USERS
    datePickerService.initDatePicker("startDatePickerEdit", defaultStartDatePickerOptions);
    datePickerService.initDatePicker("stopDatePickerEdit", defaultStopDatePickerOptions);
    // Edit dialog for userRoles and RoleGroup assignments in ORGUNITS
    datePickerService.initDatePicker("startDatePickerEditOU", defaultStartDatePickerOptions);
    datePickerService.initDatePicker("stopDatePickerEditOU", defaultStopDatePickerOptions);
    // Settings -> attestation -> first attestation date
    datePickerService.initDatePicker("attestationStartDate", attestationDatePickerOptions);
}

function initDatePickerPairs() {
    linkStartStopDatePickers("startDatePicker", "stopDatePicker");
    linkStartStopDatePickers("groupStartDatePicker", "groupStopDatePicker");
    linkStartStopDatePickers("startDatePickerOU", "stopDatePickerOU");
    linkStartStopDatePickers("startDatePickerEdit", "stopDatePickerEdit");
    linkStartStopDatePickers("startDatePickerEditOU", "stopDatePickerEditOU");
}

/**
 * Keeps a start/stop date picker pair in sync: the stop picker's minimum date
 * follows the start picker's selected date (defaulting to today), and the
 * start picker defaults to today if it has no value once the stop picker changes.
 */
function linkStartStopDatePickers(startId, stopId) {
    const startElement = $(`#${startId}`);
    const stopElement = $(`#${stopId}`);

    startElement.on("dp.change", (event) => {
        const minDate = event.date ? new Date(event.date) : new Date();
        stopElement.data("DateTimePicker").minDate(minDate);

        if (stopElement.data("date") && stopElement.data("DateTimePicker").date().toDate() < minDate) {
            stopElement.data("DateTimePicker").date(minDate);
        }
    });

    stopElement.on("dp.change", () => {
        if (!startElement.data("date")) {
            startElement.data("DateTimePicker").date(new Date());
        }
    });
}

// Exposed so pages can (re-)initialize date pickers inside ajax-loaded modal
// content, since this file's own DOMContentLoaded listener only runs once,
// before such content exists in the DOM.
window.datePickerService = datePickerService;

window.initGroupModalDatePickers = function () {
    datePickerService.initDatePicker("groupStartDatePicker", defaultStartDatePickerOptions);
    datePickerService.initDatePicker("groupStopDatePicker", defaultStopDatePickerOptions);
    linkStartStopDatePickers("groupStartDatePicker", "groupStopDatePicker");
};

window.initOuModalDatePickers = function () {
    datePickerService.initDatePicker("startDatePickerOU", defaultStartDatePickerOptions);
    datePickerService.initDatePicker("stopDatePickerOU", defaultStopDatePickerOptions);
    linkStartStopDatePickers("startDatePickerOU", "stopDatePickerOU");
};

window.initOuEditModalDatePickers = function () {
    datePickerService.initDatePicker("startDatePickerEditOU", defaultStartDatePickerOptions);
    datePickerService.initDatePicker("stopDatePickerEditOU", defaultStopDatePickerOptions);
    linkStartStopDatePickers("startDatePickerEditOU", "stopDatePickerEditOU");
};

window.initUserRoleModalDatePickers = function () {
    datePickerService.initDatePicker("startDatePicker", defaultStartDatePickerOptions);
    datePickerService.initDatePicker("stopDatePicker", defaultStopDatePickerOptions);
    linkStartStopDatePickers("startDatePicker", "stopDatePicker");
};

window.initBulkAssignModalDatePickers = function () {
    datePickerService.initDatePicker("startDatePickerBulk", defaultStartDatePickerOptions);
    datePickerService.initDatePicker("stopDatePickerBulk", defaultStopDatePickerOptions);
    linkStartStopDatePickers("startDatePickerBulk", "stopDatePickerBulk");
};
