import { MediaItem } from '../../shared/types';
import { extractDomain, generateMediaId } from '../../shared/utils';
import { DrmDetector } from '../drm';

export class HlsDashStrategy {
  /**
   * Check if a URL represents an HLS playlist or DASH manifest.
   */
  static isHlsOrDash(url: string, mime?: string): 'hls' | 'dash' | null {
    const cleanUrl = url.toLowerCase();
    if (cleanUrl.includes('.m3u8') || (mime && (mime.includes('mpegurl') || mime.includes('x-mpegurl')))) {
      return 'hls';
    }
    if (cleanUrl.includes('.mpd') || (mime && mime.includes('dash+xml'))) {
      return 'dash';
    }
    return null;
  }

  /**
   * Create MediaItem for HLS or DASH stream.
   */
  static createStreamItem(url: string, type: 'hls' | 'dash', pageUrl: string, title?: string, mime?: string): MediaItem {
    const isProtected = DrmDetector.isDrmUrlOrMime(url, mime);

    return {
      id: generateMediaId(url, pageUrl),
      url,
      pageUrl,
      title: title || `${type.toUpperCase()} Stream from ${extractDomain(pageUrl)}`,
      type,
      format: type === 'hls' ? 'm3u8' : 'mpd',
      quality: 'auto',
      isProtected,
      mimeType: mime,
      site: extractDomain(pageUrl),
      timestamp: Date.now(),
      sourceStrategy: type
    };
  }
}
