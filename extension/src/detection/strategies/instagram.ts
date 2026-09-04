import { MediaItem } from '../../shared/types';
import { formatDuration, generateMediaId } from '../../shared/utils';

export class InstagramStrategy {
  /**
   * Check if the given page URL is an Instagram Story, Reel, Post, or TV page.
   */
  static isInstagramPage(url: string): boolean {
    if (!url) return false;
    return (
      url.includes('instagram.com/stories/') ||
      url.includes('instagram.com/reel/') ||
      url.includes('instagram.com/reels/') ||
      url.includes('instagram.com/p/') ||
      url.includes('instagram.com/tv/')
    );
  }

  /**
   * Extract clean media info from Instagram page.
   */
  static detectFromPage(pageUrl: string, doc: Document = document): MediaItem | null {
    if (!this.isInstagramPage(pageUrl)) return null;

    let title = '';

    if (pageUrl.includes('/stories/')) {
      const match = pageUrl.match(/\/stories\/([^/?#]+)/);
      const username = match ? match[1] : '';
      title = username ? `Instagram Story - ${username}` : 'Instagram Story';
    } else if (pageUrl.includes('/reel/') || pageUrl.includes('/reels/')) {
      title = (doc.title || '').replace(/• Instagram.*$/i, '').trim() || 'Instagram Reel';
    } else if (pageUrl.includes('/p/')) {
      title = (doc.title || '').replace(/• Instagram.*$/i, '').trim() || 'Instagram Post';
    } else {
      title = (doc.title || '').replace(/• Instagram.*$/i, '').trim() || 'Instagram Media';
    }

    // Try to find thumbnail from video poster or meta tags
    let thumbnailUrl = '';
    const videoElem = doc.querySelector('video') as HTMLVideoElement | null;
    if (videoElem?.poster && !videoElem.poster.startsWith('data:')) {
      thumbnailUrl = videoElem.poster;
    }

    if (!thumbnailUrl) {
      const ogImg = doc.querySelector('meta[property="og:image"]') as HTMLMetaElement | null;
      if (ogImg?.content) {
        thumbnailUrl = ogImg.content;
      }
    }

    let duration: number | undefined;
    if (videoElem && videoElem.duration && !isNaN(videoElem.duration) && videoElem.duration > 0) {
      duration = videoElem.duration;
    }

    // Use the canonical Instagram webpage URL so yt-dlp triggers its native extractor
    const canonicalUrl = pageUrl;

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
      quality: '1080p',
      site: 'instagram.com',
      timestamp: Date.now(),
      sourceStrategy: 'instagram'
    };
  }
}
