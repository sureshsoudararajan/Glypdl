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

    // 1. Detect active video element on page
    const videos = Array.from(doc.querySelectorAll<HTMLVideoElement>('video'));
    const activeVideo =
      videos.find((v) => !v.paused && v.currentTime > 0) ||
      videos.find((v) => v.offsetHeight > 200) ||
      videos[0] ||
      null;

    let thumbnailUrl = '';
    if (activeVideo?.poster && !activeVideo.poster.startsWith('data:')) {
      thumbnailUrl = activeVideo.poster;
    }

    // 2. In Instagram Stories, find the active slide image in the DOM (bypasses stale og:image meta)
    if (!thumbnailUrl && pageUrl.includes('/stories/')) {
      if (activeVideo) {
        const container = activeVideo.closest('section') || activeVideo.closest('article') || activeVideo.parentElement?.parentElement;
        if (container) {
          const imgs = Array.from(container.querySelectorAll<HTMLImageElement>('img')).filter((img) => {
            const src = img.currentSrc || img.src || '';
            return (src.includes('fbcdn.net') || src.includes('cdninstagram.com')) && !src.includes('150x150') && !src.includes('s150x150');
          });
          if (imgs.length > 0) {
            thumbnailUrl = imgs[0].currentSrc || imgs[0].src;
          }
        }
      }

      if (!thumbnailUrl) {
        const storyImgs = Array.from(doc.querySelectorAll<HTMLImageElement>('section img, article img, main img')).filter((img) => {
          const src = img.currentSrc || img.src || '';
          return (src.includes('fbcdn.net') || src.includes('cdninstagram.com')) && img.offsetHeight > 200;
        });
        if (storyImgs.length > 0) {
          thumbnailUrl = storyImgs[0].currentSrc || storyImgs[0].src;
        }
      }
    }

    // 3. Fallback to og:image meta tag for non-story posts or initial loads
    if (!thumbnailUrl) {
      const ogImg = doc.querySelector('meta[property="og:image"]') as HTMLMetaElement | null;
      if (ogImg?.content) {
        thumbnailUrl = ogImg.content;
      }
    }

    let duration: number | undefined;
    if (activeVideo && activeVideo.duration && !isNaN(activeVideo.duration) && activeVideo.duration > 0) {
      duration = activeVideo.duration;
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
