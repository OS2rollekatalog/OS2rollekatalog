function UserRoleService() {

    this.init = function() {
        let rgTable = $(`#userRoleTable`).DataTable({
            "pageLength" : 25,
            "responsive" : true,
            "autoWidth" : false,
            "columnDefs" : [{ "orderable" : false, "targets" : [4] }],
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

    this.onUserRoleRemoval =(userRoleId) =>{
        const removalRequestDTO = new RemovalRequestDTO(userRoleId, null, '')
        if (reasonRequirement === 'NONE') {
            requestService.removeRequest(removalRequestDTO)
        } else {
            requestService.openRequestRemovalModal(removalRequestDTO)
        }
    }
}

function RoleGroupService() {

    this.init = function() {
        let rgTable = $(`#roleGroupTable`).DataTable({
            "pageLength" : 25,
            "responsive" : true,
            "autoWidth" : false,
            "columnDefs" : [{ "orderable" : false, "targets" : [1,5] }],
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
        rgTable.on('click', 'td.dt-control', e => expandableRoleGroupTableService.openCloseDetails(rgTable, e));
    }

    this.onRoleGroupRemoval = (roleGroupId) =>{
        const removalRequestDTO = new RemovalRequestDTO(null, roleGroupId, '')
        console.log(reasonRequirement)
        if (reasonRequirement === 'NONE') {
            requestService.removeRequest(removalRequestDTO)
        } else {
            requestService.openRequestRemovalModal(removalRequestDTO)
        }
    }

}

class RequestService {
    currentRemovalRequestDTO = null
    removalModalId = 'request-removal-modal'

    constructor () {
    }

    async removeRequest(removalRequestDTO) {
        const url = `${restUrl}/remove`
        await fetch (url, {
            method: 'POST',
            headers: {
                'X-CSRF-TOKEN': token,
                "Content-Type": "application/json",
            },
            body: JSON.stringify(removalRequestDTO)
        })

        location.reload()
    }

    openRequestRemovalModal(removalRequestDTO){
        this.currentRemovalRequestDTO = removalRequestDTO
        $(`#${this.removalModalId}`).modal({
            backdrop: 'static',
            keyboard: false
        })
    }

    onRemovalModalConfirm(){
        const reasonTextField = document.getElementById('removal_reason')
        const reasonGiven = reasonTextField.value || ''
        if (reasonRequirement === 'OBLIGATORY' && !reasonGiven) {
            const validationError = document.getElementById('reasonEmptyError')
            validationError.hidden=false;
            reasonTextField.classList.add('is-invalid')
        } else  {
            this.currentRemovalRequestDTO.reason = reasonGiven
            this.removeRequest(this.currentRemovalRequestDTO)
            this.currentRemovalRequestDTO = null;
        }
    }

    async onRequestCancel (requestId) {
        swal({
            html: true,
            title : "Er du sikker?",
            text : "Dette vil annullere anmodningen, og kan ikke fortrydes.",
            type : "warning",
            showCancelButton : true,
            confirmButtonColor : "#DD6B55",
            confirmButtonText : "Ja, annuller",
            cancelButtonText : "Fortryd",
            closeOnConfirm : true,
            closeOnCancel : true
        },
        async function (confirmed) {
            if (confirmed) {
                const url = `${restUrl}/${requestId}/cancel`
                await fetch (url, {
                    method: 'DELETE',
                    headers: {
                        'X-CSRF-TOKEN': token,
                    }
                })

                location.reload()
            }
        });
    }

}

class RemovalRequestDTO {
    userRoleAssignmentId
    roleGroupAssignmentId
    reason

    constructor(
        userRoleAssignmentId,
        roleGroupAssignmentId,
        reason
    ){
        this.userRoleAssignmentId = userRoleAssignmentId
        this.roleGroupAssignmentId = roleGroupAssignmentId
        this.reason = reason
    }
}
