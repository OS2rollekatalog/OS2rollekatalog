// Global CSRF token, used across the app's shared services
const token = $("meta[name='_csrf']").attr("content");

document.addEventListener("DOMContentLoaded", () => {
    const config = JSON.parse(document.getElementById("kle-mapped-config").textContent);
    const klePerformers = config.klePerformers;

    renderKleTree($("#kle_performing_menu"), config.kleMainGroups, "1", klePerformers);

    $("#kle_performing_menu").on("click", "a", function (e) {
        kleTree($(this), config.restUrl, klePerformers);
    });
});

function kleTree(element, restUrl, klePerformers) {
    const scrollTop = document.documentElement.scrollTop;
    const $this = element.closest("li");

    if ($this.hasClass("kleOpen")) {
        $this.removeClass("kleOpen").addClass("kleClose").find("ul").first().hide();
        $this.find(".arrow").removeClass("fa-chevron-down").addClass("fa-chevron-right");
        return;
    }

    if ($this.hasClass("kleClose")) {
        $this.find(".arrow").removeClass("fa-chevron-right").addClass("fa-chevron-down");
        $this.removeClass("kleClose").addClass("kleOpen").find("ul").first().show();
        return;
    }

    $this.find(".arrow").removeClass("fa-chevron-right").addClass("fa-chevron-down");
    $this.addClass("kleOpen");
    const kleParentCode = $this.attr("id").replace(".", "");

    $.ajax({
        method: "GET",
        url: restUrl + "getKle/" + kleParentCode,
        success: (response) => {
            if ($this.hasClass("kleMainGroup")) {
                renderKleTree($this, response, "2", klePerformers);
            } else if ($this.hasClass("kleGroup")) {
                renderKleTree($this, response, "3", klePerformers);
            }

            window.scrollTo(0, scrollTop);
        }
    });
}

function renderKleTree(element, kleList, level, klePerformers) {
    element.append("<ul class='list-unstyled'></ul>");
    const ul = $(element).find("ul");

    $(kleList).each(function () {
        switch (level) {
            case "1":
                renderKleTreeMainGroup(ul, $(this));
                break;
            case "2":
                renderKleTreeGroup(ul, $(this));
                break;
            case "3":
                renderKleTreeSubject(ul, $(this));
                break;
        }
    });

    $(klePerformers).each(function () {
        const code = $(this).attr("code").replace("\.\*", "");
        ul.find('em[id="' + code + '"]').removeClass("fa-square-o").removeClass("fa-minus-square-o").addClass("fa-check-square-o");

        if (code.length == 8) {
            if (ul.find('em[id="' + code.substring(0, 5) + '"]').hasClass("fa-square-o")) {
                ul.find('em[id="' + code.substring(0, 5) + '"]').toggleClass("fa-square-o").toggleClass("fa-minus-square-o");
            }
        }

        if (code.length >= 5) {
            if (ul.find('#' + code.substring(0, 2) + " > em").hasClass("fa-square-o")) {
                ul.find('#' + code.substring(0, 2) + " > em").toggleClass("fa-square-o").toggleClass("fa-minus-square-o");
            }
        }
    });
}

function renderKleTreeMainGroup(ul, $this) {
    ul.append("<li class='kleElement kleMainGroup' id='" + $this.attr("code") + "'><em id='" + $this.attr("code") + "' class='kleCheckbox fa fa-square-o' aria-hidden='true'></em> <a href='#'><em class='arrow fa fa-chevron-right' aria-hidden='true'></em> " + $this.attr("code") + " " + $this.attr("name") + "</a></li>");
}

function renderKleTreeGroup(ul, $this) {
    ul.append("<li class='kleElement kleGroup' id='" + $this.attr("code") + "'><em id='" + $this.attr("code") + "'class='kleCheckbox fa fa-square-o' aria-hidden='true'></em> <a href='#'><em class='arrow fa fa-chevron-right' aria-hidden='true'></em> " + $this.attr("code") + " " + $this.attr("name") + "</a></li>");
}

function renderKleTreeSubject(ul, $this) {
    ul.append("<li class='kleElement kleSubject' id='" + $this.attr("code") + "'><em id='" + $this.attr("code") + "'class='kleCheckbox fa fa-square-o' aria-hidden='true'></em> <a href='#'> " + $this.attr("code") + " " + $this.attr("name") + "</a></li>");
}
