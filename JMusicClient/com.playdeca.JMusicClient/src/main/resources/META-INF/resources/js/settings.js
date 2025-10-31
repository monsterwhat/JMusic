window.resetLibrary = async function () {
    const res = await fetch(`/api/settings/resetLibrary`, {method: "POST"});
    const json = await res.json();
    if (res.ok && json.data) {
        console.log("[Settings] Library reset to:", json.data.libraryPath);
        const pathElem = document.getElementById("musicLibraryPath");
        if (pathElem)
            pathElem.textContent = json.data.libraryPath;
    } else {
        console.error("[Settings] Failed to reset library:", json.error);
    }
};

window.scanLibrary = async function () {
    const res = await fetch(`/api/settings/scanLibrary`, {method: "POST"});
    const json = await res.json();
    if (res.ok && json.data) {
        console.log("[Settings] Scan started");
    } else {
        console.error("[Settings] Failed to scan library:", json.error);
    }
};

window.clearLogs = async function () {
    const res = await fetch(`/api/settings/clearLogs`, {method: "POST"});
    const json = await res.json();
    if (res.ok && json.data) {
        const logsPanel = document.getElementById("logsPanel");
        if (logsPanel) {
            logsPanel.innerHTML = "";
        }
    } else {
        console.error("[Settings] Failed to clear logs:", json.error);
    }
};

window.clearSongsDB = async function () {
    const res = await fetch(`/api/settings/clearSongs`, {method: "POST"});
    const json = await res.json();
    if (res.ok && json.data) {
        console.log("[Settings] All songs deleted");
    } else {
        console.error("[Settings] Failed to clear songs DB:", json.error);
    }
};

window.setupLogWebSocket = function () {
    const logsPanel = document.getElementById("logsPanel");
    if (!logsPanel) return;

    const socket = new WebSocket(`ws://${window.location.host}/api/logs/ws`);

    socket.onmessage = function (event) {
        const message = JSON.parse(event.data);
        if (message.type === "log") {
            const p = document.createElement("p");
            p.textContent = message.payload;
            logsPanel.appendChild(p);
            logsPanel.scrollTop = logsPanel.scrollHeight;
        }
    };

    socket.onopen = function () {
        console.log("Log WebSocket connected.");
    };

    socket.onclose = function () {
        console.log("Log WebSocket disconnected. Attempting to reconnect...");
        setTimeout(window.setupLogWebSocket, 3000); // Reconnect after 3 seconds
    };

    socket.onerror = function (error) {
        console.error("Log WebSocket error:", error);
    };
}

window.refreshSettingsUI = async function () {
    const res = await fetch(`/api/settings`);
    const json = await res.json();
    if (res.ok && json.data) {
        const pathElem = document.getElementById("musicLibraryPath");
        if (pathElem && json.data.libraryPath)
            pathElem.textContent = json.data.libraryPath;
    } else {
        console.error("[Settings] Failed to refresh settings UI:", json.error);
    }
};

window.reloadMetadata = async function () {
    const res = await fetch(`/api/settings/reloadMetadata`, {method: "POST"});
    const json = await res.json();
    if (res.ok && json.data) {
        console.log("[Settings] Metadata reload started");
    } else {
        console.error("[Settings] Failed to reload metadata:", json.error);
    }
};

window.deleteDuplicates = async function () {
    const res = await fetch(`/api/settings/deleteDuplicates`, {method: "POST"});
    const json = await res.json();
    if (res.ok && json.data) {
        console.log("[Settings] Duplicate deletion started");
    } else {
        console.error("[Settings] Failed to delete duplicates:", json.error);
    }
};

document.addEventListener("DOMContentLoaded", () => {
    document.getElementById("resetLibrary").onclick = () => window.resetLibrary();
    document.getElementById("scanLibrary").onclick = () => window.scanLibrary();
    document.getElementById("clearLogs").onclick = () => window.clearLogs();
    document.getElementById("clearSongs").onclick = () => window.clearSongsDB();
    document.getElementById("reloadMetadata").onclick = () => window.reloadMetadata();
    document.getElementById("deleteDuplicates").onclick = () => window.deleteDuplicates();
    window.refreshSettingsUI?.();
    window.setupLogWebSocket?.();

    // Navbar burger functionality
    const burger = document.querySelector('.navbar-burger');
    const menu = document.querySelector('.navbar-menu');
    burger.addEventListener('click', () => {
        burger.classList.toggle('is-active');
        menu.classList.toggle('is-active');
    });
});