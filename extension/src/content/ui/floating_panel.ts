import { ExtensionSettings, MediaItem } from '../../shared/types';

export class FloatingMediaPanel {
  private panel: HTMLElement | null = null;
  private currentItem: MediaItem | null = null;
  private onDownloadClick: (item: MediaItem) => void;
  private onDismissClick: () => void;
  private settings: ExtensionSettings;

  constructor(
    settings: ExtensionSettings,
    onDownload: (item: MediaItem) => void,
    onDismiss: () => void
  ) {
    this.settings = settings;
    this.onDownloadClick = onDownload;
    this.onDismissClick = onDismiss;
  }

  show(item: MediaItem): void {
    this.currentItem = item;
    if (this.panel && document.contains(this.panel)) {
      this.updateContent(item);
      return;
    }

    this.panel = document.createElement('div');
    this.panel.className = `glypdl-floating-panel ${this.settings.floatingPanelPosition || 'bottom-right'}`;

    const thumbSrc = item.thumbnailUrl || '';
    const badgeText = item.isProtected ? '🔒 DRM' : `${item.quality || 'HD'} • ${item.format.toUpperCase()}`;
    const badgeClass = item.isProtected ? 'glypdl-badge protected' : 'glypdl-badge';

    this.panel.innerHTML = `
      <div class="glypdl-panel-header">
        <div class="glypdl-panel-brand">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="#3584e4">
            <path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/>
          </svg>
          <span>Glypdl Detected Media</span>
        </div>
        <button class="glypdl-panel-close" title="Dismiss">&times;</button>
      </div>
      <div class="glypdl-panel-body">
        ${
          thumbSrc
            ? `<img class="glypdl-panel-thumb" src="${thumbSrc}" alt="Thumbnail" />`
            : `<div class="glypdl-panel-thumb" style="display:flex;align-items:center;justify-content:center;color:#666;">🎬</div>`
        }
        <div class="glypdl-panel-info">
          <div class="glypdl-panel-title" title="${item.title}">${item.title}</div>
          <div class="glypdl-panel-meta">
            <span class="${badgeClass}">${badgeText}</span>
            ${item.formattedDuration ? `<span>${item.formattedDuration}</span>` : ''}
            ${item.formattedSize ? `<span>${item.formattedSize}</span>` : ''}
          </div>
        </div>
      </div>
      <div class="glypdl-panel-actions">
        ${
          item.isProtected
            ? `<button class="glypdl-btn glypdl-btn-secondary" disabled>Protected Media</button>`
            : `<button class="glypdl-btn glypdl-btn-primary glypdl-download-btn">Download</button>`
        }
        <button class="glypdl-btn glypdl-btn-secondary glypdl-dismiss-btn">Dismiss</button>
      </div>
    `;

    // Bind event listeners
    this.panel.querySelector('.glypdl-panel-close')?.addEventListener('click', () => this.hide());
    this.panel.querySelector('.glypdl-dismiss-btn')?.addEventListener('click', () => this.hide());
    this.panel.querySelector('.glypdl-download-btn')?.addEventListener('click', () => {
      if (this.currentItem) {
        this.onDownloadClick(this.currentItem);
        const dlBtn = this.panel?.querySelector('.glypdl-download-btn');
        if (dlBtn) {
          dlBtn.textContent = '✓ Sent to Glypdl';
          setTimeout(() => this.hide(), 1500);
        }
      }
    });

    document.body.appendChild(this.panel);
  }

  private updateContent(item: MediaItem): void {
    if (!this.panel) return;
    const titleEl = this.panel.querySelector('.glypdl-panel-title');
    if (titleEl) {
      titleEl.textContent = item.title;
      titleEl.setAttribute('title', item.title);
    }
  }

  hide(): void {
    if (this.panel && this.panel.parentElement) {
      this.panel.parentElement.removeChild(this.panel);
      this.panel = null;
    }
    this.onDismissClick();
  }
}
