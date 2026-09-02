import { MediaItem } from '../../shared/types';
import {
  cleanFilenameToTitle,
  extractDomain,
  formatSize,
  generateMediaId,
  inferFormatFromMime,
  inferFormatFromUrl,
  inferMediaQuality
} from '../../shared/utils';

export class DirectMediaStrategy {
  /**
   * Create MediaItem from a direct media resource URL.
   */
  static createDirectItem(
    url: string,
    pageUrl: string,
    title?: string,
    contentLength?: number,
    mimeType?: string,
    thumbnailUrl?: string
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
          cleanTitle = cleanFilenameToTitle(decodeURIComponent(name));
        }
      } catch {
        cleanTitle = `Direct ${inferred.type} (${inferred.format})`;
      }
    }

    const quality = inferred.type === 'video' ? inferMediaQuality(url) : 'audio';

    return {
      id: generateMediaId(url, pageUrl),
      url,
      pageUrl,
      title: cleanTitle || `Media from ${extractDomain(pageUrl)}`,
      thumbnailUrl,
      type: inferred.type,
      format: inferred.format,
      quality,
      fileSize: contentLength,
      formattedSize: formatSize(contentLength),
      mimeType,
      site: extractDomain(pageUrl),
      timestamp: Date.now(),
      sourceStrategy: 'direct'
    };
  }
}
