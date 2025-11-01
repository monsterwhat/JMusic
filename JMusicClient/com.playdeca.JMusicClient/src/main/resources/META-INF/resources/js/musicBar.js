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
const formatTime = s => {
    if (s === null || s === undefined || isNaN(s)) {
        return "0:00";
    }
    return `${Math.floor(s / 60)}:${Math.floor(s % 60).toString().padStart(2, '0')}`;
};

function throttle(func, delay) {
    let lastCall = 0;
    return function (...args) {
        const now = new Date().getTime();
        if (now - lastCall < delay) {
            return;
        }
        lastCall = now;
        return func(...args);
    };
}

function applyMarqueeEffect(elementId, text) {
    const element = document.getElementById(elementId);
    if (element) {
        element.innerText = text; // Set text first
        // Check if text overflows
        if (element.scrollWidth > element.clientWidth) {
            element.classList.add('marquee');
            element.classList.remove('no-scroll');
        } else {
            element.classList.remove('marquee');
            element.classList.add('no-scroll');
        }
    }
}

function updateMusicBar() {
    const {songName, artist, playing, currentTime, duration, volume, shuffleEnabled, repeatEnabled} = musicState;

    const titleEl = document.getElementById('songTitle');
    const artistEl = document.getElementById('songArtist');
    if (titleEl)
        titleEl.innerText = songName ?? "Unknown Title";
    if (artistEl)
        artistEl.innerText = artist ?? "Unknown Artist";

    // Apply marquee effect for mobile elements
    applyMarqueeEffect('songTitleMobile', songName ?? "Unknown Title");
    applyMarqueeEffect('songArtistMobile', artist ?? "Unknown Artist");

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
const throttledSendWS = throttle(sendWS, 300); // Send a message at most every 300ms

function sendWS(type, payload) {
    if (ws && ws.readyState === WebSocket.OPEN)
        ws.send(JSON.stringify({type, payload}));
}

audio.ontimeupdate = () => {
    if (!draggingSeconds) {
        musicState.currentTime = audio.currentTime;
        throttledSendWS("seek", {value: audio.currentTime});
        updateMusicBar(); // Update UI more frequently

    }
};

audio.onended = () => {
    console.log("[musicBar.js] audio.onended: Song ended, sending 'next'");
    // tell backend to go to next song
    sendWS("next", {});

    // immediately fetch the new current song from backend
    fetch('/api/music/playback/current')
            .then(r => r.json())
            .then(json => {
                if (!json.data || !json.data.id)
                    return;

                // update the audio element with new song
                UpdateAudioSource(json.data, true, json.data.currentTime ?? 0);

                // force UI refresh
                refreshSongTable();
            })
            .catch(console.error);
};


// ---------------- Update Audio Source ----------------
function UpdateAudioSource(song, play = false, backendTime = 0) {
    console.log("[musicBar.js] UpdateAudioSource called with song:", song, "play:", play, "backendTime:", backendTime);
    if (!song || !song.id)
        return;

    const sameSong = String(musicState.currentSongId) === String(song.id);
    musicState.currentSongId = song.id;
    musicState.songName = song.title ?? "Unknown Title";
    musicState.artist = song.artist ?? "Unknown Artist";
    musicState.currentTime = (sameSong || backendTime !== 0) ? (backendTime ?? 0) : 0;
    musicState.duration = song.durationSeconds ?? 0; // Prioritize duration from backend
    updateMusicBar();
    updatePageTitle({name: musicState.songName, artist: musicState.artist});

    audio.src = `/api/music/stream/${song.id}`;
    audio.load();
    audio.volume = musicState.volume;

    audio.onloadedmetadata = () => {
        console.log("[musicBar.js] onloadedmetadata: audio.duration=", audio.duration);
        // Only update musicState.duration from audio.duration if it's a valid, non-zero number
        if (typeof audio.duration === 'number' && !isNaN(audio.duration) && audio.duration > 0) {
            musicState.duration = audio.duration;
        }
        audio.currentTime = musicState.currentTime; // set backend time after duration is known
        if (play)
            audio.play().catch(console.error);
        updateMusicBar();
        console.log("[musicBar.js] onloadedmetadata: musicState.duration (final)=", musicState.duration);
    };
}
    
    
    // ---------------- WebSocket ----------------
    let ws;
    function connectWS() {
        ws = new WebSocket((location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/api/music/ws');
        ws.onopen = () => console.log('[WS] Connected');
        ws.onclose = () => {
            console.log('[WS] Disconnected. Attempting to reconnect...');
            setTimeout(connectWS, 1000); // Reconnect after 1 second
        };
        ws.onerror = e => console.error('[WS] Error', e);
        ws.onmessage = handleWSMessage;
    }
    
    function handleWSMessage(msg) {
        let message;
        try {
            message = JSON.parse(msg.data);
        } catch (e) {
            return console.error(e);
        }
    
        if (message.type === 'state') {
            const state = message.payload;
            const songChanged = String(state.currentSongId) !== String(musicState.currentSongId);
            const playChanged = state.playing !== musicState.playing;
    
                        musicState.currentSongId = state.currentSongId;
                        // musicState.songName = state.songName; // Removed: Prioritize API for songName
                        musicState.artist = state.artist ?? "Unknown Artist";
                        musicState.playing = state.playing;
                        musicState.currentTime = state.currentTime;
                        musicState.duration = state.duration;
                        musicState.volume = state.volume;
                        musicState.shuffleEnabled = state.shuffleEnabled;
                        musicState.repeatEnabled = state.repeatEnabled;
            
                        console.log("[WS] handleWSMessage: songChanged=", songChanged, "state.currentSongId=", state.currentSongId, "musicState.currentSongId=", musicState.currentSongId);
            
                        if (songChanged) {
                            console.log("[WS] handleWSMessage: Song changed, fetching current song details.");
                            fetch(`/api/music/playback/current`)
                                .then(r => r.json())
                                .then(json => {
                                    if (json.data) {
                                        console.log("[WS] handleWSMessage: Calling UpdateAudioSource with data:", json.data);
                                        UpdateAudioSource(json.data, state.playing, state.currentTime ?? 0);
                                        refreshSongTable();
                                        htmx.trigger('body', 'queueChanged');
                                    }
                                });            } else if (playChanged) { // If only play state changed, re-initialize audio source to ensure duration is correct
                fetch(`/api/music/playback/current`)
                    .then(r => r.json())
                    .then(json => {
                        if (json.data) {
                            UpdateAudioSource(json.data, state.playing, state.currentTime ?? 0);
                        }
                    });
            }
    
            if (!draggingSeconds) {
                if (Math.abs(audio.currentTime - musicState.currentTime) > 1) {
                    audio.currentTime = musicState.currentTime;
                }
            }
    
            if (playChanged) {
                audio.currentTime = musicState.currentTime; // Synchronize audio element's current time
                if (musicState.playing) {
                    audio.play().catch(console.error);
                } else {
                    audio.pause();
                }
            }
    
            updateMusicBar();
        }
    }
    
    function sendWS(type, payload) {
        if (ws && ws.readyState === WebSocket.OPEN)
            ws.send(JSON.stringify({type, payload}));
    }
    
    // ---------------- Controls ----------------
        function setPlaybackTime(newTime, fromClient = false) {
            musicState.currentTime = newTime;
            audio.currentTime = newTime;
            updateMusicBar();
            if (fromClient) {
                sendWS('seek', {value: newTime});
        }
        }

        function handleSeek(newTime) {
            musicState.currentTime = newTime; // Immediate UI update
            updateMusicBar();
            sendWS('seek', {value: newTime});
            console.log("[musicBar.js] User manually seeked to sec=", newTime);
        }

        function bindTimeSlider() {
            const slider = document.querySelector('input[name="seconds"]');
            if (!slider)
                return;

            slider.onmousedown = slider.ontouchstart = () => draggingSeconds = true;
            slider.onmouseup = slider.ontouchend = () => {
                draggingSeconds = false;
                handleSeek(parseInt(slider.value, 10));
            };
            slider.oninput = e => {
                setPlaybackTime(parseInt(e.target.value, 10), true);
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
                sendWS('volume', {value: vol});
            };
            slider.oninput = e => {
                const vol = parseInt(e.target.value, 10) / 100;
                audio.volume = vol;
                sendWS('volume', {value: vol});
            };
        }

        function bindPlaybackButtons() {
            const apiPost = (path) => fetch(`/api/music/playback/${path}`, {method: 'POST'});

            document.getElementById('playPauseBtn').onclick = () => {
                console.log("[musicBar.js] playPauseBtn clicked");
                apiPost('toggle');
            };
            document.getElementById('prevBtn').onclick = () => {
                console.log("[musicBar.js] prevBtn clicked");
                apiPost('previous');
            };
            document.getElementById('nextBtn').onclick = () => {
                console.log("[musicBar.js] nextBtn clicked");
                apiPost('next');
            };
            document.getElementById('shuffleBtn').onclick = () => {
                console.log("[musicBar.js] shuffleBtn clicked");
                apiPost('shuffle');
            };
            document.getElementById('repeatBtn').onclick = () => {
                console.log("[musicBar.js] repeatBtn clicked");
                apiPost('repeat');
            };
        }


// ---------------- UI ----------------
        function refreshSongTable() {
            console.log("[musicBar.js] refreshSongTable called");
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

        // Theme Toggle Logic
        function applyThemePreference() {
            const themeToggle = document.getElementById('themeToggle');
            const themeIcon = document.getElementById('themeIcon');
            if (!themeToggle || !themeIcon) return;

            let isDarkMode = localStorage.getItem('darkMode');

            if (isDarkMode === null) {
                // No preference saved, check system preference
                isDarkMode = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'true' : 'false';
            }

            if (isDarkMode === 'true') {
                document.documentElement.classList.add('is-dark-mode');
                themeIcon.classList.remove('pi-sun');
                themeIcon.classList.add('pi-moon');
            } else {
                document.documentElement.classList.remove('is-dark-mode');
                themeIcon.classList.remove('pi-moon');
                themeIcon.classList.add('pi-sun');
            }
        }

        // ---------------- Init ----------------

        document.addEventListener('DOMContentLoaded', () => {

            bindMusicBarControls();

            connectWS();

            applyThemePreference(); // Apply theme on load

            const themeToggle = document.getElementById('themeToggle');
            if (themeToggle) {
                themeToggle.addEventListener('click', () => {
                    const isDarkMode = document.documentElement.classList.contains('is-dark-mode');
                    localStorage.setItem('darkMode', !isDarkMode);
                    applyThemePreference(); // Re-apply to update icon and class
                });
            }

        });