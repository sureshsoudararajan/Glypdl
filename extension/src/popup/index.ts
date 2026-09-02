import { ConnectionStatus, MediaItem } from '../shared/types';

// @ts-expect-error browser runtime
const browserApi = typeof browser !== 'undefined' ? browser : typeof chrome !== 'undefined' ? chrome : null;

class PopupController {
  private mediaItems: MediaItem[] = [];

  async init(): Promise<void> {
    this.bindStaticListeners();
    await this.loadConnectionStatus();
    await this.loadCurrentTabMedia();
  }

  private bindStaticListeners(): void {
    document.getElementById('options-btn')?.addEventListener('click', () => {
      if (browserApi?.runtime?.openOptionsPage) {
        browserApi.runtime.openOptionsPage();
      }
    });

    document.getElementById('download-all-btn')?.addEventListener('click', () => {
      if (this.mediaItems.length > 0) {
        browserApi?.runtime?.sendMessage({
          action: 'download_batch',
          items: this.mediaItems
        });
        window.close();
      }
    });
  }

  private async loadConnectionStatus(): Promise<void> {
    const statusEl = document.getElementById('connection-status');
    const textEl = document.getElementById('status-text');
    if (!statusEl || !textEl || !browserApi?.runtime) return;

    try {
      browserApi.runtime.sendMessage({ action: 'get_connection_status' }, (status: ConnectionStatus) => {
        if (status && status.connected) {
          statusEl.className = 'status-badge connected';
          textEl.textContent = status.glypdlVersion ? `Glypdl v${status.glypdlVersion}` : 'Connected';
        } else {
          statusEl.className = 'status-badge disconnected';
          textEl.textContent = 'Glypdl Disconnected';
        }
      });
    } catch {
      if (textEl) textEl.textContent = 'Offline';
    }
  }

  private async loadCurrentTabMedia(): Promise<void> {
    if (!browserApi?.tabs) return;

    const tabs = await browserApi.tabs.query({ active: true, currentWindow: true });
    const activeTab = tabs[0];
    if (!activeTab || !activeTab.id) return;

    browserApi.runtime.sendMessage({ action: 'get_tab_media', tabId: activeTab.id }, (response: any) => {
      this.mediaItems = response?.items || [];
      this.renderMediaList();
    });
  }

  private renderMediaList(): void {
    const container = document.getElementById('media-container');
    const emptyState = document.getElementById('empty-state');
    const sectionTitle = document.getElementById('section-title');
    const downloadAllBtn = document.getElementById('download-all-btn');

    if (!container || !emptyState || !sectionTitle || !downloadAllBtn) return;

    container.innerHTML = '';

    if (this.mediaItems.length === 0) {
      emptyState.style.display = 'block';
      sectionTitle.style.display = 'none';
      downloadAllBtn.style.display = 'none';
      return;
    }

    emptyState.style.display = 'none';
    sectionTitle.style.display = 'block';
    sectionTitle.textContent = `${this.mediaItems.length} Detected ${this.mediaItems.length === 1 ? 'Media' : 'Media Items'}`;
    downloadAllBtn.style.display = 'block';

    for (const item of this.mediaItems) {
      const card = document.createElement('div');
      card.className = 'media-card';

      const thumbHtml = item.thumbnailUrl
        ? `<img class="media-thumb" src="${item.thumbnailUrl}" alt="Thumb" />`
        : `<div class="media-thumb" style="display:flex;align-items:center;justify-content:center;color:#666;">🎬</div>`;

      const badgeText = item.isProtected ? '🔒 DRM' : `${item.quality || 'HD'} • ${item.format.toUpperCase()}`;

      card.innerHTML = `
        ${thumbHtml}
        <div class="media-info">
          <div class="media-title" title="${item.title}">${item.title}</div>
          <div class="media-meta">
            <span class="media-tag">${badgeText}</span>
            ${item.formattedDuration ? `<span>${item.formattedDuration}</span>` : ''}
            ${item.formattedSize ? `<span>${item.formattedSize}</span>` : ''}
          </div>
        </div>
        <button class="download-btn" ${item.isProtected ? 'disabled' : ''}>Download</button>
      `;

      card.querySelector('.download-btn')?.addEventListener('click', () => {
        browserApi?.runtime?.sendMessage({
          action: 'download_item',
          item
        });
        window.close();
      });

      container.appendChild(card);
    }
  }
}

document.addEventListener('DOMContentLoaded', () => {
  const popup = new PopupController();
  popup.init();
});
