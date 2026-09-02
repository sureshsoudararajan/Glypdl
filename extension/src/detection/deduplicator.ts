import { MediaItem } from '../shared/types';
import { normalizeUrl } from '../shared/utils';

export class MediaDeduplicator {
  private seen = new Map<string, MediaItem>();

  /**
   * Add a media item if it is not a duplicate. Returns true if newly added or updated with better metadata.
   */
  add(item: MediaItem): boolean {
    const key = this.getDeduplicationKey(item);
    const existing = this.seen.get(key);

    if (!existing) {
      this.seen.set(key, item);
      return true;
    }

    // Merge / update with richer metadata (e.g. thumbnail, size, title)
    let updated = false;
    if (!existing.thumbnailUrl && item.thumbnailUrl) {
      existing.thumbnailUrl = item.thumbnailUrl;
      updated = true;
    }
    if ((!existing.duration || existing.duration <= 0) && item.duration && item.duration > 0) {
      existing.duration = item.duration;
      existing.formattedDuration = item.formattedDuration;
      updated = true;
    }
    if ((!existing.fileSize || existing.fileSize <= 0) && item.fileSize && item.fileSize > 0) {
      existing.fileSize = item.fileSize;
      existing.formattedSize = item.formattedSize;
      updated = true;
    }
    if (existing.title.startsWith('Media from') && item.title && !item.title.startsWith('Media from')) {
      existing.title = item.title;
      updated = true;
    }

    return updated;
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

  private getDeduplicationKey(item: MediaItem): string {
    if (item.sourceStrategy === 'youtube') {
      return `youtube:${normalizeUrl(item.pageUrl)}`;
    }
    return `${normalizeUrl(item.url)}:${item.type}:${item.format}`;
  }
}
