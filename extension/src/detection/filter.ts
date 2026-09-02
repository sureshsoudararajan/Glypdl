import { IGNORED_URL_PATTERNS } from '../shared/constants';
import { ExtensionSettings, MediaItem } from '../shared/types';

export class MediaFilter {
  /**
   * Check if a URL should be filtered out (ads, tracking, analytics, non-media).
   */
  static isIgnoredUrl(url: string): boolean {
    if (!url || typeof url !== 'string') return true;
    if (url.startsWith('javascript:') || url.startsWith('about:') || url.startsWith('chrome:') || url.startsWith('resource:')) {
      return true;
    }
    if (url.startsWith('data:') && !url.startsWith('data:video') && !url.startsWith('data:audio')) {
      return true;
    }
    return IGNORED_URL_PATTERNS.some((pattern) => pattern.test(url));
  }

  /**
   * Validate if a detected media item passes user filtering criteria.
   */
  static isValidMedia(item: Partial<MediaItem>, settings: ExtensionSettings): boolean {
    if (!item.url || !item.url.trim()) return false;
    if (this.isIgnoredUrl(item.url)) return false;

    // Check minimum file size if available
    if (item.fileSize !== undefined && item.fileSize > 0) {
      const minBytes = (settings.minFileSizeKb || 0) * 1024;
      if (item.fileSize < minBytes) {
        return false;
      }
    }

    // Filter by type toggles
    if (item.type === 'video' && !settings.detectHtml5Video && item.sourceStrategy !== 'youtube') {
      return false;
    }
    if (item.type === 'audio' && !settings.detectHtml5Audio) {
      return false;
    }
    if (item.type === 'hls' && !settings.detectHls) {
      return false;
    }
    if (item.type === 'dash' && !settings.detectDash) {
      return false;
    }

    return true;
  }
}
