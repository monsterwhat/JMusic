const audio = document.getElementById('audioPlayer');

// ---------------- Load persisted state ----------------
const savedSongId = localStorage.getItem("currentSongId");
const savedVolume = parseFloat(localStorage.getItem("musicVolume") ?? 0.8);
const savedTime = parseFloat(localStorage.getItem("currentTime") ?? "0");


// ---------------- State ----------------
const musicState = {
    currentSongId: savedSongId,
    songName: "Loading...",
    artist: "Unknown Artist",
    playing: true,
    currentTime: savedTime,
    duration: 0,
    volume: savedVolume,
    shuffleEnabled: false,
    repeatEnabled: false,
    userInitiated: false
};

let draggingSeconds = false;
let draggingVolume = false;
let userSetPlayback = false;

audio.volume = musicState.volume;

// ---------------- Helpers ----------------
const formatTime = s => `${Math.floor(s / 60)}:${Math.floor(s % 60).toString().padStart(2, '0')}`;
const logState = action => console.log("[MUSIC BAR] " + action, JSON.parse(JSON.stringify(musicState)));

function updateMusicBar() {
    const {songName, artist, playing, currentTime, duration, volume, shuffleEnabled, repeatEnabled} = musicState;
    document.getElementById('songTitle').innerText =
            `${musicState.songName ?? "Unknown Title"} — ${musicState.artist ?? "Unknown Artist"}`;

    document.getElementById('playPauseIcon').className = playing
            ? "pi pi-pause button is-warning is-rounded"
            : "pi pi-play button is-success is-rounded";
    document.getElementById('currentTime').innerText = formatTime(currentTime ?? 0);
    document.getElementById('totalTime').innerText = formatTime(duration ?? 0);

    const timeSlider = document.querySelector('input[name="seconds"]');
    if (timeSlider) {
        timeSlider.max = duration ?? 0;
        if (!draggingSeconds)
            timeSlider.value = currentTime ?? 0;
    }
    const volumeSlider = document.querySelector('input[name="level"]');
    if (volumeSlider && !draggingVolume)
        volumeSlider.value = volume * 100;

    document.getElementById('shuffleIcon').className = shuffleEnabled
            ? "pi pi-sort-alt-slash has-text-success"
            : "pi pi-sort-alt";
    document.getElementById('repeatIcon').className = repeatEnabled
            ? "pi pi-refresh pi-spin has-text-success"
            : "pi pi-refresh";
}

// ---------------- Audio Events ----------------
audio.ontimeupdate = () => {
    if (!draggingSeconds) {
        musicState.currentTime = audio.currentTime;
        localStorage.setItem("currentTime", audio.currentTime);
        updateMusicBar();
        if (!audio._lastSeekSent || Math.abs(audio.currentTime - audio._lastSeekSent) > 0.3) {
            sendWS({action: 'seek', value: audio.currentTime});
            audio._lastSeekSent = audio.currentTime;
        }
    }
};
audio.onplay = () => {
    if (userSetPlayback) {
        musicState.playing = true;
        updateMusicBar();
        sendWS({action: 'play'});
    }
};
audio.onpause = () => {
    if (userSetPlayback) {
        musicState.playing = false;
        updateMusicBar();
        sendWS({action: 'pause'});
    }
};
audio.onloadedmetadata = () => {
    musicState.duration = audio.duration;
    updateMusicBar();
};
audio.onended = () => sendWS({action: 'next'});

// ---------------- Update Audio Source ----------------
function UpdateAudioSource(songId, play = false) {
    if (!songId)
        return;

    const sameSong = String(musicState.currentSongId) === String(songId);
    const alreadyLoaded = audio.src && audio.src.includes(`/api/music/stream/${songId}`);

    // ✅ Skip reloading same song
    if (sameSong && alreadyLoaded) {
        if (play && audio.paused)
            audio.play().catch(console.warn);
        return;
    }

    // Update state
    musicState.currentSongId = songId;
    localStorage.setItem("currentSongId", songId);
    musicState.songName = "Loading...";
    musicState.artist = "Loading...";
    updateMusicBar();
    updatePageTitle(null); // show "JMusic Home" while loading

    // ✅ Reload only if needed
    if (!alreadyLoaded) {
        audio.src = `/api/music/stream/${songId}`;
        audio.load();
    }

    fetch(`/api/music/current`)
            .then(r => r.json())
            .then(data => {
                musicState.songName = data?.title ?? "Unknown Title";
                musicState.artist = data?.artist ?? "Unknown Artist";
                updateMusicBar();
                // ✅ update page title after song loads
                updatePageTitle({name: musicState.songName, artist: musicState.artist});
            })
            .catch(() => {
                musicState.songName = "Unknown Title";
                musicState.artist = "Unknown Artist";
                updateMusicBar();
                updatePageTitle(null);
            });

    if (play)
        audio.play().catch(e => console.warn("Audio play blocked", e));
}

// ---------------- WebSocket ----------------
let ws;
function connectWS() {
    ws = new WebSocket((location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/api/music/ws/state');
    ws.onopen = () => console.log('[WS] Connected');
    ws.onclose = () => setTimeout(connectWS, 1000);
    ws.onerror = e => console.error('[WS] Error', e);
    ws.onmessage = handleWSMessage;
}

function handleWSMessage(msg) {
    let state;
    try {
        state = JSON.parse(msg.data);
    } catch (e) {
        return console.error(e);
    }

    const songChanged = String(state.currentSongId) !== String(musicState.currentSongId);
    const playChanged = state.playing !== musicState.playing;

    musicState.shuffleEnabled = state.shuffleEnabled ?? musicState.shuffleEnabled;
    musicState.repeatEnabled = state.repeatEnabled ?? musicState.repeatEnabled;
    musicState.volume = state.volume ?? musicState.volume;

    if (songChanged) {
        UpdateAudioSource(state.currentSongId, state.playing);
        musicState.currentTime = state.currentTime ?? 0;
        audio.currentTime = musicState.currentTime;
        refreshSongTable();
    } else if (!draggingSeconds && state.currentTime !== undefined) {
        audio.currentTime = state.currentTime;
        musicState.currentTime = state.currentTime;
    }

    if (playChanged) {
        musicState.playing = state.playing;
        if (state.playing && musicState.userInitiated)
            audio.play().catch(console.error);
        else
            audio.pause();
    }

    updateMusicBar();
}

function sendWS(obj) {
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify(obj));
        console.log("[WS] Sent:", obj);
    }
}

// ---------------- Controls ----------------
function bindTimeSlider() {
    const slider = document.querySelector('input[name="seconds"]');
    if (!slider)
        return;
    slider.onmousedown = slider.ontouchstart = () => draggingSeconds = true;
    slider.onmouseup = slider.ontouchend = e => {
        draggingSeconds = false;
        const sec = parseFloat(e.target.value);
        musicState.currentTime = sec;
        audio.currentTime = sec;
        localStorage.setItem("currentTime", sec);
        sendWS({action: 'seek', value: sec});
        updateMusicBar();
        logState("Seek performed");
    };
    slider.oninput = e => {
        if (draggingSeconds)
            document.getElementById('currentTime').innerText = formatTime(parseFloat(e.target.value));
    };
}

function bindVolumeSlider() {
    const slider = document.querySelector('input[name="level"]');
    if (!slider)
        return;
    slider.onmousedown = slider.ontouchstart = () => draggingVolume = true;
    slider.onmouseup = slider.ontouchend = () => {
        draggingVolume = false;
        const vol = parseInt(slider.value, 10) / 100;
        musicState.volume = vol;
        audio.volume = vol;
        localStorage.setItem("musicVolume", vol);
        sendWS({action: 'volume', value: vol});
        updateMusicBar();
    };
    slider.oninput = e => {
        const vol = parseInt(slider.value, 10) / 100;
        musicState.volume = vol;
        audio.volume = vol;
        localStorage.setItem("musicVolume", vol);
        sendWS({action: 'volume', value: vol});
    };
}

function bindPlaybackButtons() {
    const btn = document.getElementById('playPauseBtn');
    btn.onclick = () => {
        musicState.playing = !musicState.playing;
        musicState.userInitiated = true;
        userSetPlayback = true;
        if (musicState.playing)
            audio.play().catch(console.error);
        else
            audio.pause();
        updateMusicBar();
        sendWS({action: 'toggle-play'});
        logState("Play/Pause toggled");
        setTimeout(() => userSetPlayback = false, 200);
    };
    document.getElementById('prevBtn').onclick = () => sendWS({action: 'previous'});
    document.getElementById('nextBtn').onclick = () => sendWS({action: 'next'});
    document.getElementById('shuffleBtn').onclick = () => sendWS({action: 'shuffle'});
    document.getElementById('repeatBtn').onclick = () => sendWS({action: 'repeat'});
}

function refreshSongTable() {
    htmx.ajax('GET', '/api/music/ui/songs-fragment', {target: '#songTable tbody', swap: 'outerHTML'});
}

function updatePageTitle(song) {
    if (!song) {
        document.getElementById('pageTitle').innerText = "JMusic Home";
        document.title = "JMusic Home";
        return;
    }
    document.getElementById('pageTitle').innerText = `${song.name} — ${song.artist}`;
    document.title = `${song.name} — ${song.artist}`;
}


function bindMusicBarControls() {
    bindTimeSlider();
    bindVolumeSlider();
    bindPlaybackButtons();
}

// ---------------- Init ----------------
document.addEventListener('DOMContentLoaded', () => {
    bindMusicBarControls();
    connectWS();

    if (musicState.currentSongId) {
        // ✅ restore source on reload
        UpdateAudioSource(musicState.currentSongId, false);

        // ✅ restore position
        audio.currentTime = musicState.currentTime || 0;

        audio.play().catch(console.warn);
    } else {
        console.log("[MUSIC BAR] No saved song to restore");
    }
});
