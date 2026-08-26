document.addEventListener("DOMContentLoaded", () => {
    const xmlViewerElement = document.getElementById("xmlViewer");
    if (!xmlViewerElement) {
        return;
    }

    // Exposed on window for backwards compatibility, since other scripts on
    // pages using this fragment may reference "xmlEditor" as a global
    window.xmlEditor = CodeMirror.fromTextArea(xmlViewerElement, {
        mode: "application/xml",
        lineNumbers: true
    });

    const totalLines = window.xmlEditor.lineCount();
    window.xmlEditor.autoFormatRange({ line: 0, ch: 0 }, { line: totalLines });
    window.xmlEditor.setCursor({ line: 0, ch: 0 });
});
