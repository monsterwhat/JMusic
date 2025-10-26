window.resetLibrary = async function () {
    const res = await fetch(`/api/settings/resetLibrary`, {method: "POST"});
    const data = await res.json();
    if (res.ok && data) {
        console.log("[Settings] Library reset to:", data.libraryPath);
        const pathElem = document.getElementById("musicLibraryPath");
        if (pathElem)
            pathElem.textContent = data.libraryPath;
    } else {
        console.error("[Settings] Failed to reset library");
    }
};

window.scanLibrary = async function () {
    const res = await fetch(`/api/settings/scanLibrary`, {method: "POST"});
    if (res.ok) {
        console.log("[Settings] Scan started");
        await window.refreshSettingsUI();
    } else {
        console.error("[Settings] Failed to scan library");
    }
};

window.clearLogs = async function () {
    const res = await fetch(`/api/settings/clearLogs`, {method: "POST"});
    const logsPanel = document.getElementById("logsPanel");
    if (res.ok && logsPanel) {
        await window.refreshLogsPanel();
    } else {
        console.error("[Settings] Failed to clear logs");
    }
};

window.clearSongsDB = async function () {
    const res = await fetch(`/api/settings/clearSongs`, {method: "POST"});
    if (res.ok) {
        console.log("[Settings] All songs deleted");
        await window.refreshLogsPanel();
    } else {
        console.error("[Settings] Failed to clear songs DB");
    }
};


window.refreshLogsPanel = async function () {
    const res = await fetch(`/api/settings/logs`);
    if (!res.ok)
        return;

    const logs = await res.json(); // backend returns array of strings
    const logsPanel = document.getElementById("logsPanel");
    if (!logsPanel)
        return;

    logsPanel.innerHTML = "";
    logs.forEach(line => {
        const p = document.createElement("p");
        p.textContent = line;
        logsPanel.appendChild(p);
    });

    logsPanel.scrollTop = logsPanel.scrollHeight;
};

window.refreshSettingsUI = async function () {
    const res = await fetch(`/api/settings`);
    if (!res.ok)
        return;

    const settings = await res.json();
    const pathElem = document.getElementById("musicLibraryPath");
    if (pathElem && settings.libraryPath)
        pathElem.textContent = settings.libraryPath;

    // Fetch logs automatically
    if (window.refreshLogsPanel)
        await window.refreshLogsPanel();
};
 