$(document).ready(function () {
    const stars = '********';
    const table = document.getElementById('listTable');
    const deleteURL = table.dataset.deleteUrl;
    const deleteTitle = table.dataset.deleteTitle;
    const deleteText = table.dataset.deleteText;
    const deleteButtonConfirm = table.dataset.deleteConfirm;
    const deleteButtonCancel = table.dataset.deleteCancel;

    function handleDelete(clientId) {
        swal({
            html: true,
            title: deleteTitle,
            text: deleteText,
            type: "warning",
            showCancelButton: true,
            confirmButtonColor: "#DD6B55",
            confirmButtonText: deleteButtonConfirm,
            cancelButtonText: deleteButtonCancel,
            closeOnConfirm: true,
            closeOnCancel: true
        },
        function (isConfirm) {
            if (isConfirm) {
                window.location = deleteURL + '/' + clientId;
            }
        });
    }

    function togglePassword(clientId) {
        const pwdElement = $("#pwd-" + clientId);

        if (pwdElement.text() === stars) {
            pwdElement.text(pwdElement.data("key"));
        } else {
            pwdElement.text(stars);
        }
    }

    // Event delegation for toggle password
    $(table).on("click", ".js-toggle-password", function () {
        const clientId = $(this).data("client-id");
        togglePassword(clientId);
    });

    // Event delegation for delete
    $(table).on("click", ".js-delete-client", function (e) {
        e.preventDefault();
        const clientId = $(this).data("client-id");
        handleDelete(clientId);
    });
});
