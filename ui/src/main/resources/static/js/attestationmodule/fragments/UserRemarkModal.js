/**
 * Service for the shared user remark modal (attestation reject/remove flow).
 * This fragment is included on multiple attestation pages, so the class
 * declaration is guarded to avoid "Identifier already declared" errors when
 * the fragment is present more than once on the same page.
 *
 * The instance is exposed as window.userRemarkModalService, since the
 * consuming pages call validateSelection() / getNotApprovedRoles() directly
 * on that global to build their save request.
 */
if (typeof UserRemarkModalService === 'undefined') {
    class UserRemarkModalService {
        constructor() {}

        init() {
            $("#userRemarkError").hide();
            $("#userRemarkField").val("");

            $(".i-checks.remarkCheckbox").iCheck({
                checkboxClass: "icheckbox_square-green",
                radioClass: "iradio_square-green",
            });

            $("#userRemarkModalTable").DataTable({
                "destroy": true,
                "paging": true,
                "ordering": true,
                "order": [
                    [1, "asc"]
                ],
                "autoWidth": false,
                "info": true,
                "stateSave": true,
                "stateDuration": 0,
                "pageLength": 10,
                "language": {
                    "search": "Søg",
                    "lengthMenu": "_MENU_ rækker per side",
                    "info": "Viser _START_ til _END_ af _TOTAL_ rækker",
                    "zeroRecords": "Ingen data...",
                    "infoEmpty": "",
                    "infoFiltered": "(ud af _MAX_ rækker)",
                    "paginate": {
                        "previous": "Forrige",
                        "next": "Næste"
                    }
                }
            });
        }

        getNotApprovedRoles() {
            const notApproved = [];

            // Note: kept as a regular function (not an arrow function) because
            // DataTables' row iterator binds `this` to the row context, which
            // this.row(index) relies on.
            $('#userRemarkModalTable').DataTable().rows().iterator('row', function (context, index) {
                const node = $(this.row(index).node());
                const td = $(node).find("td:first");
                const checkbox = $(td).find('input').first();
                const id = $(checkbox).data("id");
                const name = $(checkbox).data("name");
                const type = $(checkbox).data("type");
                const itSystemName = $(checkbox).data("itsystemname");
                const checked = $(checkbox).iCheck('update')[0].checked;

                if (checked) {
                    const notApprovedObj = {
                        "roleId": id,
                        "roleName": name,
                        "roleType": type,
                        "itSystemName": itSystemName
                    };
                    notApproved.push(notApprovedObj);
                }
            });

            return notApproved;
        }

        validateSelection() {
            const notApproved = this.getNotApprovedRoles();
            if (notApproved.length === 0) {
                $("#userRoleSelectError").show();
                return false;
            }
            $("#userRoleSelectError").hide();
            return true;
        }
    }

    window.UserRemarkModalService = UserRemarkModalService;
}

$(document).ready(function () {
    window.userRemarkModalService = new window.UserRemarkModalService();
    window.userRemarkModalService.init();
});
