const audio = document.getElementById('audioPlayer');

// ---------------- State ----------------
const musicState = {
    currentSongId: null,
    songName: "Loading...",
    artist: "Unknown Artist",
    playing: false,
    currentTime: 0,
    duration: 0,
    volume: 0.8,
    shuffleEnabled: false,
    repeatEnabled: false
};

let draggingSeconds = false;
let draggingVolume = false;

audio.volume = musicState.volume;

// ---------------- Helpers ----------------
const formatTime = s => `${Math.floor(s / 60)}:${Math.floor(s % 60).toString().padStart(2, '0')}`;

function updateMusicBar() {
    const {songName, artist, playing, currentTime, duration, volume, shuffleEnabled, repeatEnabled} = musicState;

    const titleEl = document.getElementById('songTitle');
    const artistEl = document.getElementById('songArtist');
    if (titleEl)
        titleEl.innerText = songName ?? "Unknown Title";
    if (artistEl)
        artistEl.innerText = artist ?? "Unknown Artist";

    document.getElementById('playPauseIcon').className = playing
            ? "pi pi-pause button is-warning is-rounded is-large"
            : "pi pi-play button is-success is-rounded is-large";

    document.getElementById('currentTime').innerText = formatTime(currentTime);
    document.getElementById('totalTime').innerText = formatTime(duration);

    const timeSlider = document.querySelector('input[name="seconds"]');
    if (timeSlider) {
        timeSlider.max = duration;
        if (!draggingSeconds)
            timeSlider.value = currentTime;
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
        if (!audio._lastSeekSent || Math.abs(audio.currentTime - audio._lastSeekSent) > 0.3) {
            sendWS({action: 'seek', value: audio.currentTime});
            audio._lastSeekSent = audio.currentTime;
        }
        updateMusicBar();
    }
};

audio.onended = () => {
    // tell backend to go to next song
    sendWS({action: 'next'});

    // immediately fetch the new current song from backend
    fetch('/api/music/current')
            .then(r => r.json())
            .then(data => {
                if (!data || !data.id)
                    return;

                // update the audio element with new song
                UpdateAudioSource(data.id, true, data.currentTime ?? 0);

                // force UI refresh
                refreshSongTable();
            })
            .catch(console.error);
};


// ---------------- Update Audio Source ----------------
function UpdateAudioSource(songId, play = false, backendTime = 0) {
    if (!songId)
        return;

    const sameSong = String(musicState.currentSongId) === String(songId);
    musicState.currentSongId = songId;
    musicState.songName = "Loading...";
    musicState.artist = "Loading...";
    musicState.currentTime = backendTime ?? 0;
    musicState.duration = 0;
    updateMusicBar();
    updatePageTitle(null);

    audio.src = `/api/music/stream/${songId}`;
    audio.load();
    audio.volume = musicState.volume;

    audio.onloadedmetadata = () => {
        audio.currentTime = musicState.currentTime; // set backend time
        if (play)
            audio.play().catch(console.warn);
        musicState.duration = audio.duration;
        updateMusicBar();
    };

    fetch(`/api/music/current`)
            .then(r => r.json())
            .then(data => {
                musicState.songName = data?.title ?? "Unknown Title";
                musicState.artist = data?.artist ?? "Unknown Artist";

                // Always overwrite currentTime if backend sent it
                if (data?.currentTime !== undefined) {
                    musicState.currentTime = data.currentTime;
                    if (!draggingSeconds)
                        audio.currentTime = musicState.currentTime;
                }

                updateMusicBar();
                updatePageTitle({name: musicState.songName, artist: musicState.artist});
            })
            .catch(() => {
                musicState.songName = "Unknown Title";
                musicState.artist = "Unknown Artist";
                updateMusicBar();
                updatePageTitle(null);
            });
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
    audio.volume = musicState.volume; // make sure volume is applied immediately

    if (songChanged) {
        UpdateAudioSource(state.currentSongId, state.playing, state.currentTime ?? 0);
        musicState.playing = true;
        refreshSongTable();
    } else if (!draggingSeconds && state.currentTime !== undefined) {
        audio.currentTime = state.currentTime;
        musicState.currentTime = state.currentTime;
    }

    if (playChanged) {
        musicState.playing = state.playing;
        if (state.playing)
            audio.play().catch(console.error);
        else
            audio.pause();
    }

    updateMusicBar();
}

function sendWS(obj) {
    if (ws && ws.readyState === WebSocket.OPEN)
        ws.send(JSON.stringify(obj));
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
        sendWS({action: 'seek', value: sec});
        updateMusicBar();
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
        sendWS({action: 'volume', value: vol});
        updateMusicBar();
    };
    slider.oninput = e => {
        const vol = parseInt(slider.value, 10) / 100;
        musicState.volume = vol;
        audio.volume = vol;
        sendWS({action: 'volume', value: vol});
    };
}

function bindPlaybackButtons() {
    const apiPost = (path) => fetch(`/api/music/${path}`, {method: 'POST'});

    document.getElementById('playPauseBtn').onclick = async () => {
        // Toggle between play/pause depending on current state
        const currentSong = await fetch('/api/music/current').then(r => r.json());
        apiPost('toggle-play');
    };

    document.getElementById('prevBtn').onclick = () => apiPost('previous');
    document.getElementById('nextBtn').onclick = () => apiPost('next');
    document.getElementById('shuffleBtn').onclick = async () => {
        const currentSong = await fetch('/api/music/current').then(r => r.json());
        // Toggle shuffle based on current state (you might want to store it separately)
        apiPost(`shuffle/${!currentSong?.shuffle}`);
    };
    document.getElementById('repeatBtn').onclick = async () => {
        const currentSong = await fetch('/api/music/current').then(r => r.json());
        // Toggle repeat based on current state
        apiPost(`repeat/${!currentSong?.repeat}`);
    };
}


// ---------------- UI ----------------
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
    document.title = `${song.name} : ${song.artist}`;
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
});
