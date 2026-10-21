/**
 * Adapter for eonasdan bootstrap-datetimepicker, loaded as a classic script
 * alongside jQuery. Migrate along with modalService.js's bsModalAdapter when the
 * app drops jQuery.
 */
const dateTimePickerAdapter = {
	create(el, options) {
		$(el).datetimepicker(options);
	},
	instance(el) {
		return $(el).data('DateTimePicker');
	},
	onChange(el, handler) {
		$(el).on('dp.change', event => handler(event.date));
	},
	destroy(el) {
		$(el).off('dp.change');
		this.instance(el)?.destroy();
	},
};

const DISPLAY_FORMAT = 'DD-MM-YYYY';

/** Parseable by LocalDate without a formatter. */
const WIRE_FORMAT = 'YYYY-MM-DD';

const BASE_OPTIONS = {
	format: DISPLAY_FORMAT,
	locale: 'da',
	allowInputToggle: true,
	showClear: true,
	icons: {
		time: 'fa fa-clock-o',
		date: 'fa fa-calendar',
		up: 'fa fa-chevron-up',
		down: 'fa fa-chevron-down',
		previous: 'fa fa-chevron-left',
		next: 'fa fa-chevron-right',
		today: 'fa fa-calendar-check-o',
		clear: 'fa fa-trash',
		close: 'fa fa-times',
	},
};

function resolveElement(root, selector) {
	const el = root.querySelector(selector);
	if (!el) {
		throw new Error(`dateRangePicker: no element matching ${selector}`);
	}

	return el;
}

function startOfToday() {
	const today = new Date();
	today.setHours(0, 0, 0, 0);

	return today;
}

// the picker enforces minDate/maxDate to the millisecond but greys out whole
// days, so a bound must span the day or its own boundary day is unselectable
function dayStart(date) {
	return moment(date).startOf('day');
}

function dayEnd(date) {
	return moment(date).endOf('day');
}

/**
 * Wires a from/to pair of date pickers as one range, keeping each side's bound in
 * step with the other so an end date can never precede its start.
 * Dates are displayed in Danish format but always read back as ISO yyyy-MM-dd.
 * @param {HTMLElement} root element containing both pickers; bounds every lookup
 * @param {Object} options
 * @param {string} options.start selector for the start picker's .input-group.date
 * @param {string} options.end selector for the end picker's .input-group.date
 * @param {?string} [options.startDate] initial start date, ISO yyyy-MM-dd; empty when omitted
 * @param {?string} [options.stopDate] initial end date, ISO yyyy-MM-dd; empty when omitted
 * @param {boolean} [options.allowPast] whether a date before today may be picked; an
 *   existing assignment that already started needs this to show its own start date
 * @returns {{startDate: function(): ?string, stopDate: function(): ?string,
 *   setRange: function(?string, ?string): void, destroy: function(): void}}
 */
export function initDateRangePicker(root, {start, end, startDate = null, stopDate = null, allowPast = false} = {}) {
	const startEl = resolveElement(root, start);
	const endEl = resolveElement(root, end);
	const floor = allowPast ? false : startOfToday();

	dateTimePickerAdapter.create(startEl, {...BASE_OPTIONS, minDate: floor});
	// useCurrent would otherwise stamp today onto the end picker as soon as a minDate lands
	dateTimePickerAdapter.create(endEl, {...BASE_OPTIONS, minDate: floor, useCurrent: false});

	dateTimePickerAdapter.onChange(startEl, date => {
		dateTimePickerAdapter.instance(endEl)?.minDate(date ? dayStart(date) : floor);
	});

	dateTimePickerAdapter.onChange(endEl, date => {
		dateTimePickerAdapter.instance(startEl)?.maxDate(date ? dayEnd(date) : false);
	});

	// a stored date may predate the floor, and the picker refuses a value below minDate
	function apply(el, isoDate) {
		const picker = dateTimePickerAdapter.instance(el);
		if (!picker) {
			return;
		}

		if (!isoDate) {
			picker.clear();

			return;
		}

		const date = moment(isoDate, WIRE_FORMAT);
		const predatesFloor = floor && date.isBefore(floor);

		if (predatesFloor) {
			picker.minDate(false);
		}

		picker.date(date);

		if (predatesFloor) {
			picker.minDate(floor);
		}
	}

	function valueOf(el) {
		return dateTimePickerAdapter.instance(el)?.date()?.format(WIRE_FORMAT) ?? null;
	}

	function setRange(from, to) {
		// drop the previous range's bounds first, or they reject the incoming one
		dateTimePickerAdapter.instance(startEl)?.maxDate(false);
		dateTimePickerAdapter.instance(endEl)?.minDate(floor);

		// the end picker's floor follows the start date, so that has to land first
		apply(startEl, from);
		apply(endEl, to);
	}

	setRange(startDate, stopDate);

	return {
		startDate: () => valueOf(startEl),
		stopDate: () => valueOf(endEl),
		setRange,
		destroy() {
			dateTimePickerAdapter.destroy(startEl);
			dateTimePickerAdapter.destroy(endEl);
		},
	};
}
