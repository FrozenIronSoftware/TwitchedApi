/**
 * Extracts the access token and state (link id) from the url hash
 */
function extractAccessToken() {
    var hash = location.hash.replace("#", "").split("&");
    var token = null;
    var id = null;
    hash.forEach(function(item) {
        var itemSplit = item.split("=");
        if (itemSplit.length > 1) {
            if (itemSplit[0] === "access_token")
                token = itemSplit[1];
            else if (itemSplit[0] === "state")
                id = itemSplit[1];
        }
    });
    // There is an access token and link id. Verify them
    if (typeof(token) !== "undefined" && token !== null && typeof(id) !== "undefined" && id !== null) {
        storeToken(token, id)
    }
    // Missing access token or link id. Error
    else {
        showMessage("Link Failed", "Invalid data received");
    }
}

/**
 * Sets the message fields on the page
 * @param titleString title of message
 * @param messageString message text
 */
function showMessage(titleString, messageString) {
    var title = document.getElementById("title");
    var message = document.getElementById("message");
    title.innerHTML = titleString;
    message.innerHTML = messageString;
}

/**
 * Sends an XHR request to the API server to store the token
 * @param token access token string
 * @param id link id string
 */
function storeToken(token, id) {
    var data = {token:token, id:id};
    var request = new XMLHttpRequest();

    request.onloadend = function () {
        if (request.status === 200)
            return;
        var message = request.statusText;
        if (request.responseText.indexOf("Invalid link id") !== -1)
            message = "The link code has expired.";
        showMessage("Link Failed", message);
    };
    request.open("POST", "/api/link", true);
    request.setRequestHeader("Content-Type", "application/json");
    request.send(JSON.stringify(data));
}

window.onload = function () {
    extractAccessToken();
};