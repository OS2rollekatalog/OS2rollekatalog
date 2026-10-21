// Global DataTables defaults. Page-level options are deep-merged on top of these.
const paginate = JSON.parse(document.getElementById("datatables-defaults-config").textContent);

$.extend(true, $.fn.dataTable.defaults, {
    language: {
        paginate: paginate
    }
});
