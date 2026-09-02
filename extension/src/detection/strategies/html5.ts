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

      // If playing a video via blob/MSE, capture rich metadata with pageUrl fallback (deduplicator will upgrade URL)
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

    // 2. Find player container / card container
    const playerContainer = element.closest(
      '.video-js, .plyr, .jwplayer, .flowplayer, [class*="player"], [id*="player"], [class*="video-container"], article, [class*="card"], [class*="item"], [class*="media"], [data-testid*="video"], a, div[class*="video"]'
    );

    // 3. Extract thumbnail through multi-layer detection (poster, CSS backgrounds, JSON-LD, canvas, meta tags)
    const thumbnailUrl = this.extractThumbnail(element, playerContainer, doc);

    // 4. Extract title scoped strictly to this media element / player / page
    let title = this.extractTitle(element, playerContainer, doc, src, pageUrl);

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

  private static extractThumbnail(
    element: HTMLMediaElement,
    playerContainer: Element | null,
    doc: Document
  ): string | undefined {
    // 1. Native poster attribute
    if (element instanceof HTMLVideoElement && element.poster) {
      return element.poster;
    }
    const dataPoster =
      element.getAttribute('data-poster') ||
      element.getAttribute('data-thumb') ||
      element.getAttribute('data-thumbnail') ||
      element.getAttribute('data-preview') ||
      element.getAttribute('data-image') ||
      element.getAttribute('data-cover');
    if (dataPoster) return dataPoster;

    // 2. Player poster elements (Video.js, Plyr, JWPlayer, etc.)
    if (playerContainer) {
      // Direct poster element or background style
      const posterElem = playerContainer.querySelector(
        '.vjs-poster, .plyr__poster, .jw-preview, .jw-poster, [class*="poster"], [class*="thumb"], [class*="preview"], [class*="cover"]'
      );
      if (posterElem) {
        // Check inline style for url(...)
        const style = posterElem.getAttribute('style') || '';
        const bgMatch = style.match(/url\(['"]?([^'"]+)['"]?\)/i);
        if (bgMatch && bgMatch[1] && !bgMatch[1].startsWith('data:image/svg')) {
          return bgMatch[1];
        }

        // Check img inside poster element
        const posterImg = posterElem.querySelector('img') as HTMLImageElement | null;
        if (posterImg) {
          const src = posterImg.currentSrc || posterImg.src || posterImg.getAttribute('data-src');
          if (src) return src;
        }
      }

      // Look for any image inside player container
      const containerImg = playerContainer.querySelector('img') as HTMLImageElement | null;
      if (containerImg) {
        const src = containerImg.currentSrc || containerImg.src || containerImg.getAttribute('data-src') || containerImg.getAttribute('srcset')?.split(' ')[0];
        if (src && !src.includes('avatar') && !src.includes('icon') && !src.includes('logo')) {
          return src;
        }
      }
    }

    // 3. Try real-time canvas capture if video is currently loaded/playing
    if (element instanceof HTMLVideoElement && element.videoWidth > 0 && element.readyState >= 2) {
      try {
        const canvas = doc.createElement('canvas');
        canvas.width = Math.min(element.videoWidth, 320);
        canvas.height = Math.round((canvas.width / element.videoWidth) * element.videoHeight);
        const ctx = canvas.getContext('2d');
        if (ctx) {
          ctx.drawImage(element, 0, 0, canvas.width, canvas.height);
          const dataUrl = canvas.toDataURL('image/jpeg', 0.8);
          if (dataUrl && dataUrl.length > 500) {
            return dataUrl;
          }
        }
      } catch {
        // Cross-origin tainted canvas fallback
      }
    }

    // 4. Check JSON-LD metadata for video thumbnail
    try {
      const jsonLdScripts = doc.querySelectorAll('script[type="application/ld+json"]');
      for (const s of jsonLdScripts) {
        const text = s.textContent || '';
        if (text.includes('thumbnail') || text.includes('VideoObject') || text.includes('image')) {
          const parsed = JSON.parse(text);
          const item = Array.isArray(parsed) ? parsed[0] : parsed;
          const thumb = item?.thumbnailUrl || item?.thumbnail || item?.image?.url || (typeof item?.image === 'string' ? item.image : undefined);
          if (thumb && typeof thumb === 'string' && thumb.startsWith('http')) {
            return thumb;
          }
        }
      }
    } catch {
      // Ignored
    }

    // 5. Check OpenGraph / Twitter meta tags
    const ogImg =
      doc.querySelector('meta[property="og:image"]')?.getAttribute('content') ||
      doc.querySelector('meta[property="og:image:secure_url"]')?.getAttribute('content') ||
      doc.querySelector('meta[name="twitter:image"]')?.getAttribute('content') ||
      doc.querySelector('meta[name="twitter:image:src"]')?.getAttribute('content') ||
      doc.querySelector('link[rel="image_src"]')?.getAttribute('href');

    if (ogImg && ogImg.startsWith('http')) {
      return ogImg;
    }

    return undefined;
  }

  private static extractTitle(
    element: HTMLMediaElement,
    playerContainer: Element | null,
    doc: Document,
    src: string,
    pageUrl: string
  ): string {
    let title = element.getAttribute('title') || element.getAttribute('aria-label') || '';

    if (!title && playerContainer) {
      const cardHeading = playerContainer.querySelector('h1, h2, h3, h4, [class*="title"], [class*="heading"], a[title]');
      if (cardHeading) {
        title = cardHeading.getAttribute('title') || cardHeading.textContent?.trim() || '';
      }
      if (!title) {
        const cardImg = playerContainer.querySelector('img');
        if (cardImg && cardImg.alt && cardImg.alt.trim().length > 2) {
          title = cardImg.alt.trim();
        }
      }
    }

    // If still no title, try JSON-LD
    if (!title) {
      try {
        const jsonLdScripts = doc.querySelectorAll('script[type="application/ld+json"]');
        for (const s of jsonLdScripts) {
          const text = s.textContent || '';
          if (text.includes('name') || text.includes('headline')) {
            const parsed = JSON.parse(text);
            const item = Array.isArray(parsed) ? parsed[0] : parsed;
            const t = item?.name || item?.headline || item?.title;
            if (t && typeof t === 'string' && t.length > 2) {
              title = t;
              break;
            }
          }
        }
      } catch {
        // Ignored
      }
    }

    // If still no title, extract human-readable title from URL filename
    if (!title && src && !src.startsWith('blob:') && !src.startsWith('mediasource:')) {
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

    // Fallback to page title or og:title
    if (!title) {
      const ogTitle = doc.querySelector('meta[property="og:title"]')?.getAttribute('content') || doc.querySelector('meta[name="twitter:title"]')?.getAttribute('content');
      if (ogTitle) title = ogTitle.trim();
    }
    if (!title) {
      title = doc.title;
    }

    // Clean up generic suffixes (e.g. " - Watch Free Online | SiteName")
    if (title) {
      title = title.replace(/\s*[-|–—]\s*(?:YouTube|Watch|Free|Online|Video|HD).*$/i, '').trim();
    }

    if (!title) {
      title = `Video from ${extractDomain(pageUrl)}`;
    }

    return title;
  }
}
