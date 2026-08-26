/**
 * Handles the "my roles" page: loads the history tab's DataTable
 * lazily the first time the tab is shown.
 */
class MyListService {
    constructor(config) {
        this.historyUrl = config.historyUrl;
        this.searchTxt = config.searchTxt;
        this.dropdownTxt = config.dropdownTxt;
        this.infoDefaultTxt = config.infoDefaultTxt;
        this.infoEmptyTxt = config.infoEmptyTxt;
        this.infoFilteredTxt = config.infoFilteredTxt;
        this.historyLoaded = false;
    }

    init() {
        $('a[data-toggle="tab"]').on('shown.bs.tab', (event) => {
            const target = $(event.target).attr("href");
            if (target === '#history_menu' && !this.historyLoaded) {
                this.historyLoaded = true;
                this.initHistoryTable();
            }
        });
    }

    initHistoryTable() {
        $('#listTable3').DataTable({
            ajax: this.historyUrl,
            pageLength: 100,
            columns: [
                { data: "timestamp" },
                { data: "eventType" },
                { data: "roleName" },
                { data: "systemName" },
                { data: "username" }
            ],
            order: [
                [0, "desc"]
            ],
            language: {
                search: this.searchTxt,
                lengthMenu: this.dropdownTxt,
                info: this.infoDefaultTxt,
                zeroRecords: this.infoEmptyTxt,
                infoEmpty: "",
                infoFiltered: this.infoFilteredTxt
            }
        });
    }
}

document.addEventListener('DOMContentLoaded', () => {
    const config = JSON.parse(document.getElementById('my-list-config').textContent);
    const myListService = new MyListService(config);
    myListService.init();
});
