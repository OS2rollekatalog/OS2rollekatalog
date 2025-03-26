
class DatatableService {
    defaultLanguageOptions = {
        "search":       "Søg",
        "lengthMenu":   "_MENU_ rækker per side",
        "info":         "Viser _START_ til _END_ af _TOTAL_ rækker",
        "zeroRecords":  "Ingen data...",
        "infoEmpty":    "",
        "infoFiltered": "(ud af _MAX_ rækker)",
        "paginate": {
            "next": "Næste",
            "previous": "Forrige"
        }
    }

    constructor () {
    }

    /**
     * Constructs an object containing the default options for a serverside datatable
     * @param {*} endointURL URL providing the data for the table
     * @param {*} languageOptionsObject An optional datatable Language object to be used instead of the default
     * @returns
     */
    getDefaultOptions_Serverside(endpointURL, languageOptionsObject = this.defaultLanguageOptions) {
        return {
            destroy: true,
            searchDelay: 1000,
            'ajax': {
                'contentType': 'application/json',
                'url': endpointURL,
                'type': 'POST',
                headers: {
                    'X-CSRF-TOKEN': token
                },
                'data': function(d) {
                    return JSON.stringify(d);
                }
            },
            'serverSide' : true,
            'paging':    true,
            'ordering':  true,
            'stateSave': true,
            'info':      true,
            'pageLength': 25,
            "order": [ [ 0, "desc" ] ],
            'language': languageOptionsObject || this.defaultLanguageOptions
        }
    }

    /**
     * Initializes a datatable using serverside data, with default options, then returns the table
     * @param {*} tableElementSelector Selector for the table element used for the table
     * @param {*} endpointUrl URL providing the data for the table
     * @param {*} columnsArray Optional "columns" definition for the table. Must be provided if no columnDefs is provided
     * @param {*} columnDefsArray Optional "columnDefs" definition for the table. Must be provided if no columns is provided
     * @returns the datatable instance
     */
    initDefaultServersideTable(tableElementSelector, endpointUrl, columnDefsArray = null, columnsArray = null, ) {

        const options = this.getDefaultOptions_Serverside(endpointUrl)

        if (columnsArray) {
            options.columns = columnsArray
        }

        if (columnDefsArray) {
            options.columnDefs = columnDefsArray
        }

        return $(`${tableElementSelector}`).DataTable(options)
    }

}