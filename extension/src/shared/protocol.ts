import { PROTOCOL_VERSION } from './constants';
import { MediaItem, ProtocolMessage } from './types';
import { normalizeUrl } from './utils';

export function createPingMessage(): ProtocolMessage {
  return {
    protocolVersion: PROTOCOL_VERSION,
    action: 'ping'
  };
}

export function createStatusMessage(): ProtocolMessage {
  return {
    protocolVersion: PROTOCOL_VERSION,
    action: 'get_status'
  };
}

export function isSupportedPlatformPage(url?: string): boolean {
  if (!url) return false;
  return (
    url.includes('youtube.com/') ||
    url.includes('youtu.be/') ||
    url.includes('instagram.com/') ||
    url.includes('tiktok.com/') ||
    url.includes('twitter.com/') ||
    url.includes('x.com/') ||
    url.includes('facebook.com/') ||
    url.includes('fb.watch/') ||
    url.includes('reddit.com/')
  );
}

export function isRawMediaChunkUrl(url?: string): boolean {
  if (!url) return false;
  return (
    url.includes('.fbcdn.net/') ||
    url.includes('.cdninstagram.com/') ||
    url.includes('.googlevideo.com/') ||
    url.includes('.tiktokcdn.com/') ||
    url.includes('.byteoversea.com/') ||
    url.includes('.ibytedtos.com/') ||
    url.includes('v.redd.it/')
  );
}

export function createDownloadMessage(
  item: MediaItem,
  autoDownload = false,
  cookiesTxt?: string,
  isTempCookie?: boolean
): ProtocolMessage {
  let targetUrl = item.url;
  if (
    item.sourceStrategy === 'youtube' ||
    item.sourceStrategy === 'instagram' ||
    isSupportedPlatformPage(item.pageUrl) ||
    isRawMediaChunkUrl(item.url)
  ) {
    if (item.pageUrl && item.pageUrl.startsWith('http')) {
      targetUrl = item.pageUrl;
    }
  }
  const msg: ProtocolMessage = {
    protocolVersion: PROTOCOL_VERSION,
    action: 'download',
    url: targetUrl,
    source: {
      url: targetUrl,
      pageUrl: item.pageUrl,
      title: item.title
    },
    media: {
      type: item.type,
      format: item.format,
      quality: item.quality
    },
    autoDownload
  };

  if (cookiesTxt) {
    msg.cookies_txt = cookiesTxt;
    msg.is_temp_cookie = isTempCookie ?? true;
    msg.use_cookies = true;
  }

  return msg;
}

export function createBatchDownloadMessage(items: MediaItem[]): ProtocolMessage {
  return {
    protocolVersion: PROTOCOL_VERSION,
    action: 'download_batch',
    jobs: items.map((it) => createDownloadMessage(it, true))
  };
}

export function validateProtocolMessage(msg: unknown): { valid: boolean; error?: string } {
  if (!msg || typeof msg !== 'object') {
    return { valid: false, error: 'Message must be a non-null object' };
  }

  const p = msg as Partial<ProtocolMessage>;
  if (typeof p.protocolVersion !== 'number' || p.protocolVersion < 1) {
    return { valid: false, error: 'Invalid or missing protocolVersion' };
  }

  if (!p.action || typeof p.action !== 'string') {
    return { valid: false, error: 'Invalid or missing action string' };
  }

  if (p.action === 'download') {
    const url = p.url || (p.source && p.source.url);
    if (!url || typeof url !== 'string' || !url.startsWith('http')) {
      return { valid: false, error: 'Download action requires a valid http/https URL' };
    }
  }

  return { valid: true };
}
