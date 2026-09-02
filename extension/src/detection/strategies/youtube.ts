import { MediaItem } from '../../shared/types';
import { extractDomain, formatDuration, generateMediaId } from '../../shared/utils';

export class YouTubeStrategy {
  /**
   * Check if the given page URL is a YouTube video, Shorts, or embed page.
   */
  static isYouTubePage(url: string): boolean {
    if (!url) return false;
    return (
      url.includes('youtube.com/watch') ||
      url.includes('youtube.com/shorts/') ||
      url.includes('youtube.com/embed/') ||
      url.includes('youtube.com/v/') ||
      url.includes('youtu.be/')
    );
  }

  /**
   * Extract video ID from YouTube URL.
   */
  static extractVideoId(url: string): string | null {
    try {
      const parsed = new URL(url);
      if (parsed.hostname.includes('youtu.be')) {
        return parsed.pathname.slice(1).split('/')[0] || null;
      }
      if (parsed.pathname.includes('/shorts/')) {
        return parsed.pathname.split('/shorts/')[1].split('/')[0] || null;
      }
      if (parsed.pathname.includes('/embed/')) {
        return parsed.pathname.split('/embed/')[1].split('/')[0] || null;
      }
      return parsed.searchParams.get('v');
    } catch {
      return null;
    }
  }

  /**
   * Detect YouTube video metadata from DOM or page context.
   */
  static detectFromPage(pageUrl: string, doc: Document = document): MediaItem | null {
    if (!this.isYouTubePage(pageUrl)) return null;

    const videoId = this.extractVideoId(pageUrl);
    if (!videoId) return null;

    const canonicalUrl = `https://www.youtube.com/watch?v=${videoId}`;
    const thumbnailUrl = `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`;

    // Extract title from multiple DOM selectors and metadata
    let title = '';
    const titleSelectors = [
      'ytd-watch-metadata h1 yt-formatted-string',
      'h1.ytd-watch-metadata yt-formatted-string',
      'ytd-watch-metadata #title yt-formatted-string',
      '#title h1 yt-formatted-string',
      'h1.title yt-formatted-string',
      '#title.ytd-watch-metadata',
      'h1.ytd-watch-metadata',
      'ytd-video-primary-info-renderer h1',
      'h1.watch-title-container',
      'meta[property="og:title"]',
      'meta[name="twitter:title"]'
    ];

    for (const sel of titleSelectors) {
      const el = doc.querySelector(sel);
      if (el) {
        let candidate = '';
        if (el instanceof HTMLMetaElement) {
          candidate = el.getAttribute('content') || '';
        } else if (el.textContent) {
          candidate = el.textContent.trim();
        }
        if (candidate && candidate.toLowerCase() !== 'youtube' && candidate.length > 2) {
          title = candidate;
          break;
        }
      }
    }

    if (!title || title.toLowerCase() === 'youtube') {
      // Clean document.title: strip leading notification count like (606) and trailing " - YouTube"
      title = (doc.title || '')
        .replace(/^\(\d+\)\s*/, '')
        .replace(/\s*[-–—]\s*YouTube$/i, '')
        .trim();
    }

    if (!title || title.toLowerCase() === 'youtube') {
      title = `YouTube Video (${videoId})`;
    }

    // Extract duration & quality from video element
    let duration: number | undefined;
    let quality = '1080p';

    const videoElem = doc.querySelector('video') as HTMLVideoElement | null;
    if (videoElem) {
      if (videoElem.duration && !isNaN(videoElem.duration) && videoElem.duration > 0) {
        duration = videoElem.duration;
      }
      if (videoElem.videoHeight >= 2160) quality = '2160p';
      else if (videoElem.videoHeight >= 1440) quality = '1440p';
      else if (videoElem.videoHeight >= 1080) quality = '1080p';
      else if (videoElem.videoHeight >= 720) quality = '720p';
      else if (videoElem.videoHeight >= 480) quality = '480p';
    }

    return {
      id: generateMediaId(canonicalUrl, canonicalUrl),
      url: canonicalUrl,
      pageUrl: canonicalUrl,
      title,
      thumbnailUrl,
      duration,
      formattedDuration: formatDuration(duration),
      type: 'video',
      format: 'mp4',
      quality: quality as any,
      site: 'youtube.com',
      timestamp: Date.now(),
      sourceStrategy: 'youtube'
    };
  }
}
