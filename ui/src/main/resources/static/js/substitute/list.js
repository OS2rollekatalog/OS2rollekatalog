class SubstituteListService {
    self //reference to this class
    modalContainer //reference to the modal container for editing/creating
    networkService

    constructor() {
        this.self = this
        this.networkService = new NetworkService()
        this.modalContainer = document.getElementById('SubstituteListmodalContainer')
        this.#initButtons()
    }

    #initButtons() {
        this.#initEditDelegation()
        this.#initDeleteDelegation()
        this.#initCreateButton()

    }

    #initCreateButton() {
            const createButton = document.getElementById('createManagerSubstituteButton')
            createButton.addEventListener('click' , async (event)=> {
                // fetch modal
                const url =`${uiUrl}/create`
                await this.networkService.GetFragment(url, this.modalContainer)
                //init modal functionality
                this.initAutocomplete()
                this.#initManagerSelect()
                //show modal
                $(`#createModal`).modal('show')
            })
    }

    #initEditDelegation() {
        //event delegation
        const container = document.getElementById('listTable')
        this.#delegateEvent(container, 'onEditButton', 'click', async (onEditButton)=> {
            const id = onEditButton.getAttribute('data-id')
            const url =`${uiUrl}/${id}/edit`

            // fetch and open create modal
            await this.networkService.GetFragment(url, this.modalContainer)
            $(`#editModal`).modal('show')
            this.initAutocomplete()
        }
    )}

    #initDeleteDelegation() {
        //event delegation
        const self = this;
        const container = document.getElementById('listTable')
        this.#delegateEvent(container, 'onDeleteButton', 'click', (onDeleteButton)=> {
            const tr = onDeleteButton.closest('tr')
            const tdElements =  tr.querySelectorAll('td')

            //show confirmation, then fetch delete
            swal({
                html: true,
                title : `Er du sikker?`,
                text : `Du er ved at slette ${tdElements[0].textContent} som stedfortræder for ${tdElements[1].textContent} i afdelingen ${tdElements[2].textContent}?`,
                type : "warning",
                showCancelButton : true,
                confirmButtonColor : "#DD6B55",
                confirmButtonText : "Slet",
                cancelButtonText : "Fortryd",
                closeOnConfirm : true,
                closeOnCancel : true
            },
            function(confirmed) {
                if (!confirmed) { return;}
                const id = onDeleteButton.getAttribute('data-id')
                self.onDeleteManagerSubstitute(id)
                location.reload()
            });
        })
    }


    async onCreateManagerSubstitute(){
        //get values from fields
        const managerField = document.getElementById('managerSelection')
        const substituteField = document.getElementById('selectedSubstituteUuid')
        const ouField = document.getElementById('selectedOrgUnit')

        const data = {
            manager: {
                uuid: managerField.selectedOptions[0].value,
                name: ''
            },
            substitute: {
                uuid: substituteField.value,
                name:'',
                userId:'',
                orgUnitUuid:'',
                orgUnitName:'',
                managerUuid:'',
                managerUserId:''
            },
            orgUnitUUIDs: [...ouField.selectedOptions].map(option => option.value)
        }

        //validate
        let errors = 0
        if (!data.manager || !data.manager.uuid) {
            this.#showError('selectedManagerError')
            errors++
        } else {
            this.#hideError('selectedManagerError')
        }
        if (!data.substitute || !data.substitute.uuid) {
            this.#showError('selectedSubstituteError')
            errors++
        } else {
            this.#hideError('selectedSubstituteError')
        }
        if (!data.orgUnitUUIDs || data.orgUnitUUIDs.length < 1) {
            this.#showError('selectedOrgUnitError')
            errors++
        } else {
            this.#hideError('selectedOrgUnitError')
        }
        if(errors > 0) {
            return;
        }

        //post to server
        const url = `/rest/manager/substitute/add`
        this.networkService.Post(url, data)
            .then(_ => location.reload());
    }

    async onDeleteManagerSubstitute(id) {
        const url = `${restUrl}/${id}/delete`
        this.networkService.Delete(url)
    }

    async onEditManagerSubstitute(id) {
        //get values from fields
        const managerField = document.getElementById('managerSelection')
        const substituteField = document.getElementById('selectedSubstituteUuid')
        const ouField = document.getElementById('selectedOrgUnit')

        const data = {
            managerUuid: managerField.selectedOptions[0].value,
            substituteUuid: substituteField.value,
            orgUnitUuid: [...ouField.selectedOptions][0].value
        }

        //validate
        let errors = 0
        if (!data.managerUuid ) {
            this.#showError('selectedManagerError')
            errors++
        } else {
            this.#hideError('selectedManagerError')
        }
        if (!data.substituteUuid) {
            this.#showError('selectedSubstituteError')
            errors++
        } else {
                    this.#hideError('selectedSubstituteError')
                }
        if (!data.orgUnitUuid) {
            this.#showError('selectedOrgUnitError')
            errors++
        } else {
            this.#hideError('selectedOrgUnitError')
        }
        if(errors > 0) {
            return;
        }

        //post to server
        const url = `${restUrl}/${id}/edit`
        this.networkService.Put(url, data)

        location.reload()
    }

    /**
    * Places a listener on the container, triggering the action when a
    * containing element with the given class triggers the given event,
    * passing the triggering element as parameter to the action.
    * Note: Does not work with click on inner elements, only direct clicks
    * @param {HTMLElement} container
    * @param {String} triggerClass
    * @param {String eventType
    * @param {Function} action
    */
    #delegateEvent(container, triggerClass, eventType, action) {
        container.addEventListener(eventType, (event)=> {
            const target = event.target
            if (target && target.classList.contains(triggerClass)){
                action(target)
            }
        })
    }

    #initManagerSelect() {
        const managerSelect = document.getElementById('managerSelection')
        managerSelect.addEventListener('change', ()=> this.#onManagerSelected(managerSelect))
    }

    #onManagerSelected(managerSelect) {
        const selectedValue = managerSelect.selectedOptions[0].value
        if (!selectedValue) {return;}
        const ouSelect = document.getElementById('selectedOrgUnit')
        const url = `${uiUrl}/manager/${managerSelect.value}/orgunit/options`
        this.networkService.GetFragment(url, ouSelect)
        ouSelect.disabled = false
    }

    #showError(id){
        const errormessage = document.getElementById(id)
        errormessage.style.display = 'flex';
    }

    #hideError(id){
        const errormessage = document.getElementById(id)
        errormessage.style.display = 'none';
    }

    initAutocomplete() {
        const searchField = $("#search_person");

        searchField.autocomplete({
            serviceUrl: "/rest/manager/substitute/search/person",
            onSelect: function(suggestion) {
                $(this).val(suggestion.value);

                $("#selectedSubstituteUuid").val(suggestion.data);
            },
            preventBadQueries: true,
            triggerSelectOnValidInput: false,
            transformResult: function(response) {
                // filter results to not show manager
                const responseObject = $.parseJSON(response);
                if (!responseObject || responseObject == null) {
                    return {
                        suggestions: []
                    }
                }

                const filteredSuggestions = responseObject.suggestions.filter(
                    function (item) {
                        const managerSelect = document.getElementById('managerSelection')
                        const managerUuid = managerSelect.selectedOptions[0].value
                        return item.data !== managerUuid
                    });

                    return {
                        suggestions: filteredSuggestions
                    };
                }
            });
            searchField.select();
            searchField.focus();
        };
    }
