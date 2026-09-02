import { MediaItem } from '../shared/types';
import { normalizeUrl } from '../shared/utils';
import { HlsDashStrategy } from './strategies/hls_dash';

export class MediaDeduplicator {
  private seen = new Map<string, MediaItem>();

  /**
   * Add a media item if it is not a duplicate. Returns true if newly added or updated with better metadata.
   */
  add(item: MediaItem): boolean {
    // 1. If this is an HLS sub-variant playlist and we already have a stream on this page, absorb it
    const isSubVariant = item.format === 'm3u8' && HlsDashStrategy.isSubVariantPlaylist(item.url);

    // 2. Check for existing match across all stored items
    const existingMatch = this.findMatchingItem(item);

    if (existingMatch) {
      return this.mergeItems(existingMatch, item);
    }

    // If it is an unattached sub-variant but no master exists yet, record it with master priority flag
    const key = this.getDeduplicationKey(item);
    
    // Inherit thumbnail or title from existing page items if available
    this.propagatePageMetadata(item);

    this.seen.set(key, item);
    return true;
  }

  getAll(): MediaItem[] {
    return Array.from(this.seen.values()).sort((a, b) => b.timestamp - a.timestamp);
  }

  get(id: string): MediaItem | undefined {
    for (const it of this.seen.values()) {
      if (it.id === id) return it;
    }
    return undefined;
  }

  clear(): void {
    this.seen.clear();
  }

  private findMatchingItem(item: MediaItem): MediaItem | undefined {
    const itemNormUrl = normalizeUrl(item.url);
    const itemNormPage = normalizeUrl(item.pageUrl);

    for (const existing of this.seen.values()) {
      const existNormUrl = normalizeUrl(existing.url);
      const existNormPage = normalizeUrl(existing.pageUrl);

      // 1. Exact URL match
      if (existNormUrl === itemNormUrl) {
        return existing;
      }

      // 2. YouTube canonical video match
      if (item.sourceStrategy === 'youtube' || existing.sourceStrategy === 'youtube') {
        if (existNormPage === itemNormPage) {
          return existing;
        }
      }

      // 3. DOM fallback vs Real Stream match on same page:
      // When DOM element was using MSE/blob and registered pageUrl, but network sniffer found real stream
      if (existNormPage === itemNormPage) {
        if (existNormUrl === existNormPage || itemNormUrl === itemNormPage) {
          return existing;
        }

        // 4. Same HLS / DASH stream cluster on the same page
        if ((existing.type === 'hls' || existing.format === 'm3u8') && (item.type === 'hls' || item.format === 'm3u8')) {
          if (this.isSameHlsCluster(existing.url, item.url)) {
            return existing;
          }
        }

        if ((existing.type === 'dash' || existing.format === 'mpd') && (item.type === 'dash' || item.format === 'mpd')) {
          return existing;
        }
      }
    }

    return undefined;
  }

  private isSameHlsCluster(url1: string, url2: string): boolean {
    try {
      const u1 = new URL(url1);
      const u2 = new URL(url2);
      if (u1.hostname !== u2.hostname) return false;

      // Check if they share the same directory path or base stream identifier
      const p1 = u1.pathname.substring(0, u1.pathname.lastIndexOf('/'));
      const p2 = u2.pathname.substring(0, u2.pathname.lastIndexOf('/'));
      if (p1 === p2) return true;

      // Or one is parent of the other
      if (p1.startsWith(p2) || p2.startsWith(p1)) return true;
    } catch {
      // Ignored
    }
    return false;
  }

  private mergeItems(existing: MediaItem, incoming: MediaItem): boolean {
    let updated = false;

    // 1. URL & Format Selection: Prefer direct master stream URL over pageUrl fallback or sub-rendition
    const existingIsFallback = normalizeUrl(existing.url) === normalizeUrl(existing.pageUrl);
    const incomingIsFallback = normalizeUrl(incoming.url) === normalizeUrl(incoming.pageUrl);
    const incomingIsMaster = incoming.format === 'm3u8' && HlsDashStrategy.isMasterPlaylist(incoming.url);
    const existingIsMaster = existing.format === 'm3u8' && HlsDashStrategy.isMasterPlaylist(existing.url);
    const incomingIsSubVariant = incoming.format === 'm3u8' && HlsDashStrategy.isSubVariantPlaylist(incoming.url);

    if (existingIsFallback && !incomingIsFallback) {
      existing.url = incoming.url;
      existing.format = incoming.format;
      existing.type = incoming.type;
      existing.mimeType = incoming.mimeType;
      updated = true;
    } else if (!existingIsMaster && incomingIsMaster && !incomingIsSubVariant) {
      existing.url = incoming.url;
      existing.format = incoming.format;
      existing.type = incoming.type;
      existing.mimeType = incoming.mimeType;
      updated = true;
    }

    // 2. Title: Prefer human-readable title over generic "HLS Stream...", "Video from...", "Media from..."
    const existingIsGenericTitle = this.isGenericTitle(existing.title);
    const incomingIsGenericTitle = this.isGenericTitle(incoming.title);

    if (existingIsGenericTitle && !incomingIsGenericTitle && incoming.title) {
      existing.title = incoming.title;
      updated = true;
    }

    // 3. Thumbnail
    if (!existing.thumbnailUrl && incoming.thumbnailUrl) {
      existing.thumbnailUrl = incoming.thumbnailUrl;
      updated = true;
    }

    // 4. Duration
    if ((!existing.duration || existing.duration <= 0) && incoming.duration && incoming.duration > 0) {
      existing.duration = incoming.duration;
      existing.formattedDuration = incoming.formattedDuration;
      updated = true;
    }

    // 5. File Size
    if ((!existing.fileSize || existing.fileSize <= 0) && incoming.fileSize && incoming.fileSize > 0) {
      existing.fileSize = incoming.fileSize;
      existing.formattedSize = incoming.formattedSize;
      updated = true;
    }

    // 6. Quality (e.g. 1080p over auto)
    if ((existing.quality === 'auto' || !existing.quality) && incoming.quality && incoming.quality !== 'auto') {
      existing.quality = incoming.quality;
      updated = true;
    }

    return updated;
  }

  private propagatePageMetadata(item: MediaItem): void {
    const itemNormPage = normalizeUrl(item.pageUrl);

    for (const existing of this.seen.values()) {
      if (normalizeUrl(existing.pageUrl) === itemNormPage) {
        if (!item.thumbnailUrl && existing.thumbnailUrl) {
          item.thumbnailUrl = existing.thumbnailUrl;
        }
        if (this.isGenericTitle(item.title) && !this.isGenericTitle(existing.title) && existing.title) {
          item.title = existing.title;
        }
        if ((!item.duration || item.duration <= 0) && existing.duration && existing.duration > 0) {
          item.duration = existing.duration;
          item.formattedDuration = existing.formattedDuration;
        }
        if ((item.quality === 'auto' || !item.quality) && existing.quality && existing.quality !== 'auto') {
          item.quality = existing.quality;
        }
        break;
      }
    }
  }

  private isGenericTitle(title?: string): boolean {
    if (!title) return true;
    const lower = title.toLowerCase();
    return (
      lower.startsWith('media from') ||
      lower.startsWith('video from') ||
      lower.startsWith('hls stream from') ||
      lower.startsWith('dash stream from') ||
      lower.startsWith('audio stream from')
    );
  }

  private getDeduplicationKey(item: MediaItem): string {
    if (item.sourceStrategy === 'youtube') {
      return `youtube:${normalizeUrl(item.pageUrl)}`;
    }
    return `${normalizeUrl(item.url)}:${item.type}:${item.format}`;
  }
}
