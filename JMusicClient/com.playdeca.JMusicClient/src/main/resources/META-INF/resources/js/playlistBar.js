
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
        if (response.ok) {
            closeModal();
            htmx.ajax('GET', '/api/music/ui/playlists-fragment', {target: '#sidebarPlaylistList'});
        } else {
            alert('Failed to create playlist');
        }
    });
});