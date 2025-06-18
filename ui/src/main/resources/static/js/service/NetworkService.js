async function fetchPOST(url, data) {
    const token = document.getElementsByName("_csrf")[0].getAttribute("content");
    const response = await fetch(url, {
        method: "POST",
        headers: {
            'X-CSRF-TOKEN': token,
            "Content-Type": "application/json"
        },
        body: data != null ? JSON.stringify(data) : null,
    });
    if (!response.ok) {
        console.error(response.error)
    }
    return response
}

async function fetchGET(url) {
    const token = document.getElementsByName("_csrf")[0].getAttribute("content");
    const response = await fetch(url, {
        method: "GET",
        headers: {
            'X-CSRF-TOKEN': token,
            "Content-Type": "application/json"
        }
    });
    if (!response.ok) {
        console.error(response.error)
    }
    return response
}

async function fetchPUT(url, data) {
    const token = document.getElementsByName("_csrf")[0].getAttribute("content");
    const response = await fetch(url, {
        method: "PUT",
        headers: {
            'X-CSRF-TOKEN': token,
            "Content-Type": "application/json"
        },
        body: data != null ? JSON.stringify(data) : null,
    });
    if (!response.ok) {
        console.error(response.error)
    }
    return response
}

async function fetchDELETE ( url ) {
    const token = document.getElementsByName("_csrf")[0].getAttribute("content");
    const response = await fetch(url, {
        method: "DELETE",
        headers: {
            'X-CSRF-TOKEN': token,
            "Content-Type": "application/json"
        }
    });
    if (!response.ok) {
        console.error(response.error)
    }
    return response
}

async function fetchFragment(url, containerElement) {
    const token = document.getElementsByName("_csrf")[0].getAttribute("content");
    const response = await fetch(url, {
        method: "GET",
        headers: {
            'X-CSRF-TOKEN': token,
            "Content-Type": "application/json"
        }
    });
    if (!response.ok) {
        console.error(response.error)
    }
    const responseText = await response.text()
    containerElement.innerHTML = responseText;
}