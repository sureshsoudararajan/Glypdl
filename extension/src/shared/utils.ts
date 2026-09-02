import { EXTENSION_MAP, MIME_TYPE_MAP } from './constants';
import { MediaFormat, MediaQuality, MediaType } from './types';

export function formatSize(bytes?: number): string {
  if (bytes === undefined || bytes === null || isNaN(bytes) || bytes <= 0) {
    return '';
  }
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let size = bytes;
  let unitIndex = 0;
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex++;
  }
  return `${size.toFixed(size >= 10 || unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
}

export function formatDuration(seconds?: number): string {
  if (seconds === undefined || seconds === null || isNaN(seconds) || seconds <= 0) {
    return '';
  }
  const total = Math.floor(seconds);
  const hrs = Math.floor(total / 3600);
  const mins = Math.floor((total % 3600) / 60);
  const secs = total % 60;

  if (hrs > 0) {
    return `${hrs}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  }
  return `${mins}:${secs.toString().padStart(2, '0')}`;
}

export function normalizeUrl(rawUrl: string): string {
  try {
    const parsed = new URL(rawUrl);
    // Strip tracking parameters
    const paramsToDrop = ['utm_source', 'utm_medium', 'utm_campaign', 'fbclid', 'gclid'];
    paramsToDrop.forEach((p) => parsed.searchParams.delete(p));
    return parsed.toString();
  } catch {
    return rawUrl.trim();
  }
}

export function extractDomain(url: string): string {
  try {
    const parsed = new URL(url);
    return parsed.hostname.replace(/^www\./, '');
  } catch {
    return '';
  }
}

export function generateMediaId(url: string, pageUrl: string): string {
  const base = `${normalizeUrl(url)}|${normalizeUrl(pageUrl)}`;
  let hash = 0;
  for (let i = 0; i < base.length; i++) {
    hash = (hash << 5) - hash + base.charCodeAt(i);
    hash |= 0; // Convert to 32bit integer
  }
  return `media-${Math.abs(hash).toString(36)}`;
}

export function inferFormatFromUrl(url: string): { type: MediaType; format: MediaFormat } | null {
  try {
    const parsed = new URL(url);
    const pathname = parsed.pathname.toLowerCase();
    const parts = pathname.split('.');
    if (parts.length > 1) {
      const ext = parts.pop()!;
      if (ext in EXTENSION_MAP) {
        return EXTENSION_MAP[ext];
      }
    }
  } catch {
    // Fallback simple regex
    const match = url.toLowerCase().match(/\.([a-z0-9]{3,4})(?:[?#]|$)/);
    if (match && match[1] in EXTENSION_MAP) {
      return EXTENSION_MAP[match[1]];
    }
  }
  return null;
}

export function inferFormatFromMime(mime: string): { type: MediaType; format: MediaFormat } | null {
  const cleanMime = mime.toLowerCase().split(';')[0].trim();
  return MIME_TYPE_MAP[cleanMime] || null;
}

/**
 * Infer resolution/quality string from URL patterns, filename, or element dimensions.
 */
export function inferMediaQuality(url: string, element?: HTMLMediaElement | null): MediaQuality {
  // 1. Check element videoHeight/videoWidth if loaded
  if (typeof HTMLVideoElement !== 'undefined' && element instanceof HTMLVideoElement) {
    const h = element.videoHeight || 0;
    if (h >= 2160) return '2160p';
    if (h >= 1440) return '1440p';
    if (h >= 1080) return '1080p';
    if (h >= 720) return '720p';
    if (h >= 480) return '480p';
    if (h >= 360) return '360p';
    if (h > 0) return '240p';
  }

  // 2. Parse resolution tokens in URL (e.g. 15951468_2560_1440_30fps.mp4, 1920_1080, 1080p, 4k, uhd)
  const lowerUrl = url.toLowerCase();

  if (/(?:_|\b)3840[x_]2160|\b2160p?\b|\b4k\b|\buhd\b/.test(lowerUrl)) {
    return '2160p';
  }
  if (/(?:_|\b)2560[x_]1440|\b1440p?\b|\b2k\b|\bqhd\b/.test(lowerUrl)) {
    return '1440p';
  }
  if (/(?:_|\b)1920[x_]1080|\b1080p?\b|\bfhd\b/.test(lowerUrl)) {
    return '1080p';
  }
  if (/(?:_|\b)1280[x_]720|\b720p?\b|\bhd\b/.test(lowerUrl)) {
    return '720p';
  }
  if (/(?:_|\b)(?:960[x_]540|854[x_]480|848[x_]480|640[x_]480)|\b480p?\b|\bsd\b/.test(lowerUrl)) {
    return '480p';
  }
  if (/(?:_|\b)640[x_]360|\b360p?\b/.test(lowerUrl)) {
    return '360p';
  }
  if (/(?:_|\b)426[x_]240|\b240p?\b/.test(lowerUrl)) {
    return '240p';
  }

  // 3. Check width/height HTML attributes on element
  if (element) {
    const attrHeight = parseInt(element.getAttribute('height') || '0', 10);
    if (attrHeight >= 2160) return '2160p';
    if (attrHeight >= 1440) return '1440p';
    if (attrHeight >= 1080) return '1080p';
    if (attrHeight >= 720) return '720p';
    if (attrHeight >= 480) return '480p';
    if (attrHeight >= 360) return '360p';
  }

  return '1080p';
}

/**
 * Clean up a raw filename into a human-readable title.
 */
export function cleanFilenameToTitle(filename: string): string {
  if (!filename) return '';
  // Strip extension
  let name = filename.replace(/\.[a-z0-9]{2,5}$/i, '');
  // Strip video resolution and fps tokens (e.g. 15951468_2560_1440_30fps -> 15951468)
  name = name.replace(/_\d{3,4}[x_]\d{3,4}(?:_\d+fps)?/gi, '');
  name = name.replace(/[-_](?:1080p|720p|480p|360p|2160p|1440p|4k|2k|hd|sd|fps\d+)/gi, '');
  // Replace underscores and hyphens with spaces
  name = name.replace(/[-_+]/g, ' ').replace(/\s+/g, ' ').trim();
  // Capitalize words
  if (name.length > 0) {
    name = name.charAt(0).toUpperCase() + name.slice(1);
  }
  return name;
}
