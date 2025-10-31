
document.addEventListener('DOMContentLoaded', () => {
    const modal = document.getElementById('createPlaylistModal');
    const openBtn = document.getElementById('createPlaylistBtn');
    const closeBtn = document.getElementById('closeCreatePlaylistModal');
    const cancelBtn = document.getElementById('cancelPlaylistBtn');
    const saveBtn = document.getElementById('savePlaylistBtn');

    const openModal = () => modal.classList.add('is-active');
    const closeModal = () => modal.classList.remove('is-active');

    openBtn.addEventListener('click', openModal);
    closeBtn.addEventListener('click', closeModal);
    cancelBtn.addEventListener('click', closeModal);

    saveBtn.addEventListener('click', async () => {
        const name = document.getElementById('newPlaylistName').value.trim();
        const description = document.getElementById('newPlaylistDescription').value.trim();
        if (!name)
            return alert('Please enter a name');
        const response = await fetch('/api/music/playlists', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({name, description})
        });
        const json = await response.json();
        if (response.ok && json.data) {
            closeModal();
            htmx.ajax('GET', '/api/music/ui/playlists-fragment', {target: '#sidebarPlaylistList'});
        } else {
            alert('Failed to create playlist: ' + json.error);
        }
    });

    // Mobile Sidebar Toggle Logic
    const sidebarToggle = document.getElementById('sidebarToggle');
    const playlistSidebar = document.getElementById('playlistSidebar');
    const body = document.body;

    if (sidebarToggle && playlistSidebar) {
        // Create overlay element
        const sidebarOverlay = document.createElement('div');
        sidebarOverlay.classList.add('sidebar-overlay');
        body.appendChild(sidebarOverlay);

        const toggleSidebar = () => {
            playlistSidebar.classList.toggle('is-active');
            sidebarOverlay.classList.toggle('is-active');
            // Prevent scrolling when sidebar is open
            body.classList.toggle('is-clipped', playlistSidebar.classList.contains('is-active'));
        };

        sidebarToggle.addEventListener('click', toggleSidebar);
        sidebarOverlay.addEventListener('click', toggleSidebar); // Close when overlay is clicked
    }
});