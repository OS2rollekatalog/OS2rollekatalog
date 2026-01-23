document.addEventListener("DOMContentLoaded", function() {
    initShowTechnicalDetails()
})

function initShowTechnicalDetails() {
    const technicalDetailBox = document.querySelector("#technicalDetailBox");
    const technicalDetailContainer = document.querySelector("#technicalDetailContainer");
    const instructionText = document.querySelector("#instructionText");

    technicalDetailContainer.addEventListener("click", () => {
        instructionText.classList.add("hidden");
        technicalDetailBox.classList.remove("hidden");
    })

}