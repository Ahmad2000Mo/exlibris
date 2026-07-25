// Get query parameters from URL
const params = new URLSearchParams(window.location.search);

document.getElementById('valTitle').textContent = params.get('title') || 'Unbekanntes Buch';
document.getElementById('valAuthor').textContent = params.get('author') || 'Unbekannt';
document.getElementById('valCode').textContent = params.get('code') || '-';
document.getElementById('valDate').textContent = params.get('date') || new Date().toLocaleDateString('de-DE');
