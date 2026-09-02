import { ExtensionSettings, MediaFormat, MediaType } from './types';

export const PROTOCOL_VERSION = 1;
export const NATIVE_HOST_NAME = 'io.github.sureshsoudararajan.glypdl';

export const DEFAULT_SETTINGS: ExtensionSettings = {
  autoDetect: true,
  detectHtml5Video: true,
  detectHtml5Audio: true,
  detectHls: true,
  detectDash: true,
  showPlayerButton: true,
  showNotifications: true,
  minFileSizeKb: 100, // Filter out anything under 100 KB by default
  preferredQuality: '1080p',
  theme: 'system',
  floatingPanelPosition: 'bottom-right',
  siteRules: {}
};

export const MIME_TYPE_MAP: Record<string, { type: MediaType; format: MediaFormat }> = {
  'video/mp4': { type: 'video', format: 'mp4' },
  'video/webm': { type: 'video', format: 'webm' },
  'video/ogg': { type: 'video', format: 'ogg' },
  'video/quicktime': { type: 'video', format: 'mp4' },
  'video/x-matroska': { type: 'video', format: 'webm' },
  'video/x-flv': { type: 'video', format: 'mp4' },
  'audio/mpeg': { type: 'audio', format: 'mp3' },
  'audio/mp3': { type: 'audio', format: 'mp3' },
  'audio/mp4': { type: 'audio', format: 'm4a' },
  'audio/x-m4a': { type: 'audio', format: 'm4a' },
  'audio/webm': { type: 'audio', format: 'webm' },
  'audio/ogg': { type: 'audio', format: 'ogg' },
  'audio/wav': { type: 'audio', format: 'wav' },
  'audio/x-wav': { type: 'audio', format: 'wav' },
  'audio/flac': { type: 'audio', format: 'flac' },
  'audio/opus': { type: 'audio', format: 'opus' },
  'application/vnd.apple.mpegurl': { type: 'hls', format: 'm3u8' },
  'application/x-mpegurl': { type: 'hls', format: 'm3u8' },
  'application/dash+xml': { type: 'dash', format: 'mpd' }
};

export const EXTENSION_MAP: Record<string, { type: MediaType; format: MediaFormat }> = {
  mp4: { type: 'video', format: 'mp4' },
  webm: { type: 'video', format: 'webm' },
  mkv: { type: 'video', format: 'webm' },
  mov: { type: 'video', format: 'mp4' },
  flv: { type: 'video', format: 'mp4' },
  mp3: { type: 'audio', format: 'mp3' },
  m4a: { type: 'audio', format: 'm4a' },
  aac: { type: 'audio', format: 'm4a' },
  flac: { type: 'audio', format: 'flac' },
  wav: { type: 'audio', format: 'wav' },
  ogg: { type: 'audio', format: 'ogg' },
  opus: { type: 'audio', format: 'opus' },
  m3u8: { type: 'hls', format: 'm3u8' },
  mpd: { type: 'dash', format: 'mpd' }
};

export const IGNORED_URL_PATTERNS = [
  /google-analytics\.com/i,
  /doubleclick\.net/i,
  /googlesyndication\.com/i,
  /adnxs\.com/i,
  /facebook\.com\/tr/i,
  /analytics/i,
  /telemetry/i,
  /beacon/i,
  /pixel/i,
  /favicon/i,
  /\.js(\?.*)?$/i,
  /\.css(\?.*)?$/i,
  /\.json(\?.*)?$/i,
  /\.woff2?(\?.*)?$/i,
  /\.ttf(\?.*)?$/i,
  /\.svg(\?.*)?$/i,
  /\.png(\?.*)?$/i,
  /\.jpg(\?.*)?$/i,
  /\.jpeg(\?.*)?$/i,
  /\.webp(\?.*)?$/i,
  /\.gif(\?.*)?$/i
];
