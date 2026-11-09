'use strict';

// Zeigt eine Fehlermeldung, wenn der Server mit ?error=... zurueckleitet.
const messages = {
  '1': 'Benutzername oder Passwort ist falsch.',
  'server': 'Anmeldung zurzeit nicht möglich (Serverfehler). Bitte später erneut versuchen.'
};

const error = new URLSearchParams(window.location.search).get('error');
const banner = document.getElementById('errorBanner');
if (error && banner) {
  banner.textContent = '⚠️ ' + (messages[error] || messages['1']);
  banner.style.display = 'block';
}

// Bereits angemeldet? Dann direkt zum Dashboard.
fetch('session-status')
  .then((res) => res.json())
  .then((data) => {
    if (data.loggedIn) window.location.href = 'dashboard.html';
  })
  .catch(() => { /* Login-Seite bleibt einfach stehen */ });
