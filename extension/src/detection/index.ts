import { ExtensionSettings, MediaItem } from '../shared/types';
import { MediaDeduplicator } from './deduplicator';
import { MediaFilter } from './filter';
import { DirectMediaStrategy } from './strategies/direct_media';
import { HlsDashStrategy } from './strategies/hls_dash';
import { Html5Strategy } from './strategies/html5';
import { YouTubeStrategy } from './strategies/youtube';

export class MediaDetector {
  private deduplicator = new MediaDeduplicator();

  /**
   * Scan active page DOM and extract all valid media items.
   */
  scanDocument(pageUrl: string, settings: ExtensionSettings, doc: Document = document): MediaItem[] {
    // 1. YouTube specific strategy (highest priority)
    if (YouTubeStrategy.isYouTubePage(pageUrl)) {
      const ytItem = YouTubeStrategy.detectFromPage(pageUrl, doc);
      if (ytItem && MediaFilter.isValidMedia(ytItem, settings)) {
        this.deduplicator.add(ytItem);
      }
    }

    // 2. HTML5 Media Elements
    const mediaElements = Array.from(doc.querySelectorAll<HTMLMediaElement>('video, audio'));
    for (const elem of mediaElements) {
      const item = Html5Strategy.detectFromElement(elem, pageUrl, doc);
      if (item && MediaFilter.isValidMedia(item, settings)) {
        this.deduplicator.add(item);
      }
    }

    // 3. Anchor tags pointing directly to media
    const anchors = Array.from(doc.querySelectorAll<HTMLAnchorElement>('a[href]'));
    for (const a of anchors) {
      const href = a.href;
      if (!href || href.startsWith('javascript:') || href.startsWith('#')) continue;

      const streamType = HlsDashStrategy.isHlsOrDash(href);
      if (streamType) {
        const item = HlsDashStrategy.createStreamItem(href, streamType, pageUrl, a.textContent?.trim());
        if (MediaFilter.isValidMedia(item, settings)) {
          this.deduplicator.add(item);
        }
      } else {
        const direct = DirectMediaStrategy.createDirectItem(href, pageUrl, a.textContent?.trim());
        if (direct && MediaFilter.isValidMedia(direct, settings)) {
          this.deduplicator.add(direct);
        }
      }
    }

    return this.deduplicator.getAll();
  }

  /**
   * Add a single detected item (e.g. from network or player event).
   */
  addItem(item: MediaItem, settings: ExtensionSettings): boolean {
    if (MediaFilter.isValidMedia(item, settings)) {
      return this.deduplicator.add(item);
    }
    return false;
  }

  getItems(): MediaItem[] {
    return this.deduplicator.getAll();
  }

  getItem(id: string): MediaItem | undefined {
    return this.deduplicator.get(id);
  }

  clear(): void {
    this.deduplicator.clear();
  }
}
