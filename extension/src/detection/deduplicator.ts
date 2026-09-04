import { MediaItem } from '../shared/types';
import { extractDomain, normalizeUrl } from '../shared/utils';
import { HlsDashStrategy } from './strategies/hls_dash';

export class MediaDeduplicator {
  private items: MediaItem[] = [];

  /**
   * Add a media item if it is not a duplicate. Returns true if newly added or updated with better metadata.
   */
  add(item: MediaItem): boolean {
    const existingMatch = this.findMatchingItem(item);

    if (existingMatch) {
      return this.mergeItems(existingMatch, item);
    }

    // Inherit thumbnail, duration or title from existing items if available
    this.propagatePageMetadata(item);

    this.items.unshift(item);
    return true;
  }

  getAll(): MediaItem[] {
    return [...this.items].sort((a, b) => b.timestamp - a.timestamp);
  }

  get(id: string): MediaItem | undefined {
    return this.items.find((it) => it.id === id);
  }

  clear(): void {
    this.items = [];
  }

  private findMatchingItem(item: MediaItem): MediaItem | undefined {
    const itemNormUrl = normalizeUrl(item.url);
    const itemNormPage = normalizeUrl(item.pageUrl);
    const itemDomain = extractDomain(item.pageUrl) || extractDomain(item.url);
    const itemIsHls = item.type === 'hls' || item.format === 'm3u8' || item.url.includes('.m3u8');
    const itemIsDash = item.type === 'dash' || item.format === 'mpd' || item.url.includes('.mpd');
    const itemIsFallback = itemNormUrl === itemNormPage || item.sourceStrategy === 'html5' && !this.hasDirectMediaExt(item.url);

    for (const existing of this.items) {
      const existNormUrl = normalizeUrl(existing.url);
      const existNormPage = normalizeUrl(existing.pageUrl);
      const existDomain = extractDomain(existing.pageUrl) || extractDomain(existing.url);
      const existIsHls = existing.type === 'hls' || existing.format === 'm3u8' || existing.url.includes('.m3u8');
      const existIsDash = existing.type === 'dash' || existing.format === 'mpd' || existing.url.includes('.mpd');
      const existIsFallback = existNormUrl === existNormPage || existing.sourceStrategy === 'html5' && !this.hasDirectMediaExt(existing.url);

      // 1. Exact URL match
      if (existNormUrl === itemNormUrl) {
        return existing;
      }

      // 2. YouTube & Instagram canonical video match (only when on the same video/story page)
      if (item.sourceStrategy === 'youtube' || existing.sourceStrategy === 'youtube') {
        if (existNormPage === itemNormPage) {
          return existing;
        }
      }
      if (item.sourceStrategy === 'instagram' || existing.sourceStrategy === 'instagram') {
        if (existNormPage === itemNormPage) {
          return existing;
        }
      }

      // 3. Match DOM fallback item with real downloadable stream on the same page/tab
      if ((existIsFallback && !itemIsFallback) || (!existIsFallback && itemIsFallback)) {
        return existing;
      }

      // 4. Same HLS stream cluster on the tab (master playlist vs rendition/variant playlists)
      if (existIsHls && itemIsHls) {
        return existing;
      }

      // 5. Same DASH stream cluster on the tab
      if (existIsDash && itemIsDash) {
        return existing;
      }

      // 6. Same direct media filename / video id in URL
      if (this.isSameDirectMedia(existing.url, item.url)) {
        return existing;
      }
    }

    return undefined;
  }

  private hasDirectMediaExt(url: string): boolean {
    return /\.(?:mp4|webm|mkv|mov|flv|mp3|m4a|aac|flac|wav|ogg|opus|m3u8|mpd)(?:[?#]|$)/i.test(url);
  }

  private isSameDirectMedia(url1: string, url2: string): boolean {
    try {
      const p1 = new URL(url1).pathname.split('/').pop();
      const p2 = new URL(url2).pathname.split('/').pop();
      if (p1 && p2 && p1 === p2 && p1.length > 4) {
        return true;
      }
    } catch {
      // Ignored
    }
    return false;
  }

  private mergeItems(existing: MediaItem, incoming: MediaItem): boolean {
    let updated = false;

    const existingIsFallback = normalizeUrl(existing.url) === normalizeUrl(existing.pageUrl) || !this.hasDirectMediaExt(existing.url);
    const incomingIsFallback = normalizeUrl(incoming.url) === normalizeUrl(incoming.pageUrl) || !this.hasDirectMediaExt(incoming.url);
    const incomingIsMaster = HlsDashStrategy.isMasterPlaylist(incoming.url);
    const existingIsMaster = HlsDashStrategy.isMasterPlaylist(existing.url);
    const incomingIsSubVariant = HlsDashStrategy.isSubVariantPlaylist(incoming.url);

    // 1. Upgrade URL to real downloadable master stream or preserve canonical platform URL
    if (existing.sourceStrategy === 'youtube' || incoming.sourceStrategy === 'youtube') {
      existing.url = existing.pageUrl;
      existing.sourceStrategy = 'youtube';
    } else if (existing.sourceStrategy === 'instagram' || incoming.sourceStrategy === 'instagram') {
      existing.url = existing.pageUrl;
      existing.sourceStrategy = 'instagram';
    } else if (existingIsFallback && !incomingIsFallback) {
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

    // 2. Title: Prefer human-readable video title over generic placeholder
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
    for (const existing of this.items) {
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

  private isGenericTitle(title?: string): boolean {
    if (!title) return true;
    const lower = title.toLowerCase();
    if (
      lower.startsWith('media from') ||
      lower.startsWith('video from') ||
      lower.startsWith('hls stream from') ||
      lower.startsWith('dash stream from') ||
      lower.startsWith('audio stream from')
    ) {
      return true;
    }
    // Long cryptic hashes without spaces (e.g. fbcdn chunk names)
    if (!title.includes(' ') && title.length > 25 && /^[A-Za-z0-9_-]+$/.test(title)) {
      return true;
    }
    return false;
  }
}
