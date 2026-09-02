import { MediaFilter } from '../../detection/filter';
import { DirectMediaStrategy } from '../../detection/strategies/direct_media';
import { HlsDashStrategy } from '../../detection/strategies/hls_dash';
import { MediaItem } from '../../shared/types';
import { cleanFilenameToTitle, extractDomain, generateMediaId, inferFormatFromMime, inferFormatFromUrl, inferMediaQuality } from '../../shared/utils';

// @ts-expect-error browser runtime
const browserApi = typeof browser !== 'undefined' ? browser : typeof chrome !== 'undefined' ? chrome : null;

export class NetworkMediaSniffer {
  private onMediaDetected: (tabId: number, item: MediaItem) => void;

  constructor(onMediaDetected: (tabId: number, item: MediaItem) => void) {
    this.onMediaDetected = onMediaDetected;
  }

  start(): void {
    if (!browserApi?.webRequest?.onHeadersReceived) return;

    try {
      browserApi.webRequest.onHeadersReceived.addListener(
        (details: any) => this.handleHeadersReceived(details),
        { urls: ['<all_urls>'] },
        ['responseHeaders']
      );
    } catch (e) {
      console.warn('Could not register webRequest media sniffer:', e);
    }
  }

  private handleHeadersReceived(details: any): void {
    const tabId = details.tabId;
    // Ignore requests not associated with a browser tab or main page
    if (!tabId || tabId < 0) return;

    const url = details.url;
    if (!url || MediaFilter.isIgnoredUrl(url)) return;

    // Ignore individual small stream segment chunks (.ts files unless it's a playlist or large stream)
    if (/\.(?:ts|m4s|aac|dash)(?:\?.*)?$/i.test(url) && !url.includes('playlist') && !url.includes('master')) {
      return;
    }

    let contentType = '';
    let contentLength = 0;

    if (details.responseHeaders && Array.isArray(details.responseHeaders)) {
      for (const h of details.responseHeaders) {
        const name = (h.name || '').toLowerCase();
        if (name === 'content-type') {
          contentType = (h.value || '').toLowerCase();
        } else if (name === 'content-length') {
          contentLength = parseInt(h.value || '0', 10);
        }
      }
    }

    // 1. Check for HLS (.m3u8) or DASH (.mpd) stream manifests
    const streamType = HlsDashStrategy.isHlsOrDash(url, contentType);
    if (streamType) {
      const item = HlsDashStrategy.createStreamItem(url, streamType, details.initiator || url);
      this.onMediaDetected(tabId, item);
      return;
    }

    // 2. Check for direct video/audio content types or file extensions
    const mimeInferred = inferFormatFromMime(contentType);
    const urlInferred = inferFormatFromUrl(url);
    const inferred = mimeInferred || urlInferred;

    if (inferred) {
      // Ignore tiny non-media or thumbnail requests (less than 120KB)
      if (contentLength > 0 && contentLength < 120 * 1024 && inferred.type !== 'audio') {
        return;
      }

      let title = '';
      try {
        const parsed = new URL(url);
        const filename = parsed.pathname.split('/').pop() || '';
        title = cleanFilenameToTitle(decodeURIComponent(filename));
      } catch {
        // Ignored
      }

      if (!title) {
        title = `${inferred.type.toUpperCase()} stream from ${extractDomain(details.initiator || url)}`;
      }

      const quality = inferred.type === 'video' ? inferMediaQuality(url) : 'audio';

      const item: MediaItem = {
        id: generateMediaId(url, details.initiator || url),
        url,
        pageUrl: details.initiator || url,
        title,
        type: inferred.type,
        format: inferred.format,
        quality,
        fileSize: contentLength > 0 ? contentLength : undefined,
        mimeType: contentType,
        site: extractDomain(details.initiator || url),
        timestamp: Date.now(),
        sourceStrategy: 'network'
      };

      this.onMediaDetected(tabId, item);
    }
  }
}
