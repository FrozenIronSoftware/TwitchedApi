/**
 * Handle dependency item click
 */
function handleDependencyItemClick() {
    // Item is open
    if (this.classList.contains("dep-expanded")) {
        this.classList.remove("dep-expanded");
        this.classList.add("dep-closed");
        // Hide item
        var texts = this.getElementsByClassName("dep-text");
        for (var textIndex = 0; textIndex < texts.length; textIndex++) {
            var text = texts[textIndex];
            text.style.display = "none";
        }
    }
    // Item is closed
    else if (this.classList.contains("dep-closed")) {
        this.classList.remove("dep-closed");
        this.classList.add("dep-expanded");
        // Show item
        var texts = this.getElementsByClassName("dep-text");
        for (var textIndex = 0; textIndex < texts.length; textIndex++) {
            var text = texts[textIndex];
            text.style.display = "block";
        }
    }
}

/**
 * Add click handler to the dependency items
 */
function addDependencyItemClickHandler() {
    var items = document.getElementsByClassName("dep-item");
    for (var itemIndex = 0; itemIndex < items.length; itemIndex++) {
        var item = items[itemIndex];
        item.onclick = handleDependencyItemClick
    }
}


window.addEventListener("load", addDependencyItemClickHandler, false);