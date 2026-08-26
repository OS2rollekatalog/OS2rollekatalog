const token = $("meta[name='_csrf']").attr("content");
const jsTreeService = new JsTreeService();

let navigating = false;

document.addEventListener("DOMContentLoaded", () => {
    const config = JSON.parse(document.getElementById("kle-assignment-config").textContent);
    initJSTree(config.allKles, config.kleUrl);
});

function initJSTree(allKles, kleUrl) {
    jsTreeService.initTree("#kleTree", {
        core: {
            data: allKles
        },
        plugins: ["wholerow"]
    });

    jsTreeService.onSelectNode("#kleTree", (e, data) => {
        if (!navigating) {
            navigating = true;
            window.location.href = kleUrl + data.node.id;
        }
    });
}
