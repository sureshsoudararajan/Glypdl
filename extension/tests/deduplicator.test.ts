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
});
