import { describe, expect, it } from 'vitest';
import { MediaDetector } from '../src/detection';
import { DirectMediaStrategy } from '../src/detection/strategies/direct_media';
import { HlsDashStrategy } from '../src/detection/strategies/hls_dash';
import { Html5Strategy } from '../src/detection/strategies/html5';
import { YouTubeStrategy } from '../src/detection/strategies/youtube';
import { DEFAULT_SETTINGS } from '../src/shared/constants';

describe('YouTube Detection Strategy', () => {
  it('identifies standard YouTube watch URLs', () => {
    const url = 'https://www.youtube.com/watch?v=dQw4w9WgXcQ';
    expect(YouTubeStrategy.isYouTubePage(url)).toBe(true);
    expect(YouTubeStrategy.extractVideoId(url)).toBe('dQw4w9WgXcQ');
  });

  it('identifies YouTube Shorts URLs', () => {
    const url = 'https://www.youtube.com/shorts/3YxaaGgTQYM';
    expect(YouTubeStrategy.isYouTubePage(url)).toBe(true);
    expect(YouTubeStrategy.extractVideoId(url)).toBe('3YxaaGgTQYM');
  });

  it('identifies youtu.be shortlinks', () => {
    const url = 'https://youtu.be/dQw4w9WgXcQ?si=abcdef';
    expect(YouTubeStrategy.isYouTubePage(url)).toBe(true);
    expect(YouTubeStrategy.extractVideoId(url)).toBe('dQw4w9WgXcQ');
  });

  it('rejects non-YouTube URLs', () => {
    expect(YouTubeStrategy.isYouTubePage('https://vimeo.com/123456')).toBe(false);
  });
});

describe('HLS & DASH Strategy', () => {
  it('detects HLS stream URLs (.m3u8)', () => {
    const url = 'https://stream.example.com/live/master.m3u8';
    expect(HlsDashStrategy.isHlsOrDash(url)).toBe('hls');
    const item = HlsDashStrategy.createStreamItem(url, 'hls', 'https://example.com');
    expect(item.type).toBe('hls');
    expect(item.format).toBe('m3u8');
  });

  it('detects DASH manifest URLs (.mpd)', () => {
    const url = 'https://stream.example.com/manifest.mpd';
    expect(HlsDashStrategy.isHlsOrDash(url)).toBe('dash');
    const item = HlsDashStrategy.createStreamItem(url, 'dash', 'https://example.com');
    expect(item.type).toBe('dash');
    expect(item.format).toBe('mpd');
  });
});

describe('Direct Media Strategy', () => {
  it('detects MP4 video URLs', () => {
    const item = DirectMediaStrategy.createDirectItem('https://example.com/video.mp4', 'https://example.com');
    expect(item).not.toBeNull();
    expect(item?.type).toBe('video');
    expect(item?.format).toBe('mp4');
  });

  it('detects MP3 audio URLs', () => {
    const item = DirectMediaStrategy.createDirectItem('https://example.com/audio.mp3', 'https://example.com');
    expect(item).not.toBeNull();
    expect(item?.type).toBe('audio');
    expect(item?.format).toBe('mp3');
  });
});
