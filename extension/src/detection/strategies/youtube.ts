import { MediaItem } from '../../shared/types';
import { extractDomain, formatDuration, generateMediaId } from '../../shared/utils';

export class YouTubeStrategy {
  /**
   * Check if the given page URL is a YouTube video or Shorts page.
   */
  static isYouTubePage(url: string): boolean {
    return (
      url.includes('youtube.com/watch') ||
      url.includes('youtube.com/shorts/') ||
      url.includes('youtu.be/') ||
      url.includes('youtube.com/v/')
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
    const thumbnailUrl = `https://i.ytimg.com/vi/${videoId}/maxresdefault.jpg`;

    // Extract title from DOM elements or meta tags
    let title = '';
    const titleElem = doc.querySelector('h1.ytd-watch-metadata yt-formatted-string, h1.title yt-formatted-string, #title h1');
    if (titleElem && titleElem.textContent) {
      title = titleElem.textContent.trim();
    }

    if (!title) {
      const ogTitle = doc.querySelector('meta[property="og:title"]')?.getAttribute('content');
      if (ogTitle) title = ogTitle.trim();
    }

    if (!title) {
      title = doc.title.replace(/ - YouTube$/, '').trim() || `YouTube Video (${videoId})`;
    }

    // Extract duration from video element if playing
    let duration: number | undefined;
    const videoElem = doc.querySelector('video') as HTMLVideoElement | null;
    if (videoElem && videoElem.duration && !isNaN(videoElem.duration) && videoElem.duration > 0) {
      duration = videoElem.duration;
    }

    return {
      id: generateMediaId(canonicalUrl, pageUrl),
      url: canonicalUrl,
      pageUrl: canonicalUrl,
      title,
      thumbnailUrl,
      duration,
      formattedDuration: formatDuration(duration),
      type: 'video',
      format: 'mp4',
      quality: '1080p',
      site: extractDomain(pageUrl) || 'youtube.com',
      timestamp: Date.now(),
      sourceStrategy: 'youtube'
    };
  }
}
