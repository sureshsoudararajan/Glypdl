import { MediaItem } from '../../shared/types';
import { extractDomain, formatSize, generateMediaId, inferFormatFromMime, inferFormatFromUrl } from '../../shared/utils';

export class DirectMediaStrategy {
  /**
   * Create MediaItem from a direct media resource URL.
   */
  static createDirectItem(
    url: string,
    pageUrl: string,
    title?: string,
    contentLength?: number,
    mimeType?: string
  ): MediaItem | null {
    let inferred = mimeType ? inferFormatFromMime(mimeType) : null;
    if (!inferred) {
      inferred = inferFormatFromUrl(url);
    }
    if (!inferred) return null;

    let cleanTitle = title;
    if (!cleanTitle) {
      try {
        const parsed = new URL(url);
        const name = parsed.pathname.split('/').pop();
        if (name && name.includes('.')) {
          cleanTitle = decodeURIComponent(name);
        }
      } catch {
        cleanTitle = `Direct ${inferred.type} (${inferred.format})`;
      }
    }

    return {
      id: generateMediaId(url, pageUrl),
      url,
      pageUrl,
      title: cleanTitle || `Media from ${extractDomain(pageUrl)}`,
      type: inferred.type,
      format: inferred.format,
      quality: inferred.type === 'video' ? '1080p' : 'audio',
      fileSize: contentLength,
      formattedSize: formatSize(contentLength),
      mimeType,
      site: extractDomain(pageUrl),
      timestamp: Date.now(),
      sourceStrategy: 'direct'
    };
  }
}
