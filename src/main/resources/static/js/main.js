/**
 * Add the menu event listener
 */
function addMenuEvent() {
    var menu = document.getElementById("nav-expander");
    menu.onclick = onMenuClick
}

function onMenuClick() {
    var isOpen = this.classList.contains("nav-expanded");
    if (isOpen) {
        this.classList.remove("nav-expanded");
        this.classList.add("nav-closed")
    }
    else {
        this.classList.remove("nav-closed");
        this.classList.add("nav-expanded");
    }
    var navLinks = document.getElementsByClassName("nav-links");
    for (var navLinkIndex = 0; navLinkIndex < navLinks.length; navLinkIndex++) {
        var navLink = navLinks[navLinkIndex];
        console.log(navLink);
        if (isOpen) {
            navLink.style.marginLeft = "-10000px";
        }
        else {
            navLink.style.marginLeft = "-13%";
        }
    }
}

window.addEventListener("load", addMenuEvent, false);