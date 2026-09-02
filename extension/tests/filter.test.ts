import { describe, expect, it } from 'vitest';
import { MediaFilter } from '../src/detection/filter';
import { DEFAULT_SETTINGS } from '../src/shared/constants';

describe('MediaFilter Subsystem', () => {
  it('filters out analytics and tracking URLs', () => {
    expect(MediaFilter.isIgnoredUrl('https://www.google-analytics.com/analytics.js')).toBe(true);
    expect(MediaFilter.isIgnoredUrl('https://stats.example.com/telemetry/pixel.gif')).toBe(true);
    expect(MediaFilter.isIgnoredUrl('https://example.com/static/style.css')).toBe(true);
  });

  it('allows valid media URLs', () => {
    expect(MediaFilter.isIgnoredUrl('https://cdn.example.com/media/clip.mp4')).toBe(false);
    expect(MediaFilter.isIgnoredUrl('https://stream.example.com/live/playlist.m3u8')).toBe(false);
  });

  it('respects minimum file size filter', () => {
    const tinyItem = {
      url: 'https://example.com/tiny.mp3',
      fileSize: 50 * 1024, // 50 KB
      type: 'audio' as const,
      sourceStrategy: 'direct' as const
    };
    const settings = { ...DEFAULT_SETTINGS, minFileSizeKb: 100 }; // 100 KB min
    expect(MediaFilter.isValidMedia(tinyItem, settings)).toBe(false);

    const largeItem = {
      url: 'https://example.com/song.mp3',
      fileSize: 5 * 1024 * 1024, // 5 MB
      type: 'audio' as const,
      sourceStrategy: 'direct' as const
    };
    expect(MediaFilter.isValidMedia(largeItem, settings)).toBe(true);
  });

  it('respects format toggle settings', () => {
    const audioItem = {
      url: 'https://example.com/audio.mp3',
      type: 'audio' as const,
      sourceStrategy: 'direct' as const
    };
    const settingsWithAudioDisabled = { ...DEFAULT_SETTINGS, detectHtml5Audio: false };
    expect(MediaFilter.isValidMedia(audioItem, settingsWithAudioDisabled)).toBe(false);
  });
});
