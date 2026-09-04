/**
 * Core type definitions for the Glypdl Firefox WebExtension.
 */

export type MediaType = 'video' | 'audio' | 'stream' | 'hls' | 'dash';

export type MediaFormat =
  | 'mp4'
  | 'webm'
  | 'mp3'
  | 'm4a'
  | 'flac'
  | 'wav'
  | 'ogg'
  | 'opus'
  | 'm3u8'
  | 'mpd'
  | 'unknown';

export type MediaQuality =
  | '4320p'
  | '2160p'
  | '1440p'
  | '1080p'
  | '720p'
  | '480p'
  | '360p'
  | '240p'
  | '144p'
  | 'audio'
  | 'auto';

export type DetectionStrategyType = 'youtube' | 'instagram' | 'html5' | 'hls' | 'dash' | 'direct' | 'context';

export interface MediaItem {
  id: string;
  url: string;
  pageUrl: string;
  title: string;
  thumbnailUrl?: string;
  duration?: number;
  formattedDuration?: string;
  type: MediaType;
  format: MediaFormat;
  quality: MediaQuality;
  fileSize?: number;
  formattedSize?: string;
  mimeType?: string;
  isProtected?: boolean; // DRM protected media
  site: string;
  timestamp: number;
  sourceStrategy: DetectionStrategyType;
}

export interface DownloadJob {
  id: string;
  url: string;
  pageUrl: string;
  title: string;
  thumbnailUrl?: string;
  type: MediaType;
  format?: string;
  quality?: string;
  autoDownload?: boolean;
  timestamp: number;
  cookiesTxt?: string;
  isTempCookie?: boolean;
}

export interface ConnectionStatus {
  connected: boolean;
  hostVersion?: string;
  glypdlRunning: boolean;
  glypdlVersion?: string;
  error?: string;
  isFlatpak?: boolean;
  lastChecked: number;
}

export type SiteRule = 'always' | 'ask' | 'never';

export interface ExtensionSettings {
  autoDetect: boolean;
  detectHtml5Video: boolean;
  detectHtml5Audio: boolean;
  detectHls: boolean;
  detectDash: boolean;
  showPlayerButton: boolean;
  showNotifications: boolean;
  minFileSizeKb: number;
  preferredQuality: string;
  theme: 'system' | 'light' | 'dark';
  floatingPanelPosition: 'bottom-right' | 'top-right' | 'bottom-left' | 'top-left';
  siteRules: Record<string, SiteRule>;
}

export interface ProtocolMessage {
  protocolVersion: number;
  action: 'ping' | 'get_status' | 'download' | 'download_batch' | 'cancel' | 'pause' | 'resume';
  url?: string;
  source?: {
    url: string;
    pageUrl: string;
    title: string;
  };
  media?: {
    type: string;
    format: string;
    quality: string;
  };
  cookies_txt?: string;
  is_temp_cookie?: boolean;
  use_cookies?: boolean;
  jobs?: ProtocolMessage[];
  [key: string]: unknown;
}

export interface ProtocolResponse {
  protocolVersion: number;
  success: boolean;
  action?: string;
  connected?: boolean;
  host?: string;
  hostVersion?: string;
  glypdlRunning?: boolean;
  glypdlVersion?: string;
  message?: string;
  error?: string;
  details?: unknown;
}
