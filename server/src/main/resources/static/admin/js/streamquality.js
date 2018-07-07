(function () {
    var VERSION = 1;

    /**
     * Check the quality items for valid values
     * @return {string}
     */
    function areValuesValid() {
        var qualityItems = document.getElementById("qualities").getElementsByTagName("form");
        if (qualityItems.length === 0)
            return "No items to save";
        var models = [];
        for (var qualityItemIndex = 0; qualityItemIndex < qualityItems.length; qualityItemIndex++) {
            var qualityItem = qualityItems[qualityItemIndex];
            var model = qualityItem.elements["model"].value;
            var bitrate = parseFloat(qualityItem.elements["bitrate"].value) * 1000000;
            if (model === null || typeof(model) === "undefined" || model.length === 0 ||
                isNaN(bitrate) || bitrate <= 0 || bitrate > 20000000) {
                return "Invalid item: Index " + qualityItemIndex + " Model: " + model;
            }
            if (models.indexOf(model) !== -1)
                return "Invalid item: Duplicate model name: " + model;
            models.push(model);
        }
        return "";
    }

    /**
     * Handle save button click
     */
    function onSaveButtonClicked() {
        showMessage("Saving");
        if (!window.confirm("Save stream qualities?")) {
            error("Save canceled");
            enableInputs();
            return;
        }
        disableInputs();
        var validationMessage = areValuesValid();
        if (validationMessage.length === 0) {
            // Construct quality items Json structure
            var qualityItemsJson = [];
            var qualityItems = document.getElementById("qualities").getElementsByTagName("form");
            for (var qualityItemIndex = 0; qualityItemIndex < qualityItems.length; qualityItemIndex++) {
                var qualityItem = qualityItems[qualityItemIndex];
                var qualityItemJson = {
                    model: qualityItem.elements["model"].value,
                    bitrate: parseFloat(qualityItem.elements["bitrate"].value) * 1000000,
                    "240p30": qualityItem.elements["240p30"].checked,
                    "240p60": qualityItem.elements["240p60"].checked,
                    "480p30": qualityItem.elements["480p30"].checked,
                    "480p60": qualityItem.elements["480p60"].checked,
                    "720p30": qualityItem.elements["720p30"].checked,
                    "720p60": qualityItem.elements["720p60"].checked,
                    "1080p30": qualityItem.elements["1080p30"].checked,
                    "1080p60": qualityItem.elements["1080p60"].checked,
                    only_source_60: qualityItem.elements["only_source_60"].checked,
                    comment: qualityItem.elements["comment"].value
                };
                qualityItemsJson.push(qualityItemJson);
            }
            // Submit to API
            var request = new XMLHttpRequest();
            request.addEventListener("loadend", function () {
                if (request.status !== 200)
                    error("Save failed");
                loadQualities(function (qualities) {
                    populateQualityList(qualities);
                    showMessage("Saved");
                })
            });
            request.open("POST", "/api/qualities", true);
            request.setRequestHeader("Client-ID", clientId);
            request.setRequestHeader("Content-Type", "application/json");
            request.send(JSON.stringify(qualityItemsJson));
        }
        else {
            error(validationMessage);
            enableInputs();
        }
    }

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
        qualityItem.getElementsByClassName("quality-remove")[0].addEventListener("click", function () {
            var qualityItem = this.parentNode;
            var qualities = document.getElementById("qualities");
            qualities.removeChild(qualityItem);
        });
        qualityItem.elements["bitrate"].addEventListener("focus", function () {
            var bitrate = parseFloat(this.value);
            if (isNaN(bitrate))
                this.value = "";
            else
                this.value = bitrate * 1000000.0;
        });
        qualityItem.elements["bitrate"].addEventListener("blur", function () {
            var bitrate = parseFloat(this.value);
            if (isNaN(bitrate))
                this.value = "";
            else
                this.value = (bitrate / 1000000.0) + " Mbps";
        });
        document.getElementById("qualities").appendChild(qualityItem);
    }

    /**
     * Handle add button clicked
     */
    function onAddButtonClicked() {
        addQualityItem();
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
        enableInputs();
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
            input.setAttribute("disabled", "disabled");
        }
    }

    /**
     * Enable inputs
     */
    function enableInputs() {
        var inputs = document.getElementsByTagName("input");
        for (var inputIndex = 0; inputIndex < inputs.length; inputIndex++) {
            var input = inputs[inputIndex];
            input.removeAttribute("disabled");
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
     * Clear message
     */
    function clearMessage() {
        var message = document.getElementById("message");
        message.classList.add("hidden");
    }

    /**
     * Load the qualities from the server and call back to the passed closure
     * The completion function should accept an array of quality items from Twitched's API
     * @param completion callback function
     */
    function loadQualities(completion) {
        showMessage("Loading Qualities");
        disableInputs();
        var request = new XMLHttpRequest();
        request.addEventListener("loadend", function () {
            clearMessage();
            if (request.status === 200) {
                try {
                    var json = JSON.parse(request.response);
                    if (!(json instanceof Array))
                        return error("Invalid JSON data received from API");
                    completion(json);
                }
                catch (e) {
                    console.log(e);
                    error("Failed to parse API JSON.");
                }
            }
            else {
                error("Failed to connect to API")
            }
        });
        request.open("GET", "/api/qualities", true);
        request.setRequestHeader("Client-ID", clientId);
        request.send();
    }

    /**
     * Handle import file submission
     */
    function onImportSubmit(event) {
        event.preventDefault();
        showMessage("Importing");
        disableInputs();
        var files = this.elements["file"].files;
        if (files.length !== 1) {
            error("Invalid JSON backup");
            enableInputs();
        }
        var reader = new FileReader();
        reader.addEventListener("loadend", function () {
            try {
                var backup = JSON.parse(reader.result);
                var version = backup["version"];
                if (version === null || typeof(version) === "undefined" || version.length === 0) {
                    error("Invalid JSON backup");
                    enableInputs();
                }
                else if (version > VERSION) {
                    error("This backup is from a newer version of stream quality configuration manager");
                    enableInputs();
                }
                else {
                    var confirm = version < VERSION ?
                        window.confirm("This backup is older than the current stream quality configuration manager version." +
                            " Load anyway?") : true;
                    if (confirm) {
                        var qualities = backup["qualities"];
                        populateQualityList(qualities, false);
                        if (version === VERSION)
                            onSaveButtonClicked();
                        else {
                            showMessage("Version mismatch: Caution: Loaded locally. Press save to submit to server.");
                        }
                    }
                    else {
                        error("Backup loading canceled");
                        enableInputs();
                    }
                }
            }
            catch (e) {
                enableInputs();
            }
        });
        reader.readAsText(files[0]);
    }

    /**
     * Export the values from the API
     */
    function onExportButtonClicked() {
        loadQualities(function (qualities) {
            var backup = {
                version: VERSION,
                qualities: qualities
            };
            var a = document.getElementById("backup-export-anchor");
            a.href = "data:application/json;charset=utf-8," + encodeURIComponent(JSON.stringify(backup));
            a.download = "stream_qualities.json";
            a.click();
            enableInputs();
        });
    }

    /**
     * Init
     */
    function main() {
        document.getElementById("button-save").addEventListener("click", onSaveButtonClicked);
        document.getElementById("button-add").addEventListener("click", onAddButtonClicked);
        document.getElementById("message").addEventListener("click", function () {
            this.classList.add("hidden");
        });
        document.getElementById("backup-import").addEventListener("submit", onImportSubmit);
        document.getElementById("backup-export").addEventListener("click", onExportButtonClicked);
        loadQualities(populateQualityList);
    }

    window.addEventListener("load", main)
})();