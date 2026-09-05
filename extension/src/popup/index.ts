import { ConnectionStatus, MediaItem } from '../shared/types';

// @ts-expect-error browser runtime
const browserApi = typeof browser !== 'undefined' ? browser : typeof chrome !== 'undefined' ? chrome : null;

class PopupController {
  private mediaItems: MediaItem[] = [];
  private activeTabStoreId?: string;

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
    this.activeTabStoreId = (activeTab as any).cookieStoreId;

    const fetchUnifiedList = () => {
      browserApi.runtime.sendMessage({ action: 'get_tab_media', tabId: activeTab.id }, (response: any) => {
        if (response?.items) {
          this.mediaItems = response.items;
          this.renderMediaList();
        }
      });
    };

    // 1. Initial load from background
    fetchUnifiedList();

    // 2. Trigger active tab content script scan for immediate detection and sync
    try {
      browserApi.tabs.sendMessage(activeTab.id, { action: 'scan_now' }, (response: any) => {
        if (response?.items && response.items.length > 0) {
          this.mediaItems = response.items;
          this.renderMediaList();
        } else {
          fetchUnifiedList();
        }
      });
    } catch {
      // Content script may not be loaded on internal pages
    }
  }

  private renderMediaList(): void {
    const container = document.getElementById('media-container');
    const emptyState = document.getElementById('empty-state');
    const sectionTitle = document.getElementById('section-title');
    const downloadAllBtn = document.getElementById('download-all-btn');

    if (!container || !emptyState || !sectionTitle || !downloadAllBtn) return;

    container.replaceChildren();

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

      let thumbEl: HTMLElement;
      if (item.thumbnailUrl) {
        const img = document.createElement('img');
        img.className = 'media-thumb';
        img.src = item.thumbnailUrl;
        img.alt = 'Thumb';
        thumbEl = img;
      } else {
        const placeholder = document.createElement('div');
        placeholder.className = 'media-thumb';
        placeholder.style.display = 'flex';
        placeholder.style.alignItems = 'center';
        placeholder.style.justifyContent = 'center';
        placeholder.style.color = '#666';
        placeholder.textContent = '🎬';
        thumbEl = placeholder;
      }

      const infoDiv = document.createElement('div');
      infoDiv.className = 'media-info';

      const titleDiv = document.createElement('div');
      titleDiv.className = 'media-title';
      titleDiv.title = item.title;
      titleDiv.textContent = item.title;

      const metaDiv = document.createElement('div');
      metaDiv.className = 'media-meta';

      const badgeSpan = document.createElement('span');
      badgeSpan.className = 'media-tag';
      badgeSpan.textContent = item.isProtected ? '🔒 DRM' : `${item.quality || 'HD'} • ${item.format.toUpperCase()}`;
      metaDiv.append(badgeSpan);

      if (item.formattedDuration) {
        const durSpan = document.createElement('span');
        durSpan.textContent = item.formattedDuration;
        metaDiv.append(durSpan);
      }

      if (item.formattedSize) {
        const sizeSpan = document.createElement('span');
        sizeSpan.textContent = item.formattedSize;
        metaDiv.append(sizeSpan);
      }

      infoDiv.append(titleDiv, metaDiv);

      const actionsDiv = document.createElement('div');
      actionsDiv.className = 'media-actions';

      const normalBtn = document.createElement('button');
      normalBtn.className = 'download-btn btn-normal';
      normalBtn.textContent = 'Download';
      if (item.isProtected) normalBtn.disabled = true;

      const cookieBtn = document.createElement('button');
      cookieBtn.className = 'download-btn btn-cookie';
      cookieBtn.textContent = '🍪 Using Cookie';
      cookieBtn.title = 'Extract site cookies and download with authentication';
      if (item.isProtected) cookieBtn.disabled = true;

      actionsDiv.append(normalBtn, cookieBtn);
      card.append(thumbEl, infoDiv, actionsDiv);

      card.querySelector('.btn-normal')?.addEventListener('click', () => {
        const btn = card.querySelector('.btn-normal') as HTMLButtonElement | null;
        if (btn) {
          btn.disabled = true;
          btn.textContent = 'Sending…';
        }
        browserApi?.runtime?.sendMessage(
          {
            action: 'download_item',
            item
          },
          (resp: any) => {
            if (resp && resp.success) {
              if (btn) btn.textContent = '✓ Sent';
              setTimeout(() => window.close(), 500);
            } else {
              if (btn) {
                btn.disabled = false;
                btn.textContent = 'Download';
              }
              window.close();
            }
          }
        );
      });

      card.querySelector('.btn-cookie')?.addEventListener('click', () => {
        const btn = card.querySelector('.btn-cookie') as HTMLButtonElement | null;
        if (btn) {
          btn.disabled = true;
          btn.textContent = 'Extracting…';
        }
        browserApi?.runtime?.sendMessage(
          {
            action: 'download_with_cookies',
            item,
            storeId: this.activeTabStoreId
          },
          (resp: any) => {
            if (resp && resp.success) {
              if (btn) btn.textContent = '✓ Sent to Glypdl';
              setTimeout(() => window.close(), 600);
            } else {
              if (btn) {
                btn.disabled = false;
                btn.textContent = '🍪 Using Cookie';
              }
              const errMsg = resp?.error || 'Failed to extract active session cookies.';
              alert(`Cookie Extraction: ${errMsg}`);
            }
          }
        );
      });

      container.appendChild(card);
    }
  }
}

document.addEventListener('DOMContentLoaded', () => {
  const popup = new PopupController();
  popup.init();
});
