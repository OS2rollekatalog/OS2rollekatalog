/**
 * Handles create/edit/delete flows for manager delegates, including
 * loading the modal fragments and wiring up their form controls.
 */
class ManagerDelegateCrudService {
    uiUrl
    restUrl
    networkService
    sweetAlertService
    datePickerService
    select2Service
    indefinitelyState = false

    constructor(uiUrl, restUrl) {
        this.uiUrl = uiUrl
        this.restUrl = restUrl
        this.networkService = new NetworkService()
        this.sweetAlertService = new SweetAlertService()
    }

    async loadCreateModal() {
        const modalContainer = document.getElementById('modalContainer')
        const ok = await this.networkService.GetFragment(`${this.uiUrl}/create`, modalContainer)

        if (ok) {
            this.initModal(false)
            $('#createManagerModal').modal('show')
        }
    }

    async loadEditModal(id) {
        if (!id) {
            return
        }

        const modalContainer = document.getElementById('modalContainer')
        const ok = await this.networkService.GetFragment(`${this.uiUrl}/edit?id=${id}`, modalContainer)

        if (ok) {
            this.initModal(true)
            $('#editManagerModal').modal('show')
        }
    }

    async deleteManagerDelegate(id) {
        this.sweetAlertService.confirm(
            'Slet personlig godkender',
            'Du er ved at slette denne personlige godkender. Vil du fortsætte?',
            'Fortsæt',
            'Fortryd',
            async () => {
                const url = `${this.restUrl}/delete/${id}`
                await this.networkService.Delete(url)
                location.reload()
            }
        )
    }

    initModal(editMode) {
        this.datePickerService = new DatePickerService()
        this.select2Service = new Select2Service()

        this.datePickerService.initDatePicker('fromDateInputContainer')
        this.datePickerService.initDatePicker('toDateInputContainer')
        this.datePickerService.initToAndFromConnection('fromDateInputContainer', 'toDateInputContainer')

        this.select2Service.initServerSideSelect('#delegateInput', `${this.restUrl}/users`)
        this.select2Service.initServerSideSelect('#managerInput', `${this.restUrl}/managers`)

        this.initIndefinitelyCheckbox()

        if (editMode) {
            this.initEditMode()
        } else {
            this.initCreateMode()
        }
    }

    initEditMode() {
        const initialFromValue = document.getElementById('fromDateInput').dataset.val
        const initialToValue = document.getElementById('toDateInput').dataset.val
        const fromValueAsDate = new Date(moment(initialFromValue, 'DD-MM-YYYY'))
        const toValueAsDate = new Date(moment(initialToValue, 'DD-MM-YYYY'))

        this.datePickerService.setDate('fromDateInputContainer', fromValueAsDate)
        if (initialToValue) {
            this.datePickerService.setDate('toDateInputContainer', toValueAsDate)
        }

        this.datePickerService.disable('fromDateInputContainer')

        const editConfirmBtn = document.getElementById('editManagerDelegateBtn')
        editConfirmBtn.addEventListener('click', async () => {
            const data = this.collectModalInputValues()
            if (!data) {
                return
            }
            data.id = document.getElementById('managerDelegateId').value

            const url = `${this.restUrl}/update`
            const ok = await this.networkService.Post(url, data)
            if (ok) {
                location.reload()
            }
        })
    }

    initCreateMode() {
        const today = new Date()
        const todayPlus14 = new Date(new Date().setDate(today.getDate() + 14))

        this.datePickerService.setDate('fromDateInputContainer', today)
        this.datePickerService.setDate('toDateInputContainer', todayPlus14)
        this.datePickerService.setMinDate('fromDateInputContainer', today)

        const createConfirmBtn = document.getElementById('createManagerDelegateBtn')
        createConfirmBtn.addEventListener('click', async () => {
            const data = this.collectModalInputValues()
            if (!data) {
                return
            }

            const url = `${this.restUrl}/create`
            const ok = await this.networkService.Post(url, data)
            if (ok) {
                location.reload()
            }
        })
    }

    initIndefinitelyCheckbox() {
        const indefinitelyCheckbox = document.getElementById('indefinitelyCheckbox')
        indefinitelyCheckbox.addEventListener('click', () => {
            this.indefinitelyState = indefinitelyCheckbox.checked === true
            if (this.indefinitelyState) {
                this.datePickerService.disable('toDateInputContainer')
            } else {
                this.datePickerService.enable('toDateInputContainer')
            }
        })
    }

    collectModalInputValues() {
        const managerUuid = document.getElementById('managerInput').value
        const delegateUuid = document.getElementById('delegateInput').value
        const fromDate = document.getElementById('fromDateInput').value
        const toDate = document.getElementById('toDateInput').value
        const indefinitely = document.getElementById('indefinitelyCheckbox').checked

        const warnings = {
            managerWarning: false,
            delegateWarning: false,
            fromWarning: false,
            toWarning: false
        }

        if (!managerUuid) {
            warnings.managerWarning = true
        }
        if (!delegateUuid) {
            warnings.delegateWarning = true
        }
        if (!fromDate) {
            warnings.fromWarning = true
        }
        if (!indefinitely && !toDate) {
            warnings.toWarning = true
        }

        for (const [key, value] of Object.entries(warnings)) {
            const warningElement = document.getElementById(key)
            warningElement.hidden = !value
        }

        const hasErrors = Object.values(warnings).some((warning) => warning === true)
        if (hasErrors) {
            return null
        }

        return {
            managerUuid,
            delegateUuid,
            fromDate,
            toDate: indefinitely ? null : toDate,
            indefinitely
        }
    }
}
