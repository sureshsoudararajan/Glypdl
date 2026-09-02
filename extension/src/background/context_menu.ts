import { ConnectionManager } from './connection/ConnectionManager';
import { DirectMediaStrategy } from '../detection/strategies/direct_media';
import { NotificationService } from './notifications';

export function setupContextMenus(conn: ConnectionManager): void {
  // @ts-expect-error browser contextMenus
  const browserApi = typeof browser !== 'undefined' ? browser : typeof chrome !== 'undefined' ? chrome : null;
  if (!browserApi || !browserApi.contextMenus) return;

  browserApi.contextMenus.removeAll(() => {
    browserApi.contextMenus.create({
      id: 'glypdl-download-link',
      title: 'Download link with Glypdl',
      contexts: ['link']
    });

    browserApi.contextMenus.create({
      id: 'glypdl-download-media',
      title: 'Download media with Glypdl',
      contexts: ['video', 'audio']
    });

    browserApi.contextMenus.create({
      id: 'glypdl-download-page',
      title: 'Download page with Glypdl',
      contexts: ['page']
    });
  });

  // @ts-expect-error browser contextMenus
  browserApi.contextMenus.onClicked.addListener(async (info: any, tab: any) => {
    const pageUrl = tab?.url || info.pageUrl || '';
    let targetUrl = '';
    let title = tab?.title || 'Media';

    if (info.menuItemId === 'glypdl-download-link' && info.linkUrl) {
      targetUrl = info.linkUrl;
    } else if (info.menuItemId === 'glypdl-download-media' && info.srcUrl) {
      targetUrl = info.srcUrl;
    } else if (info.menuItemId === 'glypdl-download-page') {
      targetUrl = pageUrl;
    }

    if (!targetUrl) return;

    const item = DirectMediaStrategy.createDirectItem(targetUrl, pageUrl, title) || {
      id: `ctx-${Date.now()}`,
      url: targetUrl,
      pageUrl,
      title,
      type: 'video',
      format: 'mp4',
      quality: '1080p',
      site: '',
      timestamp: Date.now(),
      sourceStrategy: 'context'
    };

    const resp = await conn.sendDownload(item, false);
    if (resp.success) {
      NotificationService.showDownloadQueued(title);
    } else {
      NotificationService.showError(resp.error || 'Failed to send download to Glypdl');
    }
  });
}
