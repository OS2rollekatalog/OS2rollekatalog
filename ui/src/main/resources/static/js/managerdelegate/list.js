// Page bootstrap for the manager delegate list view
document.addEventListener('DOMContentLoaded', () => {
    const config = JSON.parse(document.getElementById('managerdelegate-config').textContent)

    // Shared fragment scripts (e.g. datatables) rely on this being global
    window.token = $("meta[name='_csrf']").attr("content")

    const managerDelegateCrudService = new ManagerDelegateCrudService(config.uiUrl, config.restUrl)

    const createButton = document.getElementById('createModalBtn')
    if (createButton) {
        createButton.addEventListener('click', () => managerDelegateCrudService.loadCreateModal())
    }

    // Event delegation for action buttons
    const table = document.getElementById('listTable')
    table.addEventListener('click', (event) => {
        const editBtn = event.target.closest('.editbtn')
        const deleteBtn = event.target.closest('.deletebtn')

        if (editBtn) {
            const managerDelegateId = editBtn.dataset.id
            managerDelegateCrudService.loadEditModal(managerDelegateId)
        } else if (deleteBtn) {
            const managerDelegateId = deleteBtn.dataset.id
            managerDelegateCrudService.deleteManagerDelegate(managerDelegateId)
        }
    })
})
