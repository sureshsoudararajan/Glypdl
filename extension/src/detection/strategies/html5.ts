import { MediaFormat, MediaItem, MediaQuality, MediaType } from '../../shared/types';
import {
  cleanFilenameToTitle,
  extractDomain,
  formatDuration,
  generateMediaId,
  inferFormatFromMime,
  inferFormatFromUrl,
  inferMediaQuality
} from '../../shared/utils';
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

    // 1. Accurately infer quality from resolution in URL tokens and video dimensions
    const quality: MediaQuality = type === 'video' ? inferMediaQuality(src, element) : 'audio';

    // 2. Find closest card / article container for element-scoped metadata
    const cardContainer = element.closest(
      'article, [class*="card"], [class*="item"], [class*="media"], [data-testid*="video"], a, div[class*="video"]'
    );

    // 3. Extract thumbnail scoped strictly to this media element / card
    let thumbnailUrl: string | undefined;
    if (element instanceof HTMLVideoElement && element.poster) {
      thumbnailUrl = element.poster;
    }
    if (!thumbnailUrl && element.getAttribute('data-poster')) {
      thumbnailUrl = element.getAttribute('data-poster') || undefined;
    }

    // Look for image inside the same card
    if (!thumbnailUrl && cardContainer) {
      const cardImg = cardContainer.querySelector('img') as HTMLImageElement | null;
      if (cardImg) {
        thumbnailUrl = cardImg.currentSrc || cardImg.src || cardImg.getAttribute('data-src') || cardImg.getAttribute('srcset')?.split(' ')[0] || undefined;
      }
    }

    // Only fallback to global og:image if there is ONLY 1 video element on the whole page
    const allVideos = doc.querySelectorAll('video');
    if (!thumbnailUrl && allVideos.length <= 1) {
      const ogImg = doc.querySelector('meta[property="og:image"]')?.getAttribute('content');
      if (ogImg) thumbnailUrl = ogImg;
    }

    // 4. Extract title scoped strictly to this media element / card
    let title = element.getAttribute('title') || element.getAttribute('aria-label') || '';

    if (!title && cardContainer) {
      const cardHeading = cardContainer.querySelector('h1, h2, h3, h4, [class*="title"], [class*="heading"], a[title]');
      if (cardHeading) {
        title = cardHeading.getAttribute('title') || cardHeading.textContent?.trim() || '';
      }
      if (!title) {
        const cardImg = cardContainer.querySelector('img');
        if (cardImg && cardImg.alt && cardImg.alt.trim().length > 2) {
          title = cardImg.alt.trim();
        }
      }
    }

    // If still no title, extract human-readable title from URL filename
    if (!title) {
      try {
        const parsed = new URL(src);
        const filename = parsed.pathname.split('/').pop() || '';
        const cleaned = cleanFilenameToTitle(decodeURIComponent(filename));
        if (cleaned && cleaned.length > 3) {
          title = cleaned;
        }
      } catch {
        // Ignored
      }
    }

    // Only fallback to global page title if this is the ONLY video on the page
    if (!title && allVideos.length <= 1) {
      const ogTitle = doc.querySelector('meta[property="og:title"]')?.getAttribute('content');
      if (ogTitle) title = ogTitle.trim();
    }
    if (!title && allVideos.length <= 1) {
      title = doc.title;
    }

    if (!title) {
      title = `Video from ${extractDomain(pageUrl)}`;
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
