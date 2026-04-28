// ============================================================
// auth.js — funções partilhadas por todas as páginas
// ============================================================

const API = 'http://localhost:4567';

// Guarda e lê token/utilizador do localStorage do browser
function getToken() { return localStorage.getItem('mb_token'); }
function getUser()  { try { return JSON.parse(localStorage.getItem('mb_user')); } catch { return null; } }

function saveSession(data) {
    localStorage.setItem('mb_token', data.token);
    localStorage.setItem('mb_user', JSON.stringify({ name: data.name, email: data.email }));
}

function clearSession() {
    localStorage.removeItem('mb_token');
    localStorage.removeItem('mb_user');
}

// Redirige para login se não estiver autenticado
function requireAuth() {
    if (!getToken()) { window.location.href = 'login.html'; return false; }
    return true;
}

// Redirige para dashboard se já estiver autenticado (usado em login/register)
function redirectIfAuth() {
    if (getToken()) window.location.href = 'index.html';
}

// Wrapper para fetch que injeta o token automaticamente em todos os pedidos
async function apiFetch(path, options = {}) {
    const token = getToken();
    const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
    if (token) headers['Authorization'] = 'Bearer ' + token;
    const res = await fetch(API + path, { ...options, headers });
    // Se o servidor responder 401, a sessão expirou — faz logout automático
    if (res.status === 401) { clearSession(); window.location.href = 'login.html'; return null; }
    return res;
}

async function logout() {
    await apiFetch('/api/auth/logout', { method: 'POST' }).catch(() => {});
    clearSession();
    window.location.href = 'login.html';
}

// Escapa HTML para prevenir XSS ao mostrar dados do utilizador na página
function esc(s) {
    return String(s ?? '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

// Mostra um toast de feedback
function toast(id, msg, type) {
    const t = document.getElementById(id);
    if (!t) return;
    t.textContent = msg;
    t.className = 'toast ' + type;
    t.style.display = 'block';
    if (type !== 'info') setTimeout(() => t.style.display = 'none', 4000);
}
