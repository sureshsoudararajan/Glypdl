import { MediaItem } from '../shared/types';
import { setupCommands } from './commands';
import { ConnectionManager } from './connection/ConnectionManager';
import { setupContextMenus } from './context_menu';
import { NotificationService } from './notifications';

// @ts-expect-error browser runtime
const browserApi = typeof browser !== 'undefined' ? browser : typeof chrome !== 'undefined' ? chrome : null;

class BackgroundController {
  private conn = new ConnectionManager();
  private tabMediaMap = new Map<number, MediaItem[]>();

  init(): void {
    if (!browserApi) return;

    setupContextMenus(this.conn);
    setupCommands(this.conn, (tabId) => this.tabMediaMap.get(tabId) || []);

    // Listen for tab closure to clean up memory
    browserApi.tabs?.onRemoved?.addListener((tabId: number) => {
      this.tabMediaMap.delete(tabId);
    });

    // Handle messages from content scripts and popup UI
    browserApi.runtime?.onMessage?.addListener((message: any, sender: any, sendResponse: any) => {
      const action = message?.action;
      const tabId = sender?.tab?.id || message?.tabId;

      if (action === 'media_detected') {
        const items = message.items as MediaItem[];
        if (tabId && Array.isArray(items)) {
          this.tabMediaMap.set(tabId, items);
          this.updateBadge(tabId, items.length);
        }
        sendResponse({ success: true });
        return true;
      }

      if (action === 'get_tab_media') {
        const items = (tabId ? this.tabMediaMap.get(tabId) : []) || [];
        sendResponse({ items });
        return true;
      }

      if (action === 'get_connection_status') {
        this.conn.getStatus().then((status) => sendResponse(status));
        return true; // Keep message channel open for async response
      }

      if (action === 'test_connection') {
        this.conn.testConnection().then((status) => sendResponse(status));
        return true;
      }

      if (action === 'download_item') {
        const item = message.item as MediaItem;
        this.conn.sendDownload(item, message.autoDownload).then((resp) => {
          if (resp.success) {
            NotificationService.showDownloadQueued(item.title);
          } else {
            NotificationService.showError(resp.error || 'Download failed');
          }
          sendResponse(resp);
        });
        return true;
      }

      if (action === 'download_batch') {
        const items = message.items as MediaItem[];
        const batchMsg = {
          protocolVersion: 1,
          action: 'download_batch' as const,
          jobs: items
        };
        this.conn.sendCustomMessage(batchMsg).then((resp) => {
          if (resp.success) {
            NotificationService.showDownloadQueued(`${items.length} items`);
          } else {
            NotificationService.showError(resp.error || 'Batch download failed');
          }
          sendResponse(resp);
        });
        return true;
      }

      return false;
    });
  }

  private updateBadge(tabId: number, count: number): void {
    if (!browserApi?.action) return;

    try {
      const text = count > 0 ? String(count) : '';
      browserApi.action.setBadgeText({ text, tabId });
      browserApi.action.setBadgeBackgroundColor({ color: '#3584e4', tabId });
    } catch {
      // Ignored
    }
  }
}

const controller = new BackgroundController();
controller.init();
