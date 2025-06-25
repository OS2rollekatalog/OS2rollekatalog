

class DatePickerService {
    defaultConfig = {
        locale: 'da',
        format: 'DD-MM-YYYY',
        icons: {
            time: "fa fa-clock-o",
            date: "fa fa-calendar",
            up: "fa fa-arrow-up",
            down: "fa fa-arrow-down"
        },
        calendarWeeks: true
    }

    constructor(){

    }

    initDatePicker(id, customConfig){
            $(`#${id}`).datetimepicker(customConfig ? customConfig : this.defaultConfig);
    }

    setDate(id, date){
        $(`#${id}`).data("DateTimePicker").date(date)
    }

    initToAndFromConnection(fromId, toID) {
        const from = $(`#${fromId}`)
        const to = $(`#${toID}`)

        from.on("dp.change", function (e) {
            to.data("DateTimePicker").minDate(e.date);
        });
        to.on("dp.change", function (e) {
            from.data("DateTimePicker").maxDate(e.date);
        });
    }

    disable(id){
        $(`#${id}`).data("DateTimePicker").disable()
    }

    enable(id){
        $(`#${id}`).data("DateTimePicker").enable()
    }

    setMinDate(id, date) {
        $(`#${id}`).data("DateTimePicker").minDate(date);
    }

    setMaxDate(id, date) {
        $(`#${id}`).data("DateTimePicker").maxDate(date);
    }
}