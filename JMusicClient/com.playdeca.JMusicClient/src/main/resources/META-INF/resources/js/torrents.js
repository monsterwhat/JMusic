document.addEventListener('DOMContentLoaded', () => {
    const torrentListContainer = document.getElementById('torrent-list');

    // Helper function to handle form responses and display messages
    window.handleTorrentFormResponse = (event, successMessage, errorMessage) => {
        if (event.detail.successful) {
            htmx.trigger(document.body, 'showMessage', {message: successMessage, type: 'is-success'});
        } else {
            htmx.trigger(document.body, 'showMessage', {message: errorMessage, type: 'is-danger'});
        }
    };

    if (torrentListContainer) {
        const socket = new WebSocket('ws://localhost:8080/api/torrents/ws'); // Adjust port if necessary

        socket.onopen = (event) => {
            console.log('WebSocket opened:', event);
        };

        socket.onmessage = (event) => {
            const message = JSON.parse(event.data);
            console.log('WebSocket message received:', message);

            if (message.type === 'new_torrent') {
                // Trigger HTMX to refresh the torrent list
                htmx.trigger(torrentListContainer, 'refreshTorrents');
            } else if (message.type === 'rating_update') {
                // Find the torrent and update its rating display
                const torrentId = message.payload.torrentId;
                const liked = message.payload.liked;
                const torrentRow = torrentListContainer.querySelector(`tr[data-torrent-id="${torrentId}"]`);
                if (torrentRow) {
                    // Example: update a rating display element
                    const ratingElement = torrentRow.querySelector('.torrent-rating');
                    if (ratingElement) {
                        ratingElement.textContent = liked ? 'Liked' : 'Disliked';
                    }
                }
            } else if (message.type === 'peer_event') {
                console.log('Peer event:', message.payload.eventType, 'from', message.payload.peerId);
                // Potentially update peer status or a peer list
            } else if (message.type === 'torrent_deleted') {
                const torrentId = message.payload.torrentId;
                const torrentRow = torrentListContainer.querySelector(`tr[data-torrent-id="${torrentId}"]`);
                if (torrentRow) {
                    torrentRow.remove();
                }
            }
        };

        socket.onclose = (event) => {
            console.log('WebSocket closed:', event);
        };

        socket.onerror = (event) => {
            console.error('WebSocket error:', event);
        };

        // Add a custom event listener for HTMX to refresh the torrent list
        torrentListContainer.addEventListener('refreshTorrents', () => {
            htmx.ajax('GET', '/ui/torrents/list', {
                target: '#torrent-list',
                swap: 'outerHTML'
            });
        });

        // Listen for custom showMessage event to display notifications
        document.body.addEventListener('showMessage', (event) => {
            const messageContainer = document.getElementById('message-container');
            if (messageContainer) {
                const message = event.detail.message;
                const type = event.detail.type || 'is-info'; // Default to info

                const notification = document.createElement('div');
                notification.className = `notification ${type}`;
                notification.innerHTML = `
                    <button class="delete"></button>
                    ${message}
                `;

                messageContainer.appendChild(notification);

                // Add dismiss functionality
                notification.querySelector('.delete').addEventListener('click', () => {
                    notification.remove();
                });

                // Auto-dismiss after 5 seconds
                setTimeout(() => {
                    if (notification.parentNode) {
                        notification.remove();
                    }
                }, 5000);
            }
        });
    }
});