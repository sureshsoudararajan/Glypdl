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
   * Check if an HLS URL is an internal sub-rendition, chunklist, or segment playlist
   * rather than the primary/master playlist.
   */
  static isSubVariantPlaylist(url: string): boolean {
    const cleanUrl = url.toLowerCase();
    if (/(?:chunklist|layer_|sub-|subtitle|audio[\/_]|tracks?[\/-]|rendition[\/-]|frag-|seg-|\bpart\d+\.m3u8|\/audio\/|\/subtitles?\/)/i.test(cleanUrl)) {
      return true;
    }
    return false;
  }

  /**
   * Check if an HLS URL is likely the top-level Master / Root playlist.
   */
  static isMasterPlaylist(url: string): boolean {
    const cleanUrl = url.toLowerCase();
    if (this.isSubVariantPlaylist(cleanUrl)) return false;
    return /(?:master|playlist|manifest|main|video|all|index|hls|stream)\.m3u8/i.test(cleanUrl) || cleanUrl.endsWith('.m3u8');
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
