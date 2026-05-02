/* ============================================
   LLD Knowledge Base — Application Logic
   ============================================ */

// Navigation structure
const NAV_STRUCTURE = [
    {
        title: 'Design Patterns',
        icon: '🏗️',
        items: [
            { key: 'docs/1-Singleton.md', label: 'Singleton', icon: '📄' },
            { key: 'docs/2-FactoryMethod.md', label: 'Factory Method', icon: '📄' },
            { key: 'docs/3-AbstractFactory.md', label: 'Abstract Factory', icon: '📄' },
            { key: 'docs/4-Adapter.md', label: 'Adapter', icon: '📄' },
            { key: 'docs/5-Decorator.md', label: 'Decorator', icon: '📄' },
            { key: 'docs/6-Flyweight.md', label: 'Flyweight', icon: '📄' },
            { key: 'docs/7-Strategy.md', label: 'Strategy', icon: '📄' },
            { key: 'docs/8-ChainOfResponsibility.md', label: 'Chain of Responsibility', icon: '📄' },
            { key: 'docs/9-Composite.md', label: 'Composite', icon: '📄' },
        ]
    },
    {
        title: 'System Design',
        icon: '⚙️',
        items: [
            { key: 'docs/FileSystem.md', label: 'In-Memory File System', icon: '📂' },
            { key: 'docs/LRUCache.md', label: 'LRU Cache', icon: '📂' },
            { key: 'docs/ParkingLot.md', label: 'Parking Lot', icon: '📂' },
            { key: 'docs/Redis.md', label: 'Redis', icon: '📂' },
        ]
    },
    {
        title: 'MySQL',
        icon: '🐬',
        items: [
            { key: 'MySQL/Exercises.md', label: 'Curriculum Overview', icon: '📋' },
            { key: 'MySQL/01-DatabaseAndTableBasics.md', label: '01 — Database & Table Basics', icon: '📝' },
            { key: 'MySQL/01-Solutions.md', label: '01 — Solutions', icon: '✅' },
            { key: 'MySQL/02-CRUDOperations.md', label: '02 — CRUD Operations', icon: '📝' },
            { key: 'MySQL/03-FilteringSortingAggregation.md', label: '03 — Filtering & Aggregation', icon: '📝' },
        ]
    }
];

// Group Java source files by package
function buildJavaNav() {
    const javaSection = {
        title: 'Java Code',
        icon: '☕',
        items: []
    };

    const javaKeys = Object.keys(SITE_CONTENT).filter(k => k.startsWith('src/com/lld/'));
    javaKeys.sort();

    javaKeys.forEach(key => {
        const relativePath = key.replace('src/com/lld/', '');
        const parts = relativePath.split('/');

        let currentLevel = javaSection.items;

        for (let i = 0; i < parts.length; i++) {
            const part = parts[i];

            if (i === parts.length - 1) {
                currentLevel.push({
                    key: key,
                    label: part,
                    icon: '📄'
                });
            } else {
                let title = part.replace(/^\w/, c => c.toUpperCase());
                if (part === "designpatterns" || part === "patterns") title = "Design Patterns";
                if (part === "filesystem") title = "File System";
                if (part === "lrucache") title = "LRU Cache";
                if (part === "parkinglot") title = "Parking Lot";

                let folder = currentLevel.find(item => item.label === title && item.items);
                if (!folder) {
                    folder = {
                        label: title,
                        icon: '📁',
                        items: []
                    };
                    currentLevel.push(folder);
                }
                currentLevel = folder.items;
            }
        }
    });

    NAV_STRUCTURE.push(javaSection);
}

// Initialize markdown-it
let md;

function initMarkdown() {
    md = window.markdownit({
        html: true,
        linkify: true,
        typographer: true
    });

    // Override the default code block fence renderer to handle mermaid
    const defaultRender = md.renderer.rules.fence || function (tokens, idx, options, env, self) {
        return self.renderToken(tokens, idx, options);
    };

    md.renderer.rules.fence = function (tokens, idx, options, env, self) {
        const token = tokens[idx];
        if (token.info && token.info.trim() === 'mermaid') {
            // Escape HTML inside mermaid block just to be safe, though mermaid usually handles it.
            // Wait, we should NOT escape the HTML because mermaid expects the raw syntax.
            return `<div class="mermaid">${escapeHtml(token.content)}</div>`;
        }
        // Pass other code blocks to the default renderer (which uses <pre><code>)
        return defaultRender(tokens, idx, options, env, self);
    };
}

function escapeHtml(str) {
    return str
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

// Render content
function renderMarkdown(key) {
    const raw = SITE_CONTENT[key];
    if (!raw) return '<p>Content not found.</p>';

    if (key.endsWith('.java')) {
        // Render Java source as a code block
        const html = `<h1>${key.split('/').pop()}</h1>
<p class="file-path" style="color: var(--text-muted); margin-bottom: 20px; font-size: 13px; font-family: var(--font-mono);">${key}</p>
<pre data-lang="java"><code class="language-java">${escapeHtml(raw)}</code></pre>`;
        return html;
    }

    return md.render(raw);
}

// Build sidebar
function buildSidebar() {
    const nav = document.getElementById('sidebar-nav');
    nav.innerHTML = '';

    NAV_STRUCTURE.forEach((section, idx) => {
        const sectionEl = document.createElement('div');
        sectionEl.className = 'nav-section';
        sectionEl.dataset.section = idx;

        const titleEl = document.createElement('div');
        titleEl.className = 'nav-section-title';
        titleEl.innerHTML = `<span class="section-icon">${section.icon}</span> ${section.title} <span class="chevron">▼</span>`;
        titleEl.addEventListener('click', () => {
            sectionEl.classList.toggle('collapsed');
        });

        const itemsEl = document.createElement('ul');
        itemsEl.className = 'nav-items';

        function buildTree(itemParent, container) {
            itemParent.forEach(item => {
                if (item.items) {
                    const li = document.createElement('li');
                    li.className = 'nav-folder collapsed';

                    const fTitle = document.createElement('div');
                    fTitle.className = 'nav-folder-title';
                    fTitle.innerHTML = `<span class="item-icon">${item.icon}</span><span class="item-label">${item.label}</span><span class="chevron">▼</span>`;

                    const fItems = document.createElement('ul');
                    fItems.className = 'nav-folder-items';

                    buildTree(item.items, fItems);

                    fTitle.addEventListener('click', (e) => {
                        e.stopPropagation();
                        li.classList.toggle('collapsed');
                    });

                    li.appendChild(fTitle);
                    li.appendChild(fItems);
                    container.appendChild(li);
                } else {
                    const li = document.createElement('li');
                    li.className = 'nav-item';
                    li.dataset.key = item.key;
                    li.innerHTML = `<span class="item-icon">${item.icon}</span><span class="item-label">${item.label}</span>`;
                    li.addEventListener('click', (e) => {
                        e.stopPropagation();
                        navigateTo(item.key);
                    });
                    container.appendChild(li);
                }
            });
        }

        buildTree(section.items, itemsEl);

        sectionEl.appendChild(titleEl);
        sectionEl.appendChild(itemsEl);
        nav.appendChild(sectionEl);
    });
}

// Table of Contents
function buildTOC(html) {
    const toc = document.getElementById('toc-list');
    if (!toc) return;
    toc.innerHTML = '';

    const tempDiv = document.createElement('div');
    tempDiv.innerHTML = html;

    const headings = tempDiv.querySelectorAll('h2, h3');

    headings.forEach((h, i) => {
        const id = `heading-${i}`;
        const li = document.createElement('li');
        const a = document.createElement('a');
        a.className = `toc-link toc-${h.tagName.toLowerCase()}`;
        a.textContent = h.textContent;
        a.addEventListener('click', (e) => {
            e.preventDefault();
            const target = document.getElementById(id);
            if (target) {
                target.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
        });
        li.appendChild(a);
        toc.appendChild(li);
    });
}

function addHeadingIds(container) {
    const headings = container.querySelectorAll('h2, h3');
    headings.forEach((h, i) => {
        h.id = `heading-${i}`;
    });
}

// Navigation
function navigateTo(key) {
    window.location.hash = encodeURIComponent(key);
}

function loadContent(key) {
    const contentEl = document.getElementById('content-area');
    if (!key || !SITE_CONTENT[key]) {
        showWelcome();
        return;
    }

    const html = renderMarkdown(key);
    contentEl.innerHTML = `<div class="markdown-body">${html}</div>`;

    // Add heading IDs and build TOC
    addHeadingIds(contentEl);
    buildTOC(html);

    // Update breadcrumb
    updateBreadcrumb(key);

    // Highlight active nav item
    document.querySelectorAll('.nav-item').forEach(el => {
        el.classList.toggle('active', el.dataset.key === key);
    });

    // Run Prism highlighting
    if (window.Prism) {
        Prism.highlightAllUnder(contentEl);
    }

    // Render mermaid diagrams
    renderMermaid();

    // Scroll to top
    contentEl.closest('.content').scrollTop = 0;

    // Close sidebar on mobile
    closeSidebar();
}

function renderMermaid() {
    const mermaidDivs = document.querySelectorAll('.mermaid');
    if (mermaidDivs.length > 0 && window.mermaid) {
        // Reset each mermaid div so mermaid.run() processes them fresh
        mermaidDivs.forEach((el, i) => {
            el.removeAttribute('data-processed');
            el.id = `mermaid-${Date.now()}-${i}`;
        });
        setTimeout(() => {
            try {
                mermaid.run({ nodes: [...mermaidDivs] });
            } catch (e) {
                console.warn('Mermaid rendering error:', e);
            }
        }, 50);
    }
}

function updateBreadcrumb(key) {
    const current = document.getElementById('breadcrumb-current');
    const section = document.getElementById('breadcrumb-section');

    // Find the section and item
    for (const sec of NAV_STRUCTURE) {
        const item = sec.items.find(i => i.key === key);
        if (item) {
            section.textContent = sec.title;
            current.textContent = item.label;
            return;
        }
    }
    section.textContent = '';
    current.textContent = key.split('/').pop();
}

function showWelcome() {
    const contentEl = document.getElementById('content-area');

    const docCount = Object.keys(SITE_CONTENT).filter(k => k.endsWith('.md')).length;
    const javaCount = Object.keys(SITE_CONTENT).filter(k => k.endsWith('.java')).length;
    const patternCount = NAV_STRUCTURE[0].items.length;

    contentEl.innerHTML = `
    <div class="welcome">
      <span class="welcome-icon">🧠</span>
      <h1>Tech Learning Docs</h1>
      <p>A comprehensive collection of design patterns, system design documents, MySQL exercises, and Java implementations.</p>
      <div class="stats-grid">
        <div class="stat-card">
          <div class="stat-number">${patternCount}</div>
          <div class="stat-label">Design Patterns</div>
        </div>
        <div class="stat-card">
          <div class="stat-number">${docCount}</div>
          <div class="stat-label">Documents</div>
        </div>
        <div class="stat-card">
          <div class="stat-number">${javaCount}</div>
          <div class="stat-label">Java Files</div>
        </div>
      </div>
      <div class="category-grid">
        <div class="category-card" onclick="navigateTo('${NAV_STRUCTURE[0].items[0].key}')">
          <span class="cat-icon">🏗️</span>
          <div class="cat-title">Design Patterns</div>
          <div class="cat-count">${NAV_STRUCTURE[0].items.length} patterns</div>
        </div>
        <div class="category-card" onclick="navigateTo('${NAV_STRUCTURE[1].items[0].key}')">
          <span class="cat-icon">⚙️</span>
          <div class="cat-title">System Design</div>
          <div class="cat-count">${NAV_STRUCTURE[1].items.length} problems</div>
        </div>
        <div class="category-card" onclick="navigateTo('${NAV_STRUCTURE[2].items[0].key}')">
          <span class="cat-icon">🐬</span>
          <div class="cat-title">MySQL</div>
          <div class="cat-count">${NAV_STRUCTURE[2].items.length} exercises</div>
        </div>
        <div class="category-card" onclick="navigateTo('${javaCount > 0 ? NAV_STRUCTURE[3].items[0].key : ''}')">
          <span class="cat-icon">☕</span>
          <div class="cat-title">Java Source</div>
          <div class="cat-count">${javaCount} files</div>
        </div>
      </div>
    </div>
  `;

    // Clear TOC
    const toc = document.getElementById('toc-list');
    if (toc) toc.innerHTML = '';

    // Clear breadcrumb
    document.getElementById('breadcrumb-section').textContent = '';
    document.getElementById('breadcrumb-current').textContent = 'Home';

    // Clear active nav
    document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));
}

// Search
function initSearch() {
    const input = document.getElementById('search-input');
    input.addEventListener('input', (e) => {
        const query = e.target.value.toLowerCase().trim();
        filterNav(query);
    });
}

function filterNav(query) {
    document.querySelectorAll('.nav-section').forEach(section => {
        const items = section.querySelectorAll('.nav-item');
        let sectionVisibleCount = 0;

        items.forEach(item => {
            const label = item.querySelector('.item-label').textContent.toLowerCase();
            const matches = !query || label.includes(query);
            item.style.display = matches ? '' : 'none';

            if (matches) {
                sectionVisibleCount++;
                // ensure parent folders are visible and expanded
                let parent = item.parentElement;
                while (parent && parent !== section) {
                    if (parent.classList.contains('nav-folder')) {
                        parent.style.display = '';
                        if (query) parent.classList.remove('collapsed');
                    }
                    parent = parent.parentElement;
                }
            }
        });

        // Hide empty folders
        if (query) {
            const folders = section.querySelectorAll('.nav-folder');
            folders.forEach(f => {
                const visibleItems = f.querySelectorAll('.nav-item[style=""]');
                f.style.display = visibleItems.length > 0 ? '' : 'none';
            });
        } else {
            const folders = section.querySelectorAll('.nav-folder');
            folders.forEach(f => {
                f.style.display = '';
                // optionally keep folders collapsed on search clear
            });
        }

        section.style.display = sectionVisibleCount > 0 || !query ? '' : 'none';

        if (query) {
            section.classList.remove('collapsed');
        }
    });
}

// Sidebar toggle (mobile)
function initSidebarToggle() {
    const toggle = document.getElementById('menu-toggle');
    const sidebar = document.getElementById('sidebar');
    const overlay = document.getElementById('sidebar-overlay');

    toggle.addEventListener('click', () => {
        sidebar.classList.toggle('open');
        overlay.classList.toggle('active');
    });

    overlay.addEventListener('click', closeSidebar);
}

function closeSidebar() {
    document.getElementById('sidebar').classList.remove('open');
    document.getElementById('sidebar-overlay').classList.remove('active');
}

// Scroll to top
function initScrollTop() {
    const btn = document.getElementById('scroll-top');
    const contentContainer = document.querySelector('.content');

    contentContainer.addEventListener('scroll', () => {
        btn.classList.toggle('visible', contentContainer.scrollTop > 300);
    });

    btn.addEventListener('click', () => {
        contentContainer.scrollTo({ top: 0, behavior: 'smooth' });
    });
}

// Hash routing
function handleHashChange() {
    const hash = decodeURIComponent(window.location.hash.slice(1));
    if (hash && SITE_CONTENT[hash]) {
        loadContent(hash);
    } else {
        showWelcome();
    }
}

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    buildJavaNav();
    initMarkdown();
    buildSidebar();
    initSearch();
    initSidebarToggle();
    initScrollTop();

    // Configure mermaid
    if (window.mermaid) {
        mermaid.initialize({
            startOnLoad: false,
            theme: 'dark',
            themeVariables: {
                darkMode: true,
                background: '#1c2128',
                primaryColor: '#1f6feb',
                primaryTextColor: '#e6edf3',
                primaryBorderColor: '#30363d',
                lineColor: '#8b949e',
                secondaryColor: '#21262d',
                tertiaryColor: '#161b22',
                fontFamily: 'Inter, sans-serif',
                fontSize: '14px',
            },
            flowchart: { curve: 'basis' },
            securityLevel: 'loose',
        });
    }

    // Handle initial route
    handleHashChange();
    window.addEventListener('hashchange', handleHashChange);
});
