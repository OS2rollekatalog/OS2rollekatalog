function ExpandableRoleGroupTableService () {
    this.initUserRoleTable = function() {
        let rgUrTable = $(".roleGroupUserRoleTable").DataTable({
            "destroy" : true,
            "pageLength" : 25,
            "responsive" : true,
            "language" : {
                "search" : "Søg",
                "lengthMenu" : "_MENU_ rækker per side",
                "info" : "Viser _START_ til _END_ af _TOTAL_ rækker",
                "zeroRecords" : "Ingen data...",
                "infoEmpty" : "",
                "infoFiltered" : "(ud af _MAX_ rækker)",
                "paginate" : {
                    "previous" : "Forrige",
                    "next" : "Næste"
                }
            }
        });
    }

    this.openCloseDetails = function(table, e) {
        let tr = e.target.closest('tr');
        let row = table.row(tr);
        if (row.child.isShown()) {
            row.child.hide();
        } else {
            row.child(expandableRoleGroupTableService.formatDetails(row.data(), true)).show();
        }
        $(tr).toggleClass('dt-hasChild');
    }

    this.formatDetails = function ( rowData ) {
        let div = $('<div/>')
        .addClass( 'loading' )
        .text( 'Henter...' );
        $.ajax( {
            url: `${detailsUrl}/${rowData[0]}/userroles` ,
            data: {
                name: rowData.name
            },
            success: function ( data ) {
                div.html( data )
                .removeClass( 'loading' );

                expandableRoleGroupTableService.initUserRoleTable();
            }
        } );

        return div;
    }
}

/**
* Service class meant to provide common custom utility for DataTables.
*/
class DataTableService{

    /**
    * Service class meant to provide common custom utility for DataTables.
    */
    constructor() {
    }


    toggleRowDetailsFromClass(event, table) {
		this.#toggleDetailVisibility(event, table, (rowData) => this.#findDetailView(event))
    }

    toggleRowDetailsCustom(event, table, formattingFunction){
        this.#toggleDetailVisibility(event, table, formattingFunction)
    }

    toggleRowDetailsFromServer(event, table, fullDetailUrl){
        this.#toggleDetailVisibility( event, table,
            (rowData) => this.#fetchDetailViewFromServer(fullDetailUrl, rowData)
        )
    }

    /**
     * toggles the visibility of a rows detail view
     * @param {function} formattingFunction a function returning the detailview of the row, taking the data of the row as the first argument
     */
    async #toggleDetailVisibility(event, table, formattingFunction) {
        const tr = event.target.closest('tr');
        const row = table.row(tr);
        if (row.child.isShown()) {
            row.child.hide();
        } else {
            const detailView = await formattingFunction()
            row.child(detailView).show();
        }
        $(tr).toggleClass('dt-hasChild');
    }

    /**
     * Fetches the detailview for a row from the server, using the url provided
     * @param {string} fullDetailsUrl The url from which to retrieve the detailview
     * @param {object} rowData the table data for the row
     * @returns a div element containing the detailview
     */
    async #fetchDetailViewFromServer(fullDetailsUrl, rowData) {
        let div = document.createElement('div')
        div.classList.add('loading')
        div.textContent = 'Henter'


        const response = await fetch(fullDetailsUrl)

        if (!response.ok) {
            //error handling
            console.error('could not retrieve details for row from '+fullDetailsUrl, response.statusText)
        }

        div.innerHTML = await response.text()
        div.classList.remove('loading')

        return div
    }

    #findDetailView(event) {
        const tr = event.target.closest('tr');
        const detailView = tr.querySelector('.detailview')
        const div = document.createElement('div')
        div.innerHTML = detailView.innerHTML
        div.classList.add('col-sm-12')
        return div
    }
}



class NetworkService {
    XCSRFToken
    loadingMessage
    loadingClass
    #loadingElement

    /**
    * Creates a new NetworkService instance
    * @param {string} XCSRFToken the X-CSRF token to use for requests. If not defined, will attempt to use any accessable variable called 'token'
    * @param {string} loadingMessage The message shown while fragment loads
    * @param {string} loadingClass the class applied to the loading div while a fragment loads
    */
    constructor(XCSRFToken = token, loadingMessage = 'Henter...', loadingClass = 'loading'){
        this.XCSRFToken = XCSRFToken
        this.loadingMessage = loadingMessage
        this.loadingClass = loadingClass

        this.#loadingElement = document.createElement('div')
        this.#loadingElement.classList.add(this.loadingClass)
        this.#loadingElement.textContent = this.loadingMessage


    }

    /**
    * Fetches a fragment from the provided url and inserts it into the provided container, meanwhile showing a loading message
    * @param {string} url
    * @param {HTMLElement} containerElement
    * @returns the response ok status
    */
    async GetFragment(url, containerElement) {
        if (!this.XCSRFToken || !token) {
            throw new Error(`X-CSRF token not defined. NetworkService methods requires the token variable to be defined in the document`)
        }
        if (!containerElement) {
            throw new Error('No container element defined. A container must be provided to contain the fragment')
        }

        containerElement.appendChild(this.#loadingElement)

        const response = await fetch (url, {
            headers: {
                'X-CSRF-TOKEN': this.XCSRFToken || token,
            }
        })

        if (!response.ok) {
            throw new Error(`Error when getting url: ${url}`)
        }

        containerElement.innerHTML = await response.text()

        return response.ok
    }

    /**
    * Fetch GET's from a url, parsing the result from json to JS object
    * @param {string} url
    * @returns the response as JS object
    */
    async Get(url) {
        if (!this.XCSRFToken || token) {
            throw new Error(`X-CSRF token not defined. NetworkService methods requires the token variable to be defined in the document`)
        }

        const response = await fetch (url, {
            headers: {
                'X-CSRF-TOKEN': this.XCSRFToken || token,
            }
        })

        if (!response.ok) {
            throw new Error(`Error when getting url: ${url}`)
        }

        return await response.json()
    }

    /**
    * Fetch POST's data to the provided url as json, returning the parsed response as JS object
    * @param {string} url
    * @param {Object} data
    * @returns response as JS object
    */
    async Post (url, data) {
        if (!this.XCSRFToken || token) {
            throw new Error(`X-CSRF token not defined. NetworkService methods requires the token variable to be defined in the document`)
        }

        const response = await fetch (url, {
            method: 'POST',
            headers: {
                'X-CSRF-TOKEN': this.XCSRFToken || token,
                "Content-Type": "application/json",
            },
            body: JSON.stringify(data)
        })

        if (!response.ok) {
            throw new Error(`Error when posting to url: ${url}`)
        }

        return await response.json()
    }

    /**
    * Fetch PUT's the data to the provided url as json, returning the parsed response as JS object
    * @param {string} url
    * @param {Object} data
    * @returns response as JS object
    */
    async Put (url, data){

        if (!this.XCSRFToken || token) {
            throw new Error(`X-CSRF token not defined. NetworkService methods requires the token variable to be defined in the document`)
        }

        const response = await fetch (url, {
            method: 'PUT',
            headers: {
                'X-CSRF-TOKEN': this.XCSRFToken || token,
                "Content-Type": "application/json",
            },
            body: JSON.stringify(data)
        })

        if (!response.ok) {
            throw new Error(`Error when posting to url: ${url}`)
        }

        return await response.json()
    }

    /**
    * Fetch DELETE's to the provided url, returning the parsed response as JS object
    * @returns response as JS object
    */
    async Delete (url) {
        if (!this.XCSRFToken || token) {
            throw new Error(`X-CSRF token not defined. NetworkService methods requires the token variable to be defined in the document`)
        }

        const response = await fetch (url, {
            method: 'DELETE',
            headers: {
                'X-CSRF-TOKEN': this.XCSRFToken || token,
            }
        })

        if (!response.ok) {
            throw new Error(`Error when getting url: ${url}`)
        }

        return await response.json()
    }

}

class TabService  {
    nameSpace

    constructor(nameSpace){
        this.nameSpace = nameSpace
    }

    rememberTab (href) {
        sessionStorage.setItem(this.nameSpace+'_active_tab', href)
    }

    restoreTab () {
        const href = sessionStorage.getItem(this.nameSpace+'_active_tab')
        if (href) {
        	$('a[href="#' + href + '"]').tab('show');
        }
    }
}
