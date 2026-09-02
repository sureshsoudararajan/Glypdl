import { describe, expect, it } from 'vitest';
import { DrmDetector } from '../src/detection/drm';
import { MediaFilter } from '../src/detection/filter';
import { validateProtocolMessage } from '../src/shared/protocol';

describe('Security & Privacy Safeguards', () => {
  it('rejects javascript: and pseudo URLs', () => {
    expect(MediaFilter.isIgnoredUrl('javascript:alert(1)')).toBe(true);
    expect(MediaFilter.isIgnoredUrl('data:text/html,<script>evil()</script>')).toBe(true);
  });

  it('detects DRM streams and flags them', () => {
    expect(DrmDetector.isDrmUrlOrMime('https://license.widevine.com/cenc/getlicense')).toBe(true);
    expect(DrmDetector.isDrmUrlOrMime('https://example.com/stream.mpd', 'application/dash+xml;widevine')).toBe(true);
  });

  it('validates protocol messages against malicious payloads', () => {
    const maliciousMsg = {
      protocolVersion: 1,
      action: 'download',
      url: 'javascript:document.location="http://attacker.com"'
    };
    expect(validateProtocolMessage(maliciousMsg).valid).toBe(false);
  });
});
