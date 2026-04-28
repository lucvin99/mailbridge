// Logo SVG reutilizado em todas as páginas
const LOGO_SVG = `<svg class="logo-svg" viewBox="0 0 36 36" fill="none">
  <rect width="36" height="36" rx="10" fill="#eff6ff"/>
  <rect x="4" y="22" width="28" height="3" rx="1.5" fill="#3b82f6"/>
  <rect x="7" y="12" width="3" height="13" rx="1" fill="#1d4ed8"/>
  <rect x="26" y="12" width="3" height="13" rx="1" fill="#1d4ed8"/>
  <line x1="8.5" y1="12" x2="18" y2="22" stroke="#93c5fd" stroke-width="1.5" stroke-linecap="round"/>
  <line x1="8.5" y1="12" x2="13" y2="22" stroke="#93c5fd" stroke-width="1.5" stroke-linecap="round"/>
  <line x1="27.5" y1="12" x2="18" y2="22" stroke="#93c5fd" stroke-width="1.5" stroke-linecap="round"/>
  <line x1="27.5" y1="12" x2="23" y2="22" stroke="#93c5fd" stroke-width="1.5" stroke-linecap="round"/>
  <rect x="6.5" y="10" width="4" height="3" rx="1" fill="#1d4ed8"/>
  <rect x="25.5" y="10" width="4" height="3" rx="1" fill="#1d4ed8"/>
</svg>`;

function renderNav(activePage) {
    const user = getUser();
    const pages = [
        { id: 'index',     href: 'index.html',     label: 'Dashboard' },
        { id: 'send',      href: 'send.html',       label: 'Enviar' },
        { id: 'campaigns', href: 'campaigns.html',  label: 'Campanhas' },
        { id: 'history',   href: 'history.html',    label: 'Histórico' },
    ];

    document.body.insertAdjacentHTML('afterbegin', `
        <nav>
            <a class="logo" href="index.html">
                ${LOGO_SVG}
                <span class="logo-text">Mail<span>Bridge</span></span>
            </a>
            <div class="nav-links">
                ${pages.map(p => `
                    <a class="nav-link ${activePage === p.id ? 'active' : ''}" href="${p.href}">${p.label}</a>
                `).join('')}
            </div>
            <div style="display:flex;align-items:center;gap:4px;">
                <span class="nav-user">${esc(user?.name ?? '')}</span>
                <button class="nav-link logout" onclick="logout()">Sair</button>
            </div>
        </nav>
    `);
}
