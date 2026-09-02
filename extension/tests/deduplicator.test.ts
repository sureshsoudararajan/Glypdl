import { describe, expect, it } from 'vitest';
import { MediaDeduplicator } from '../src/detection/deduplicator';
import { MediaItem } from '../src/shared/types';

describe('MediaDeduplicator', () => {
  const item1: MediaItem = {
    id: '1',
    url: 'https://example.com/video.mp4',
    pageUrl: 'https://example.com/watch',
    title: 'Media from example.com',
    type: 'video',
    format: 'mp4',
    quality: '1080p',
    site: 'example.com',
    timestamp: 1000,
    sourceStrategy: 'html5'
  };

  const item1Updated: MediaItem = {
    id: '2',
    url: 'https://example.com/video.mp4?utm_source=test',
    pageUrl: 'https://example.com/watch',
    title: 'Official Music Video',
    thumbnailUrl: 'https://example.com/thumb.jpg',
    duration: 180,
    formattedDuration: '3:00',
    type: 'video',
    format: 'mp4',
    quality: '1080p',
    site: 'example.com',
    timestamp: 1050,
    sourceStrategy: 'html5'
  };

  it('deduplicates identical media streams and merges metadata', () => {
    const dedup = new MediaDeduplicator();
    expect(dedup.add(item1)).toBe(true);
    expect(dedup.getAll().length).toBe(1);

    // Adding updated version should merge richer metadata without increasing count
    expect(dedup.add(item1Updated)).toBe(true);
    const all = dedup.getAll();
    expect(all.length).toBe(1);
    expect(all[0].title).toBe('Official Music Video');
    expect(all[0].thumbnailUrl).toBe('https://example.com/thumb.jpg');
    expect(all[0].duration).toBe(180);
  });

  it('deduplicates YouTube pages by canonical video URL', () => {
    const dedup = new MediaDeduplicator();
    const yt1: MediaItem = {
      id: 'yt-1',
      url: 'https://www.youtube.com/watch?v=abc',
      pageUrl: 'https://www.youtube.com/watch?v=abc',
      title: 'First Load',
      type: 'video',
      format: 'mp4',
      quality: '1080p',
      site: 'youtube.com',
      timestamp: 1000,
      sourceStrategy: 'youtube'
    };
    const yt2: MediaItem = {
      id: 'yt-2',
      url: 'https://www.youtube.com/watch?v=abc&t=10s',
      pageUrl: 'https://www.youtube.com/watch?v=abc',
      title: 'Second Load',
      thumbnailUrl: 'https://i.ytimg.com/vi/abc/maxresdefault.jpg',
      type: 'video',
      format: 'mp4',
      quality: '1080p',
      site: 'youtube.com',
      timestamp: 2000,
      sourceStrategy: 'youtube'
    };

    dedup.add(yt1);
    dedup.add(yt2);
    expect(dedup.getAll().length).toBe(1);
    expect(dedup.getAll()[0].thumbnailUrl).toBe('https://i.ytimg.com/vi/abc/maxresdefault.jpg');
  });

  it('clusters HLS master and variant streams on same page into a single enriched item', () => {
    const dedup = new MediaDeduplicator();

    // 1. DOM detector discovers video element using MSE/blob, falls back to pageUrl with rich title & thumbnail
    const domItem: MediaItem = {
      id: 'dom-1',
      url: 'https://mysite.com/watch/video-99',
      pageUrl: 'https://mysite.com/watch/video-99',
      title: 'Ullhplm13c3',
      thumbnailUrl: 'https://mysite.com/posters/99.jpg',
      duration: 1443,
      formattedDuration: '24:03',
      type: 'video',
      format: 'mp4',
      quality: '1080p',
      site: 'mysite.com',
      timestamp: 1000,
      sourceStrategy: 'html5'
    };

    // 2. Network sniffer detects master playlist
    const masterStream: MediaItem = {
      id: 'net-master',
      url: 'https://hls-cdn.mysite.com/hls/99/master.m3u8',
      pageUrl: 'https://mysite.com/watch/video-99',
      title: 'HLS Stream from hls-cdn.mysite.com',
      type: 'hls',
      format: 'm3u8',
      quality: 'auto',
      site: 'mysite.com',
      timestamp: 1050,
      sourceStrategy: 'hls'
    };

    // 3. Network sniffer detects rendition sub-playlist (chunklist/index)
    const variantStream: MediaItem = {
      id: 'net-variant',
      url: 'https://hls-cdn.mysite.com/hls/99/1080p/index.m3u8',
      pageUrl: 'https://mysite.com/watch/video-99',
      title: 'HLS Stream from hls-cdn.mysite.com',
      type: 'hls',
      format: 'm3u8',
      quality: 'auto',
      site: 'mysite.com',
      timestamp: 1100,
      sourceStrategy: 'hls'
    };

    dedup.add(domItem);
    dedup.add(masterStream);
    dedup.add(variantStream);

    const items = dedup.getAll();
    // Must result in EXACTLY 1 clean item, not 3 duplicates!
    expect(items.length).toBe(1);

    const merged = items[0];
    expect(merged.title).toBe('Ullhplm13c3');
    expect(merged.thumbnailUrl).toBe('https://mysite.com/posters/99.jpg');
    expect(merged.duration).toBe(1443);
    expect(merged.quality).toBe('1080p');
    expect(merged.url).toBe('https://hls-cdn.mysite.com/hls/99/master.m3u8');
    expect(merged.format).toBe('m3u8');
  });
});
