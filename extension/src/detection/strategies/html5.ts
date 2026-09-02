import { MediaFormat, MediaItem, MediaQuality, MediaType } from '../../shared/types';
import { extractDomain, formatDuration, generateMediaId, inferFormatFromMime, inferFormatFromUrl } from '../../shared/utils';
import { DrmDetector } from '../drm';
import { YouTubeStrategy } from './youtube';

export class Html5Strategy {
  /**
   * Inspect HTMLMediaElement (<video> or <audio>) and extract MediaItem.
   */
  static detectFromElement(element: HTMLMediaElement, pageUrl: string, doc: Document = document): MediaItem | null {
    if (!element) return null;

    // If on YouTube, delegate to YouTubeStrategy
    if (YouTubeStrategy.isYouTubePage(pageUrl)) {
      return YouTubeStrategy.detectFromPage(pageUrl, doc);
    }

    const isVideo = element.tagName.toLowerCase() === 'video';
    let targetSrc = element.currentSrc || element.src || '';

    // Check data-src or data-video-src attributes
    if (!targetSrc || targetSrc.startsWith('blob:') || targetSrc.startsWith('mediasource:')) {
      const dataSrc = element.getAttribute('data-src') || element.getAttribute('data-video-src') || element.getAttribute('data-url');
      if (dataSrc && !dataSrc.startsWith('blob:')) {
        targetSrc = dataSrc;
      }
    }

    // If still blob or empty, check child <source> elements
    if (!targetSrc || targetSrc.startsWith('blob:') || targetSrc.startsWith('mediasource:')) {
      const sources = Array.from(element.querySelectorAll('source'));
      for (const s of sources) {
        const sSrc = s.src || s.getAttribute('data-src') || '';
        if (sSrc && !sSrc.startsWith('blob:') && !sSrc.startsWith('mediasource:')) {
          return this.createMediaItem(sSrc, isVideo ? 'video' : 'audio', element, pageUrl, doc, s.type);
        }
      }

      // If playing a video via blob/MSE (common on Pexels, Vimeo, etc.), capture the webpage as video source for yt-dlp
      if (isVideo && (element.readyState >= 1 || !element.paused || element.duration > 0)) {
        return this.createMediaItem(pageUrl, 'video', element, pageUrl, doc);
      }

      return null;
    }

    return this.createMediaItem(targetSrc, isVideo ? 'video' : 'audio', element, pageUrl, doc);
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
      const ogTitle = doc.querySelector('meta[property="og:title"]')?.getAttribute('content');
      if (ogTitle) title = ogTitle.trim();
    }
    if (!title) {
      const h1 = doc.querySelector('h1');
      if (h1 && h1.textContent) title = h1.textContent.trim();
    }
    if (!title) {
      title = doc.title || `Media from ${extractDomain(pageUrl)}`;
    }

    // Poster thumbnail for videos
    let thumbnailUrl: string | undefined;
    if (element instanceof HTMLVideoElement && element.poster) {
      thumbnailUrl = element.poster;
    }
    if (!thumbnailUrl) {
      const ogImg = doc.querySelector('meta[property="og:image"]')?.getAttribute('content');
      if (ogImg) thumbnailUrl = ogImg;
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
