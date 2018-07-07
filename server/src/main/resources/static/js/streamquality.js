(function () {
    /**
     * Create a quality item from the dummy html element and set its state
     */
    function addQualityItem(quality) {
        var qualityItem = document.getElementById("quality-dummy-container").getElementsByTagName("form")[0]
            .cloneNode(true);
        if (quality !== null && typeof(quality) !== "undefined") {
            qualityItem.elements["model"].value = quality["model"];
            qualityItem.elements["bitrate"].value = (quality["bitrate"] / 1000000.0) + " Mbps";
            qualityItem.elements["240p30"].checked = quality["240p30"];
            qualityItem.elements["240p60"].checked = quality["240p60"];
            qualityItem.elements["480p30"].checked = quality["480p30"];
            qualityItem.elements["480p60"].checked = quality["480p60"];
            qualityItem.elements["720p30"].checked = quality["720p30"];
            qualityItem.elements["720p60"].checked = quality["720p60"];
            qualityItem.elements["1080p30"].checked = quality["1080p30"];
            qualityItem.elements["1080p60"].checked = quality["1080p60"];
            qualityItem.elements["only_source_60"].checked = quality["only_source_60"];
            qualityItem.elements["comment"].value = quality["comment"];
        }
        qualityItem.removeChild(qualityItem.getElementsByClassName("quality-remove")[0]);
        document.getElementById("qualities").appendChild(qualityItem);
    }

    /**
     * Populate the list with data from the server
     * @param qualities array of quality items
     * @param validate should the quality json be validated before attempting to populate
     */
    function populateQualityList(qualities, validate) {
        validate = validate || true;
        var qualitiesElement = document.getElementById("qualities");
        while (qualitiesElement.hasChildNodes())
            qualitiesElement.removeChild(qualitiesElement.lastChild);
        for (var qualityIndex = 0; qualityIndex < qualities.length; qualityIndex++) {
            var quality = qualities[qualityIndex];
            if (!("model" in quality && "bitrate" in quality && "240p30" in quality &&
                "240p60" in quality && "480p30" in quality && "480p60" in quality &&
                "720p30" in quality && "720p60" in quality && "1080p30" in quality &&
                "1080p60" in quality && "only_source_60" in quality && "comment" in quality) && validate)
                return error("Invalid API response: Quality item missing value");
            addQualityItem(quality);
        }
        disableInputs();
    }

    /**
     * Show a message
     * @param messageText text
     * @param error if set to true an error will be shown
     */
    function showMessage(messageText, error) {
        error = error || false;
        var message = document.getElementById("message");
        message.classList.remove("hidden", "error", "success");
        if (error)
            message.classList.add("error");
        else
            message.classList.add("success");
        message.textContent = messageText;
    }

    /**
     * Disable inputs
     */
    function disableInputs() {
        var inputs = document.getElementsByTagName("input");
        for (var inputIndex = 0; inputIndex < inputs.length; inputIndex++) {
            var input = inputs[inputIndex];
            if (input.id === "quality-search")
                continue;
            input.setAttribute("disabled", "disabled");
        }
    }

    /**
     * Show error message and clear all events
     * @param message
     */
    function error(message) {
        disableInputs();
        showMessage(message, true);
    }

    /**
     * Handle search input
     */
    function onSearchInputChange() {
        var query = this.value;
        var qualitiesMatchingQuery = [];
        for (var qualityIndex = 0; qualityIndex < qualities.length; qualityIndex++) {
            var quality = qualities[qualityIndex];
            var model = quality["model"];
            var comment = quality["comment"];
            if (model.toUpperCase().indexOf(query.toUpperCase()) !== -1 ||
                comment.toUpperCase().indexOf(query.toUpperCase()) !== -1)
                qualitiesMatchingQuery.push(quality);
        }
        populateQualityList(qualitiesMatchingQuery);
    }

    /**
     * Init
     */
    function main() {
        document.getElementById("quality-search").addEventListener("input", onSearchInputChange);
        populateQualityList(qualities);
    }

    window.addEventListener("load", main)
})();