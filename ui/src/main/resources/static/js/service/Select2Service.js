/**
 * Service class for the Select2 jquery plugin
 */
class Select2Service {
    defaultConfig = {

    }
    defaultServerSideConfig = {
        ajax: {
            delay: 300, //delay request for 300 to let user finish typing search
            url: '', // request url
            data: (params) => { // function returning object describing how request query is built
                return {
                    search: params.term,
                };
            },
        }
    }

    constructor(){}


    /**
     * Initializes a default select2 instance
     * @param selector jquery selector for dropdown(s) to be initialized
     * @param customConfig (optional)
     */
    initSelect(selector, customConfig){
        $(selector).select2(customConfig ? customConfig : this.defaultConfig)
    }

    /**
     * Initializes a serverside select2 instance, calling the specified url for data
     * @param selector jquery selector for dropdown(s) to be initialized
     * @param url request endpoint url. Can be a string or a function returning a string
     * @param customConfig (optional)
     */
    initServerSideSelect(selector, url, customConfig) {
        const config = customConfig ? customConfig : {...this.defaultServerSideConfig}
        config.ajax.url = url
        $(selector).select2(config)
    }
}