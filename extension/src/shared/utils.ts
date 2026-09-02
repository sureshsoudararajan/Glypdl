import { EXTENSION_MAP, MIME_TYPE_MAP } from './constants';
import { MediaFormat, MediaType } from './types';

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
