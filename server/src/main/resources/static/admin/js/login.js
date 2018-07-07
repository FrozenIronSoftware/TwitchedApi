(function () {
    /**
     * Hide the form an show the logging in message
     */
    function showMessage(messageText, error) {
        error = error || false;
        document.getElementById("login").style.display = "none";
        var message = document.getElementById("message");
        message.textContent = messageText;
        message.classList.remove("hidden");
        if (error) {
            message.classList.remove("success");
            message.classList.add("error");
        }
        else {
            message.classList.remove("error");
            message.classList.add("success");
        }
    }

    /**
     * Show the form and show an error message
     */
    function showError(errorMessage) {
        showMessage(errorMessage, true)
    }

    /**
     * Redirect to the completion page
     */
    function redirectToCompletion() {
        var params = (new URL(document.location)).searchParams;
        var completion = params.get("completion");
        if (typeof(completion) !== "undefined" && completion !== null) {
            document.location = completion
        }
    }

    /**
     * Handle form submit
     */
    function onSubmit(event) {
        event.preventDefault();
        var username = this.elements["username"].value;
        var password = this.elements["password"].value;
        showMessage("Logging In");
        var request = new XMLHttpRequest();
        request.addEventListener("loadend", function (event) {
            switch (request.status) {
                case 200:
                    showMessage("Successfully Logged In");
                    redirectToCompletion();
                    break;
                case 401:
                    showError("Incorrect username or password");
                    break;
                default:
                    showError("Server error");
                    break;
            }
        });
        request.open("POST", "/admin/api/login", true);
        request.setRequestHeader("Content-Type", "application/json");
        request.send(JSON.stringify({
            username: username,
            password: password
        }));
    }

    /**
     * Init
     */
    function main() {
        // Handle submit event
        document.getElementById("login").addEventListener("submit", onSubmit)
    }

    window.addEventListener("load", main, false);
})();