import { extractTargetCookies } from '../shared/cookies';
import { MediaDeduplicator } from '../detection/deduplicator';
import { MediaItem } from '../shared/types';
import { setupCommands } from './commands';
import { ConnectionManager } from './connection/ConnectionManager';
import { setupContextMenus } from './context_menu';
import { NotificationService } from './notifications';
import { NetworkMediaSniffer } from './sniffer/network_sniffer';

// @ts-expect-error browser runtime
const browserApi = typeof browser !== 'undefined' ? browser : typeof chrome !== 'undefined' ? chrome : null;

class BackgroundController {
  private conn = new ConnectionManager();
  private tabMediaMap = new Map<number, MediaItem[]>();
  private tabDedupMap = new Map<number, MediaDeduplicator>();
  private networkSniffer: NetworkMediaSniffer;

  constructor() {
    this.networkSniffer = new NetworkMediaSniffer((tabId, item) => {
      this.addTabMedia(tabId, [item]);
    });
  }

  init(): void {
    if (!browserApi) return;

    setupContextMenus(this.conn);
    setupCommands(this.conn, (tabId) => this.tabMediaMap.get(tabId) || []);
    this.networkSniffer.start();

    // Listen for tab updates & closures to manage state
    browserApi.tabs?.onRemoved?.addListener((tabId: number) => {
      this.tabMediaMap.delete(tabId);
      this.tabDedupMap.delete(tabId);
    });

    browserApi.tabs?.onUpdated?.addListener((tabId: number, changeInfo: any) => {
      if (changeInfo.url) {
        this.tabMediaMap.delete(tabId);
        this.tabDedupMap.delete(tabId);
        this.updateBadge(tabId, 0);
      }
    });

    // Handle messages from content scripts and popup UI
    browserApi.runtime?.onMessage?.addListener((message: any, sender: any, sendResponse: any) => {
      const action = message?.action;
      const tabId = sender?.tab?.id || message?.tabId;

      if (action === 'tab_navigated') {
        if (tabId) {
          this.tabMediaMap.delete(tabId);
          this.tabDedupMap.delete(tabId);
          this.updateBadge(tabId, 0);
        }
        sendResponse({ success: true });
        return true;
      }

      if (action === 'media_detected') {
        const items = message.items as MediaItem[];
        if (tabId && Array.isArray(items)) {
          this.addTabMedia(tabId, items);
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
        return true;
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

      if (action === 'download_with_cookies') {
        const item = message.item as MediaItem;
        const targetUrl = item.pageUrl || item.url;
        extractTargetCookies(targetUrl, message.storeId)
          .then((cookiesTxt) => {
            return this.conn.sendDownload(item, message.autoDownload, cookiesTxt, true);
          })
          .then((resp) => {
            if (resp.success) {
              NotificationService.showDownloadQueued(`${item.title} (with Cookies)`);
            } else {
              NotificationService.showError(resp.error || 'Cookie download failed');
            }
            sendResponse(resp);
          })
          .catch((err: any) => {
            const errStr = err?.message || String(err);
            NotificationService.showError(`Cookie extraction failed: ${errStr}`);
            sendResponse({ success: false, error: errStr });
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

  private addTabMedia(tabId: number, items: MediaItem[]): void {
    let dedup = this.tabDedupMap.get(tabId);
    if (!dedup) {
      dedup = new MediaDeduplicator();
      this.tabDedupMap.set(tabId, dedup);
    }

    let changed = false;
    for (const it of items) {
      if (dedup.add(it)) {
        changed = true;
      }
    }

    const allItems = dedup.getAll();
    this.tabMediaMap.set(tabId, allItems);
    this.updateBadge(tabId, allItems.length);
  }

  private updateBadge(tabId: number, count: number): void {
    const actionApi = browserApi?.action || browserApi?.browserAction;
    if (!actionApi) return;

    try {
      const text = count > 0 ? String(count) : '';
      actionApi.setBadgeText({ text, tabId });
      actionApi.setBadgeBackgroundColor({ color: '#3584e4', tabId });
    } catch {
      // Ignored
    }
  }
}

const controller = new BackgroundController();
controller.init();
