import { describe, expect, it } from 'vitest';
import {
  createBatchDownloadMessage,
  createDownloadMessage,
  createPingMessage,
  createStatusMessage,
  validateProtocolMessage
} from '../src/shared/protocol';
import { MediaItem } from '../src/shared/types';

describe('Protocol Message Handling', () => {
  const sampleItem: MediaItem = {
    id: 'test-1',
    url: 'https://example.com/video.mp4',
    pageUrl: 'https://example.com/watch',
    title: 'Test Video',
    type: 'video',
    format: 'mp4',
    quality: '1080p',
    site: 'example.com',
    timestamp: Date.now(),
    sourceStrategy: 'html5'
  };

  it('creates valid ping message', () => {
    const msg = createPingMessage();
    expect(msg.protocolVersion).toBe(1);
    expect(msg.action).toBe('ping');
    expect(validateProtocolMessage(msg).valid).toBe(true);
  });

  it('creates valid status message', () => {
    const msg = createStatusMessage();
    expect(msg.action).toBe('get_status');
    expect(validateProtocolMessage(msg).valid).toBe(true);
  });

  it('creates valid download message', () => {
    const msg = createDownloadMessage(sampleItem);
    expect(msg.action).toBe('download');
    expect(msg.url).toBe(sampleItem.url);
    expect(msg.source?.title).toBe(sampleItem.title);
    expect(validateProtocolMessage(msg).valid).toBe(true);
  });

  it('sends canonical webpage URL for YouTube items', () => {
    const ytItem: MediaItem = {
      ...sampleItem,
      pageUrl: 'https://www.youtube.com/watch?v=12345',
      url: 'https://rr1---sn.googlevideo.com/videoplayback?...',
      sourceStrategy: 'youtube'
    };
    const msg = createDownloadMessage(ytItem);
    expect(msg.url).toBe('https://www.youtube.com/watch?v=12345');
  });

  it('creates valid batch download message', () => {
    const msg = createBatchDownloadMessage([sampleItem, sampleItem]);
    expect(msg.action).toBe('download_batch');
    expect(msg.jobs?.length).toBe(2);
    expect(validateProtocolMessage(msg).valid).toBe(true);
  });

  it('rejects invalid message structure', () => {
    expect(validateProtocolMessage(null).valid).toBe(false);
    expect(validateProtocolMessage({ action: 'ping' }).valid).toBe(false); // missing protocolVersion
    expect(validateProtocolMessage({ protocolVersion: 1, action: 'download', url: 'not-a-url' }).valid).toBe(false);
  });
});
