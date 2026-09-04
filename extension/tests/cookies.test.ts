import { describe, expect, it } from 'vitest';
import { formatNetscapeCookies } from '../src/shared/cookies';

describe('formatNetscapeCookies', () => {
  it('formats basic cookies into valid Netscape format', () => {
    const cookies = [
      {
        domain: '.youtube.com',
        name: 'SID',
        value: 'test_sid_value',
        path: '/',
        secure: true,
        expirationDate: 1770000000
      },
      {
        domain: 'example.com',
        name: 'session_id',
        value: 'sess_123',
        path: '/watch',
        secure: false,
        expirationDate: 0
      }
    ];

    const output = formatNetscapeCookies(cookies);

    expect(output).toContain('# Netscape HTTP Cookie File');
    // Leading dot means include subdomains = TRUE
    expect(output).toContain('.youtube.com\tTRUE\t/\tTRUE\t1770000000\tSID\ttest_sid_value');
    // Non-leading dot means include subdomains = FALSE, session cookie expires = 0
    expect(output).toContain('example.com\tFALSE\t/watch\tFALSE\t0\tsession_id\tsess_123');
  });

  it('handles cookies with missing optional fields gracefully', () => {
    const cookies = [
      {
        domain: 'mysite.org',
        name: 'token',
        value: 'abc'
      }
    ];

    const output = formatNetscapeCookies(cookies);
    expect(output).toContain('mysite.org\tFALSE\t/\tFALSE\t0\ttoken\tabc');
  });

  it('skips invalid cookies without names', () => {
    const cookies = [
      {
        domain: 'test.com',
        name: '',
        value: 'val'
      }
    ];

    const output = formatNetscapeCookies(cookies);
    const lines = output.trim().split('\n');
    // Only comments and blank lines
    expect(lines.every((l) => l.startsWith('#') || l.trim() === '')).toBe(true);
  });
});
