class TabService {
    loadedContent = []

    constructor () {}

    initLoadContentOnClick (onClickElement, contentContainerElement, contentUrl, callBack) {
        if(onClickElement && contentContainerElement && contentUrl) {
            onClickElement.addEventListener('click', async (event)=> this.#loadContent(contentContainerElement, contentUrl, callBack))
        } else {
            if (!onClickElement) {
                console.error('Missing parameter in initLoadContentOnClick: ', 'onClickElement')
            }
            if (!contentContainerElement) {
                console.error('Missing parameter in initLoadContentOnClick: ', 'contentContainerElement')
            }
            if (!contentUrl) {
                console.error('Missing parameter in initLoadContentOnClick: ', 'contentUrl')
            }
        }
    }

    #setLoading(contentContainerElement){
        if(contentContainerElement) {
            const loadingElement = document.createElement('div')
            loadingElement.classList.add('sk-spinner')
            loadingElement.classList.add('sk-spinner-three-bounce')
            const loadingDot1 = document.createElement('div')
            loadingDot1.classList.add('sk-bounce1')
            const loadingDot2 = document.createElement('div')
            loadingDot2.classList.add('sk-bounce2')
            const loadingDot3 = document.createElement('div')
            loadingDot3.classList.add('sk-bounce3')
            loadingElement.appendChild(loadingDot1)
            loadingElement.appendChild(loadingDot2)
            loadingElement.appendChild(loadingDot3)

            contentContainerElement.appendChild(loadingElement)
        }

    }

    async #loadContent (contentContainerElement, contentUrl, callBack) {


        //Check if content is already loaded
        if(!this.loadedContent.includes(contentContainerElement.id+'loaded')) {

            //set loading text
            this.#setLoading(contentContainerElement)

//            //fetch fragment
            const response = await fetch(contentUrl, {
                contentType: 'application/json',
                headers: {
                    'X-CSRF-TOKEN': token
                },
            })

            if (!response.ok) {
                throw new Error("status for content retrieval not ok: " + response.statusText)
            }

            const responseText = await response.text();

            //set container content to recieved fragment
            contentContainerElement.innerHTML = responseText

            callBack()

            //set loaded state for this fragment
            this.loadedContent.push(contentContainerElement.id+'loaded')
        }
    }
}