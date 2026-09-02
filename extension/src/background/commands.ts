import { ConnectionManager } from './connection/ConnectionManager';
import { NotificationService } from './notifications';

export function setupCommands(conn: ConnectionManager, getTabMedia: (tabId: number) => any[]): void {
  // @ts-expect-error browser commands
  const browserApi = typeof browser !== 'undefined' ? browser : typeof chrome !== 'undefined' ? chrome : null;
  if (!browserApi || !browserApi.commands) return;

  browserApi.commands.onCommand.addListener(async (command: string) => {
    if (command === 'send-media-to-glypdl') {
      const tabs = await browserApi.tabs.query({ active: true, currentWindow: true });
      const activeTab = tabs[0];
      if (!activeTab || !activeTab.id) return;

      const mediaList = getTabMedia(activeTab.id);
      if (mediaList && mediaList.length > 0) {
        const topItem = mediaList[0];
        const resp = await conn.sendDownload(topItem);
        if (resp.success) {
          NotificationService.showDownloadQueued(topItem.title);
        } else {
          NotificationService.showError(resp.error || 'Failed to send to Glypdl');
        }
      } else if (activeTab.url) {
        // Fallback to sending active tab URL
        const fallbackItem = {
          id: `cmd-${Date.now()}`,
          url: activeTab.url,
          pageUrl: activeTab.url,
          title: activeTab.title || 'Web Media',
          type: 'video',
          format: 'mp4',
          quality: '1080p',
          site: '',
          timestamp: Date.now(),
          sourceStrategy: 'context'
        };
        const resp = await conn.sendDownload(fallbackItem as any);
        if (resp.success) {
          NotificationService.showDownloadQueued(fallbackItem.title);
        }
      }
    }
  });
}
