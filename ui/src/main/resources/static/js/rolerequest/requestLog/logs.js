class RequestLogService{
    #TABLEID = `requestLogTable`
    #table

    constructor () {
        this.#table = this.initTable()
    }

    initTable() {
        return $(`#${this.#TABLEID}`).DataTable({
            pageLength : 25,
            responsive: true,
            autoWidth : false,
            order : [0, "desc"],
            columnDefs : [
				{
					targets: [0],
					render: (data, type, row, meta) => {
						if (type === 'sort') {
							const dateAndTime = data.split(' ')
							const date = dateAndTime[0].split('-')
							return date[2]+date[1]+date[0]+dateAndTime
						} else {
						return data;}
					}
				}
            ],
            language : {
                search : "Søg",
                lengthMenu : "_MENU_ rækker per side",
                info : "Viser _START_ til _END_ af _TOTAL_ rækker",
                zeroRecords : "Ingen data...",
                infoEmpty : "",
                infoFiltered : "(ud af _MAX_ rækker)",
                paginate : {
                    "previous" : "Forrige",
                    "next" : "Næste"
                }
            }
        })
    }
}
