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

export function createDownloadMessage(
  item: MediaItem,
  autoDownload = false,
  cookiesTxt?: string,
  isTempCookie?: boolean
): ProtocolMessage {
  const targetUrl = item.sourceStrategy === 'youtube' ? item.pageUrl : item.url;
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
