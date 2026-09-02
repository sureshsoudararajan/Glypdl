import { MediaFormat, MediaItem, MediaQuality, MediaType } from '../../shared/types';
import { extractDomain, formatDuration, generateMediaId, inferFormatFromMime, inferFormatFromUrl } from '../../shared/utils';
import { DrmDetector } from '../drm';

export class Html5Strategy {
  /**
   * Inspect HTMLMediaElement (<video> or <audio>) and extract MediaItem.
   */
  static detectFromElement(element: HTMLMediaElement, pageUrl: string, doc: Document = document): MediaItem | null {
    if (!element) return null;

    const isVideo = element.tagName.toLowerCase() === 'video';
    const rawSrc = element.currentSrc || element.src;

    if (!rawSrc || rawSrc.startsWith('blob:') || rawSrc.startsWith('mediasource:')) {
      // If src is empty or blob, check child <source> elements
      const sources = Array.from(element.querySelectorAll('source'));
      for (const s of sources) {
        if (s.src && !s.src.startsWith('blob:')) {
          return this.createMediaItem(s.src, isVideo ? 'video' : 'audio', element, pageUrl, doc, s.type);
        }
      }
      return null;
    }

    return this.createMediaItem(rawSrc, isVideo ? 'video' : 'audio', element, pageUrl, doc);
  }

  private static createMediaItem(
    src: string,
    defaultType: MediaType,
    element: HTMLMediaElement,
    pageUrl: string,
    doc: Document,
    mimeType?: string
  ): MediaItem {
    const isProtected = DrmDetector.isElementDrmProtected(element);

    let format: MediaFormat = defaultType === 'video' ? 'mp4' : 'mp3';
    let type: MediaType = defaultType;

    if (mimeType) {
      const inferred = inferFormatFromMime(mimeType);
      if (inferred) {
        format = inferred.format;
        type = inferred.type;
      }
    } else {
      const inferred = inferFormatFromUrl(src);
      if (inferred) {
        format = inferred.format;
        type = inferred.type;
      }
    }

    // Infer quality from video dimensions
    let quality: MediaQuality = type === 'video' ? '1080p' : 'audio';
    if (element instanceof HTMLVideoElement && element.videoHeight > 0) {
      const h = element.videoHeight;
      if (h >= 2160) quality = '2160p';
      else if (h >= 1440) quality = '1440p';
      else if (h >= 1080) quality = '1080p';
      else if (h >= 720) quality = '720p';
      else if (h >= 480) quality = '480p';
      else if (h >= 360) quality = '360p';
      else quality = '240p';
    }

    // Extract title from page or element attributes
    let title = element.getAttribute('title') || element.getAttribute('aria-label') || '';
    if (!title) {
      title = doc.title || `Media from ${extractDomain(pageUrl)}`;
    }

    // Poster thumbnail for videos
    let thumbnailUrl: string | undefined;
    if (element instanceof HTMLVideoElement && element.poster) {
      thumbnailUrl = element.poster;
    }

    const duration = element.duration && !isNaN(element.duration) && element.duration > 0 ? element.duration : undefined;

    return {
      id: generateMediaId(src, pageUrl),
      url: src,
      pageUrl,
      title: title.trim(),
      thumbnailUrl,
      duration,
      formattedDuration: formatDuration(duration),
      type,
      format,
      quality,
      isProtected,
      mimeType,
      site: extractDomain(pageUrl),
      timestamp: Date.now(),
      sourceStrategy: 'html5'
    };
  }
}
