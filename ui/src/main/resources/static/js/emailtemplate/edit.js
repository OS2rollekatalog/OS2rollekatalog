document.addEventListener('DOMContentLoaded', function () {
    const config = JSON.parse(document.getElementById('pageConfig').textContent);
    const restUrl = config.restUrl;
    const listUrl = config.listUrl;
    const msgSuccess = config.msgSuccess;
    const msgFail = config.msgFail;
    const swalImageOk = config.swalImageOk;
    const swalImageTitle = config.swalImageTitle;
    const swalImageText = config.swalImageText;
    const token = document.querySelector("meta[name='_csrf']").getAttribute("content");

    // Full WYSIWYG editor for the main message (pictures, links)
    $('textarea[name="message"]').summernote({
        height: 320,
        toolbar: [
            ["font", ["bold", "italic", "underline"]],
            ["para", ["ul", "ol"]],
            ["insert", ["picture", "link"]],
            ["view", ["codeview"]]
        ],
        maximumImageFileSize: 100 * 1024,
        callbacks: {
            onImageUploadError: function () {
                swal({
                    title: swalImageTitle,
                    text: swalImageText,
                    confirmButtonColor: "#4765a0",
                    confirmButtonText: swalImageOk
                });
            }
        },
        dialogsInBody: true
    });

    // Reduced toolbar for repeating parts (content-only fragments)
    $('textarea[name="repeatingPart"], textarea[name="nestedRepeatingPart"]').summernote({
        height: 150,
        toolbar: [
            ["font", ["bold", "italic", "underline"]],
            ["view", ["codeview"]]
        ],
        dialogsInBody: true
    });

    $('#checkboxEnabled').on('change', function () {
        $("input[name=enabled]").val(this.checked);
    });

    $('#buttonSubmit').on('click', function () {
        const data = getFormData($('#templateForm'));
        save(data, false);
    });

    $('#buttonTest').on('click', function () {
        const data = getFormData($('#templateForm'));
        save(data, true);
    });

    function getFormData(oForm) {
        const unindexedArray = oForm.serializeArray();
        const indexedArray = {};
        $.map(unindexedArray, function (n) {
            indexedArray[n['name']] = n['value'];
        });
        return indexedArray;
    }

    function save(payload, tryEmail) {
        $.ajax({
            method: "POST",
            url: restUrl + "?tryEmail=" + tryEmail,
            headers: {
                "content-type": "application/json",
                'X-CSRF-TOKEN': token
            },
            data: JSON.stringify(payload)
        }).done(function (data) {
            if (data !== '') {
                $.notify({ message: data }, { status: 'success', autoHideDelay: 2000 });
            } else {
                $.notify({ message: msgSuccess }, { status: 'success', autoHideDelay: 2000 });
            }
        }).fail(errorHandler(msgFail));
    }
});
