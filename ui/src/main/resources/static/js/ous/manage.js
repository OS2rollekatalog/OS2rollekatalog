let pageConfig;

document.addEventListener("DOMContentLoaded", () => {
    pageConfig = JSON.parse(document.getElementById("ou-manage-config").textContent);

    // shared fragment scripts (e.g. NetworkService, datatables.html) read this from global scope
    window.token = $("meta[name='_csrf']").attr("content");
    window.ou = pageConfig.ou;

    const sweetAlertService = new SweetAlertService();

    const rolesService = new RolesService(pageConfig, sweetAlertService);
    const autoCompleteService = new AutoCompleteService(pageConfig, () => reloadRequestApproveFragment(autoCompleteService));

    // exposed for onclick handlers still living in server-rendered fragments
    window.rolesService = rolesService;
    window.autoCompleteService = autoCompleteService;

    if (pageConfig.kleUiEnabled) {
        const kleConfig = JSON.parse(document.getElementById("ou-manage-kle-config").textContent);
        const kleService = new KleService({ ...pageConfig, ...kleConfig }, window.jsTreeService);
        window.kleService = kleService;

        kleService.loadViewFragment('PERFORMING');
        kleService.loadViewFragment('INTEREST');

        document.getElementById('editBtnPerforming').addEventListener('click', () => {
            kleService.loadEditFragment('PERFORMING');
        });
        document.getElementById('editBtnInterest').addEventListener('click', () => {
            kleService.loadEditFragment('INTEREST');
        });
    }

    document.getElementById('stamdataLink').addEventListener('click', () => {
        document.getElementById('caretIcon').classList.toggle('fa-caret-right');
        document.getElementById('caretIcon').classList.toggle('fa-caret-down');
    });

    autoCompleteService.init();

    // ensure checkbox listeners are enabled for currently visible checkboxes
    addCheckboxListeners();

    // flip active tab on page-load
    const selectedTab = localStorage.getItem(pageConfig.ou);
    if (selectedTab != null) {
        $(`a[data-toggle="tab"][href="${selectedTab}"]`).tab('show');
    }

    // setup tab memory
    $('#dataTabs a').click(function (event) {
        event.preventDefault();
        $(this).tab('show');
    });

    $('a[data-toggle="tab"]').on("shown.bs.tab", (event) => {
        const id = $(event.target).attr("href");
        localStorage.setItem(pageConfig.ou, id);
    });

    // for loading titles in the javascript fragment
    ouRolesModalService.ouUuid = pageConfig.ou;
    ouRolesModalService.parentService = rolesService;

    ouRolesEditModalService.ouUuid = pageConfig.ou;
    ouRolesEditModalService.parentService = rolesService;

    // Initialize tabs
    rolesService.loadRolesFragment();

    // set parentService for editing roleassignments
    window.ouRoleAssignmentService.parentService = rolesService;

    // init table
    fragShowDataTableFun('#ousUsersTable', 0, 100, "ou_manage_list_of_users");

    // handler for setting OU level
    document.querySelectorAll('.js-set-level-select').forEach((select) => {
        select.addEventListener('change', () => {
            setLevel(select);
        });
    });
});

function addCheckboxListeners() {
    // this makes sure the KLE checkbox (inherit KLE) has the relevant event linked to it
    $('.inherit-checkbox').off("change");
    $('.inherit-checkbox').change(function () {
        inheritCheckboxChange(this);
    });
}

// handler for setting OU level
function setLevel(selectElement) {
    const updateUrl = `${pageConfig.url}${pageConfig.ou}/setLevel/${$(selectElement).val()}`;

    $.ajax({
        url: updateUrl,
        method: "POST",
        headers: {
            'X-CSRF-TOKEN': window.token
        },
        error: defaultErrorHandler,
        success: () => {
            $.notify({
                message: pageConfig.fieldUpdatedMsg
            }, {
                status: 'success',
                autoHideDelay: 2000
            });
        }
    });
}

// handler for setting KLE inherit
function inheritCheckboxChange(checkbox) {
    let updateUrl = `${pageConfig.url}${pageConfig.ou}/inherit`;
    updateUrl += checkbox.checked ? "?active=true" : "?active=false";

    $.ajax({
        url: updateUrl,
        method: "POST",
        headers: {
            'X-CSRF-TOKEN': window.token
        },
        error: defaultErrorHandler,
        success: () => {
            $.notify({
                message: pageConfig.fieldUpdatedMsg
            }, {
                status: 'success',
                autoHideDelay: 2000
            });
        }
    });
}

function reloadRequestApproveFragment(autoCompleteService) {
    $("#requestApproveFragment").load(`${pageConfig.urlUi}requestapprove/${pageConfig.ou}`, () => {
        autoCompleteService.init();
    });
}
