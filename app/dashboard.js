'use strict';

// ===================================================================
// Zustand
// ===================================================================

const state = {
  csrfToken: null,
  activeMember: null, // { code, name }
  borrowItems: [],    // [{ code, title, author }]
  returnItems: [],    // [{ code, title, author, memberCode, memberName, overdue, timeDiffText }]
  busy: false         // verhindert Doppelklick auf "bestaetigen"
};

const $ = (id) => document.getElementById(id);

const el = {
  tabBorrowBtn: $('tabBorrowBtn'),
  tabReturnBtn: $('tabReturnBtn'),
  borrowView: $('borrowView'),
  returnView: $('returnView'),

  borrowMemberInput: $('borrowMemberInput'),
  btnCheckMember: $('btnCheckMember'),
  borrowMemberSection: $('borrowMemberSection'),
  activeMemberCard: $('activeMemberCard'),
  activeMemberName: $('activeMemberName'),
  btnResetMember: $('btnResetMember'),
  borrowBookSection: $('borrowBookSection'),
  borrowBookInput: $('borrowBookInput'),
  btnAddBookToBorrow: $('btnAddBookToBorrow'),
  borrowListSection: $('borrowListSection'),
  borrowItemsList: $('borrowItemsList'),
  borrowCount: $('borrowCount'),
  btnConfirmBorrow: $('btnConfirmBorrow'),
  borrowEmptyState: $('borrowEmptyState'),

  returnBookInput: $('returnBookInput'),
  btnCheckReturnBook: $('btnCheckReturnBook'),
  returnListSection: $('returnListSection'),
  returnItemsList: $('returnItemsList'),
  returnCount: $('returnCount'),
  btnConfirmReturn: $('btnConfirmReturn'),
  returnEmptyState: $('returnEmptyState'),

  currentUserDisplay: $('currentUserDisplay'),
  logoutBtn: $('logoutBtn'),
  toast: $('toast'),
  toastIcon: $('toastIcon'),
  toastText: $('toastText')
};

// ===================================================================
// Server-Kommunikation
// ===================================================================

/**
 * Ruft die API auf und liefert immer ein Objekt { success, message, ... } zurueck.
 * Bei abgelaufener Session geht es zurueck zum Login.
 */
async function api(path, formData) {
  const options = { headers: { 'Accept': 'application/json' } };
  if (formData) {
    options.method = 'POST';
    options.body = new URLSearchParams(formData);
    options.headers['X-CSRF-Token'] = state.csrfToken || '';
  }

  let res;
  try {
    res = await fetch(path, options);
  } catch (err) {
    return { success: false, message: 'Server nicht erreichbar. Netzwerk prüfen.' };
  }

  if (res.status === 401) {
    window.location.href = 'login.html';
    return { success: false, message: 'Sitzung abgelaufen.' };
  }

  try {
    return await res.json();
  } catch (err) {
    return { success: false, message: `Unerwartete Antwort vom Server (HTTP ${res.status}).` };
  }
}

// ===================================================================
// Anmeldung
// ===================================================================

async function initDashboard() {
  const data = await api('session-status');
  if (!data.loggedIn) {
    window.location.href = 'login.html';
    return;
  }
  state.csrfToken = data.csrfToken;
  el.currentUserDisplay.textContent = `👤 ${data.name || data.user}`;
  el.borrowMemberInput.focus();
}

el.logoutBtn.addEventListener('click', async () => {
  await api('logout', {});
  window.location.href = 'login.html';
});

// ===================================================================
// Tabs
// ===================================================================

function showTab(tab) {
  const borrow = tab === 'borrow';
  el.tabBorrowBtn.classList.toggle('active', borrow);
  el.tabReturnBtn.classList.toggle('active', !borrow);
  el.borrowView.style.display = borrow ? 'block' : 'none';
  el.returnView.style.display = borrow ? 'none' : 'block';
  if (!borrow) {
    el.returnBookInput.focus();
  } else if (state.activeMember) {
    el.borrowBookInput.focus();
  } else {
    el.borrowMemberInput.focus();
  }
}

el.tabBorrowBtn.addEventListener('click', () => showTab('borrow'));
el.tabReturnBtn.addEventListener('click', () => showTab('return'));

/** Enter im Eingabefeld = Klick auf den Button (Barcode-Scanner senden Enter). */
function onEnter(input, handler) {
  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handler();
    }
  });
}

// ===================================================================
// Ausleihe
// ===================================================================

async function checkMember() {
  const code = el.borrowMemberInput.value.trim();
  if (!code) return;

  const data = await api(`borrow?action=checkMember&code=${encodeURIComponent(code)}`);
  if (!data.success) {
    showToast(data.message || 'Mitglied ungültig.', 'error');
    el.borrowMemberInput.select();
    return;
  }

  state.activeMember = { code: data.code, name: data.name };
  el.activeMemberName.textContent = `${data.name} (${data.code})`;
  el.borrowMemberSection.style.display = 'none';
  el.activeMemberCard.style.display = 'flex';
  el.borrowBookSection.style.display = 'block';
  el.borrowEmptyState.style.display = 'none';
  el.borrowMemberInput.value = '';
  el.borrowBookInput.focus();
  showToast('Mitgliedsausweis erfasst.', 'success');
}

function resetMember() {
  state.activeMember = null;
  state.borrowItems = [];
  renderBorrowList();
  el.borrowMemberSection.style.display = 'block';
  el.activeMemberCard.style.display = 'none';
  el.borrowBookSection.style.display = 'none';
  el.borrowEmptyState.style.display = 'flex';
  el.borrowMemberInput.focus();
}

async function addBorrowBook() {
  if (!state.activeMember) return;
  const code = normalizeCode(el.borrowBookInput.value);
  if (!code) return;

  if (state.borrowItems.some((item) => item.code === code)) {
    showToast('Dieses Exemplar ist bereits in der Liste.', 'error');
    el.borrowBookInput.select();
    return;
  }

  const data = await api(`borrow?action=checkExemplar&code=${encodeURIComponent(code)}`);
  if (!data.success) {
    showToast(data.message || 'Exemplar ungültig oder verliehen.', 'error');
    el.borrowBookInput.select();
    return;
  }

  state.borrowItems.push({ code: String(data.code), title: data.title, author: data.author });
  el.borrowBookInput.value = '';
  el.borrowBookInput.focus();
  renderBorrowList();
  showToast(`Exemplar ${data.code} erfasst.`, 'success');
}

function renderBorrowList() {
  if (state.borrowItems.length === 0) {
    el.borrowListSection.style.display = 'none';
    return;
  }
  el.borrowListSection.style.display = 'block';
  el.borrowCount.textContent = `${state.borrowItems.length} Exemplar(e)`;
  el.borrowItemsList.innerHTML = state.borrowItems.map((item, index) => `
    <div class="scan-item">
      <div class="item-main">
        <span class="item-icon">📖</span>
        <div>
          <div class="item-title">${escapeHTML(item.title)}</div>
          <div class="item-author">von ${escapeHTML(item.author)}</div>
        </div>
      </div>
      <div class="item-meta">
        <span class="item-code-badge">${escapeHTML(item.code)}</span>
        <button class="btn-remove-item" data-index="${index}" aria-label="Entfernen">✕</button>
      </div>
    </div>`).join('');
}

async function confirmBorrow() {
  if (!state.activeMember || state.borrowItems.length === 0 || state.busy) return;
  await withBusy(el.btnConfirmBorrow, async () => {
    const data = await api('borrow', {
      memberCode: state.activeMember.code,
      exemplarCodes: state.borrowItems.map((item) => item.code).join(',')
    });
    if (!data.success) {
      showToast(data.message || 'Buchung fehlgeschlagen.', 'error');
      return;
    }
    const due = data.dueDate ? ` Rückgabe bis ${formatDate(data.dueDate)}.` : '';
    showToast(data.message + due, data.printed ? 'success' : 'warning');
    resetMember();
  });
}

// ===================================================================
// Rueckgabe
// ===================================================================

async function scanReturnBook() {
  const code = normalizeCode(el.returnBookInput.value);
  if (!code) return;

  if (state.returnItems.some((item) => item.code === code)) {
    showToast('Dieses Exemplar ist bereits erfasst.', 'error');
    el.returnBookInput.select();
    return;
  }

  const data = await api(`return?action=checkExemplar&code=${encodeURIComponent(code)}`);
  if (!data.success) {
    showToast(data.message || 'Exemplar nicht ausgeliehen oder unbekannt.', 'error');
    el.returnBookInput.select();
    return;
  }

  state.returnItems.push({
    code: String(data.code),
    title: data.title,
    author: data.author,
    memberCode: data.memberCode,
    memberName: data.memberName,
    overdue: data.overdue,
    timeDiffText: data.timeDiffText
  });
  el.returnBookInput.value = '';
  el.returnBookInput.focus();
  renderReturnList();
  showToast(`Rückgabe-Exemplar ${data.code} erfasst.`, 'success');
}

function renderReturnList() {
  if (state.returnItems.length === 0) {
    el.returnListSection.style.display = 'none';
    el.returnEmptyState.style.display = 'flex';
    return;
  }
  el.returnListSection.style.display = 'block';
  el.returnEmptyState.style.display = 'none';
  el.returnCount.textContent = `${state.returnItems.length} Exemplar(e)`;
  el.returnItemsList.innerHTML = state.returnItems.map((item, index) => `
    <div class="scan-item">
      <div class="item-main">
        <span class="item-icon">📖</span>
        <div>
          <div class="item-title">${escapeHTML(item.title)}</div>
          <div class="item-author">Ausgeliehen an: ${escapeHTML(item.memberName)} (${escapeHTML(item.memberCode)})</div>
        </div>
      </div>
      <div class="item-meta">
        <span class="item-code-badge">${escapeHTML(item.code)}</span>
        <span class="status-badge ${item.overdue ? 'status-overdue' : 'status-active'}">${escapeHTML(item.timeDiffText)}</span>
        <button class="btn-remove-item" data-index="${index}" aria-label="Entfernen">✕</button>
      </div>
    </div>`).join('');
}

async function confirmReturn() {
  if (state.returnItems.length === 0 || state.busy) return;
  await withBusy(el.btnConfirmReturn, async () => {
    const data = await api('return', {
      exemplarCodes: state.returnItems.map((item) => item.code).join(',')
    });
    if (!data.success) {
      showToast(data.message || 'Buchung fehlgeschlagen.', 'error');
      return;
    }
    showToast(data.message, data.printed ? 'success' : 'warning');
    state.returnItems = [];
    renderReturnList();
    el.returnBookInput.focus();
  });
}

// ===================================================================
// Event-Verkabelung
// ===================================================================

el.btnCheckMember.addEventListener('click', checkMember);
onEnter(el.borrowMemberInput, checkMember);
el.btnResetMember.addEventListener('click', resetMember);
el.btnAddBookToBorrow.addEventListener('click', addBorrowBook);
onEnter(el.borrowBookInput, addBorrowBook);
el.btnConfirmBorrow.addEventListener('click', confirmBorrow);

el.btnCheckReturnBook.addEventListener('click', scanReturnBook);
onEnter(el.returnBookInput, scanReturnBook);
el.btnConfirmReturn.addEventListener('click', confirmReturn);

// Event-Delegation: ein Listener pro Liste statt onclick="..." im HTML (CSP-konform)
el.borrowItemsList.addEventListener('click', (e) => {
  const btn = e.target.closest('.btn-remove-item');
  if (!btn) return;
  state.borrowItems.splice(Number(btn.dataset.index), 1);
  renderBorrowList();
  el.borrowBookInput.focus();
});
el.returnItemsList.addEventListener('click', (e) => {
  const btn = e.target.closest('.btn-remove-item');
  if (!btn) return;
  state.returnItems.splice(Number(btn.dataset.index), 1);
  renderReturnList();
  el.returnBookInput.focus();
});

// ===================================================================
// Hilfsfunktionen
// ===================================================================

/** "00100 " -> "100"; ungueltige Eingaben bleiben unveraendert, der Server meldet den Fehler. */
function normalizeCode(raw) {
  const trimmed = raw.trim();
  return /^\d+$/.test(trimmed) ? String(Number(trimmed)) : trimmed;
}

async function withBusy(button, action) {
  state.busy = true;
  button.disabled = true;
  try {
    await action();
  } finally {
    state.busy = false;
    button.disabled = false;
  }
}

let toastTimer;
function showToast(message, type = 'success') {
  const icons = { success: '✅', warning: '⚠️', error: '❌' };
  el.toastText.textContent = message;
  el.toastIcon.textContent = icons[type] || 'ℹ️';
  el.toast.className = `toast toast-${type} show`;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.toast.classList.remove('show'), type === 'success' ? 4000 : 7000);
}

/** "2026-11-23" -> "23.11.2026" */
function formatDate(isoDate) {
  const [y, m, d] = isoDate.split('-');
  return `${d}.${m}.${y}`;
}

/** Muss fuer JEDEN Wert benutzt werden, der per innerHTML eingefuegt wird (XSS-Schutz). */
function escapeHTML(value) {
  if (value === null || value === undefined) return '';
  return String(value).replace(/[&<>'"]/g, (ch) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
  })[ch]);
}

initDashboard();
