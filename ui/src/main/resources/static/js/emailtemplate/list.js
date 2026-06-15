document.addEventListener('DOMContentLoaded', function () {
    const config = document.getElementById('emailTemplateConfig').dataset;
    const restUrl = config.restUrl;
    const msgToggleSuccess = config.msgToggleSuccess;
    const msgToggleFail = config.msgToggleFail;
    const msgTestFail = config.msgTestFail;
    const token = document.querySelector("meta[name='_csrf']").getAttribute("content");
    let currentPreviewId = null;

    const dataTableConfig = {
        "pageLength": 25,
        "order": [[0, "asc"], [1, "asc"]],
        "language": {
            "search": "Søg:",
            "lengthMenu": "_MENU_ rækker per side",
            "info": "Viser _START_ til _END_ af _TOTAL_ rækker",
            "infoEmpty": "Ingen rækker at vise",
            "infoFiltered": "(filtreret fra _MAX_ rækker)",
            "paginate": {
                "first": "Første",
                "last": "Sidste",
                "next": "Næste",
                "previous": "Forrige"
            }
        },
        "columnDefs": [
            { "orderable": false, "targets": -1 },
            { "width": "150px", "targets": 0 },
            { "width": "80px", "targets": 3 },
            { "width": "130px", "targets": -1 }
        ]
    };

    document.querySelectorAll('.templateTable').forEach(function (table) {
        $(table).DataTable(dataTableConfig);
    });

    // Preview button handler
    $(document).on('click', '.btnPreview', function (e) {
        e.preventDefault();
        const templateId = $(this).data('id');
        currentPreviewId = templateId;

        $.ajax({
            url: restUrl + '/' + templateId,
            method: 'GET',
            headers: { 'X-CSRF-TOKEN': token }
        }).done(function (data) {
            $('#previewTemplateName').text(data.templateTypeName || '');
            $('#previewTitle').text(data.title || '');
            $('#previewContent').html(data.message || '');

            if (data.notes) {
                $('#previewNotes').text(data.notes);
                $('#previewNotesGroup').show();
            } else {
                $('#previewNotesGroup').hide();
            }

            $('#previewModal').modal('show');
        }).fail(function () {
            $.notify({ message: msgTestFail }, { status: 'danger', autoHideDelay: 3000 });
        });
    });

    // Toggle active/inactive handler
    $(document).on('click', '.btnToggle', function (e) {
        e.preventDefault();
        const btn = $(this);
        const templateId = btn.data('id');

        $.ajax({
            url: restUrl + '/' + templateId + '/toggle',
            method: 'POST',
            headers: {
                'content-type': 'application/json',
                'X-CSRF-TOKEN': token
            }
        }).done(function (data) {
            const isNowEnabled = (data === 'true');
            const icon = btn.find('em');
            const activeCell = btn.closest('tr').find('td:eq(3)');

            icon.toggleClass('fa-toggle-on', isNowEnabled);
            icon.toggleClass('fa-toggle-off', !isNowEnabled);

            if (isNowEnabled) {
                activeCell.html('<i class="fa fa-check text-success"></i>');
            } else {
                activeCell.html('');
            }
            activeCell.attr('data-order', isNowEnabled);

            $.notify({ message: msgToggleSuccess }, { status: 'success', autoHideDelay: 2000 });
        }).fail(function () {
            $.notify({ message: msgToggleFail }, { status: 'danger', autoHideDelay: 3000 });
        });
    });

    // Send test email from preview modal
    document.getElementById('btnSendTestEmail').addEventListener('click', function () {
        if (!currentPreviewId) {
            return;
        }

        const btn = $(this);
        btn.prop('disabled', true);

        $.ajax({
            url: restUrl + '/' + currentPreviewId + '/test',
            method: 'POST',
            headers: {
                'content-type': 'application/json',
                'X-CSRF-TOKEN': token
            }
        }).done(function (data) {
            $.notify({ message: data }, { status: 'success', autoHideDelay: 3000 });
        }).fail(function (xhr) {
            const msg = xhr.responseText || msgTestFail;
            $.notify({ message: msg }, { status: 'danger', autoHideDelay: 3000 });
        }).always(function () {
            btn.prop('disabled', false);
        });
    });
});
