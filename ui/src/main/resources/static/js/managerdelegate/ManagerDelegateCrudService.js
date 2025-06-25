let datePickerService, select2Service, networkService;
let indefinitelyState = false;

class ManagerDelegateCrudService {
    datePickerService
    select2Service;
    indefinitelyState = false;

    constructor() {
        networkService = new NetworkService()
    }

    async loadCreateModal() {
        const modalContainer = document.getElementById('modalContainer')

        const ok = await networkService.GetFragment(`${uiURL}/create`, modalContainer)

        if (ok) {
            this.initModal(false)
            $('#createManagerModal').modal('show')
        }
    }

    async loadEditModal(id) {
        if (!id) {
            return;
        }
        const modalContainer = document.getElementById('modalContainer')

        const ok = await networkService.GetFragment(`${uiURL}/edit?id=${id}`, modalContainer)

        if (ok) {
            this.initModal(true)
            $('#editManagerModal').modal('show')
        }
    }

    initModal(editMode) {
        datePickerService = new DatePickerService()
        select2Service = new Select2Service()
        datePickerService.initDatePicker('fromDateInputContainer')
        datePickerService.initDatePicker('toDateInputContainer')
        datePickerService.initToAndFromConnection('fromDateInputContainer', 'toDateInputContainer')

        select2Service.initServerSideSelect('#delegateInput', `${restURL}/users`)
        select2Service.initServerSideSelect('#managerInput', `${restURL}/managers`)

        this.#initIndefinitelyCheckbox()

        if (editMode) {
            //Initialization specific to editmode
            const initialFromValue = document.getElementById('fromDateInput').dataset.val
            const initialToValue = document.getElementById('toDateInput').dataset.val
            const fromValueAsDate =new Date(moment(initialFromValue, 'DD-MM-YYYY'))
            const toValueAsDate = new Date(moment(initialToValue, 'DD-MM-YYYY'))

            datePickerService.setDate('fromDateInputContainer', fromValueAsDate)
            if (initialToValue) {
                datePickerService.setDate('toDateInputContainer',toValueAsDate)
            }

            datePickerService.disable('fromDateInputContainer')

            const editConfirmBtn = document.getElementById('editManagerDelegateBtn')
            editConfirmBtn.addEventListener('click', async ()=> {
                const data = this.collectModalInputValues()
                data.id = document.getElementById('managerDelegateId').value
                if (data && data.id) {
                    const url = `${restURL}/update`
                    const ok = await networkService.Post(url, data)
                    if (ok) {
                        // $('#editManagerModal').modal('hide')
                        location.reload()
                    }
                }
            })
        } else {
            //Initialization specific to create mode
            const today = new Date()
            const todayPlus14 = new Date(new Date().setDate(today.getDate()+14))
            datePickerService.setDate('fromDateInputContainer', today)
            datePickerService.setDate('toDateInputContainer', todayPlus14)
            datePickerService.setMinDate('fromDateInputContainer', today)

            const createConfirmBtn = document.getElementById('createManagerDelegateBtn')
            createConfirmBtn.addEventListener('click', async ()=> {
                const data = this.collectModalInputValues()
                if (data) {
                    const url = `${restURL}/create`
                    const ok = await networkService.Post(url, data)
                    if (ok) {
                        // $('#createManagerModal').modal('hide')
                        location.reload()
                    }
                }
            })
        }
    }

    #initIndefinitelyCheckbox() {
        const indefinitelyCheckbox = document.getElementById('indefinitelyCheckbox')
        indefinitelyCheckbox.addEventListener('click', () => {
            indefinitelyState = indefinitelyCheckbox.checked === true
            if (indefinitelyState) {
                datePickerService.disable('toDateInputContainer')
            } else {
                datePickerService.enable('toDateInputContainer')
            }
        })
    }

    collectModalInputValues() {
        //gather values
        const managerUuidElement = document.getElementById('managerInput')
        const managerUuid = managerUuidElement.value
        const delegateUuidElement = document.getElementById('delegateInput')
        const delegateUuid = delegateUuidElement.value
        const fromDateElement = document.getElementById('fromDateInput')
        const fromDate = fromDateElement.value
        const toDateElement = document.getElementById('toDateInput')
        const toDate = toDateElement.value
        const indefinitely = document.getElementById('indefinitelyCheckbox').checked

        //validation
         const warnings = {
            'managerWarning': false,
            'delegateWarning':false,
            'fromWarning':false,
            'toWarning':false,
         }

        if (!managerUuid) {
            warnings['managerWarning'] = true
        }
        if (!delegateUuid) {
            warnings['delegateWarning'] = true
        }
        if(!fromDate) {
            warnings['fromWarning'] = true
        }
        if(indefinitely && !toDate) {
            warnings['toWarning'] = true
        }

        for (let [key, value] of Object.entries(warnings)) {
            //set warning visibility
            const warningElement = document.getElementById(key)
            warningElement.hidden = !value;
        }

        const errors = Object.values(warnings).filter(e => e === true).length>0
        if (errors) {
            //returns null if any validation errors is present
            return null;
        }

        return {
            managerUuid:managerUuid,
            delegateUuid:delegateUuid,
            fromDate:fromDate,
            toDate: indefinitely ? null : toDate,
            indefinitely:indefinitely
        }
    }

    async deleteManagerDelegate(id)  {

        swal({
                html : true,
                title : "Slet personlig godkender",
                text : "Du er ved at slette denne personlige godkender. Vil du fortsætte?",
                type : "warning",
                showCancelButton : true,
                confirmButtonColor : "#DD6B55",
                confirmButtonText : "Fortsæt",
                cancelButtonText : "Fortryd",
                closeOnConfirm : true,
                closeOnCancel : true
            },
            async function(isConfirm) {
                if (isConfirm) {
                    const url = `${restURL}/delete/${id}`
                    await networkService.Delete(url)
                    location.reload()
                }
            });


    }
}