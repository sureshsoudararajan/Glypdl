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

  it('formats httpOnly and subdomain cookies correctly with #HttpOnly_ prefix', () => {
    const cookies = [
      {
        domain: 'instagram.com',
        name: 'sessionid',
        value: 'secret123',
        path: '/',
        secure: true,
        httpOnly: true,
        hostOnly: false,
        expirationDate: 1800000000
      },
      {
        domain: '.instagram.com',
        name: 'ds_user_id',
        value: 'user999',
        path: '/',
        secure: true,
        httpOnly: false,
        hostOnly: false,
        expirationDate: 1800000000
      }
    ];

    const output = formatNetscapeCookies(cookies);
    expect(output).toContain('#HttpOnly_.instagram.com\tTRUE\t/\tTRUE\t1800000000\tsessionid\tsecret123');
    expect(output).toContain('.instagram.com\tTRUE\t/\tTRUE\t1800000000\tds_user_id\tuser999');
  });
});

describe('extractTargetCookies', () => {
  it('extracts and filters cookies matching target domain even with partitionKey or storeId', async () => {
    const { extractTargetCookies } = await import('../src/shared/cookies');
    const mockCookies = [
      {
        domain: '.instagram.com',
        name: 'sessionid',
        value: 'abc_session',
        path: '/',
        secure: true,
        httpOnly: true
      },
      {
        domain: 'instagram.com',
        name: 'ds_user_id',
        value: '12345',
        path: '/',
        secure: true
      },
      {
        domain: '.facebook.com',
        name: 'c_user',
        value: 'ignore_fb',
        path: '/'
      }
    ];

    (globalThis as any).browser = {
      cookies: {
        getAll: async (query: any) => {
          // Verify query structure supports partitionKey
          return mockCookies;
        }
      }
    };

    const netscape = await extractTargetCookies('https://www.instagram.com/stories/testuser/12345/', 'firefox-default');
    expect(netscape).toContain('sessionid');
    expect(netscape).toContain('ds_user_id');
    expect(netscape).not.toContain('ignore_fb');
    delete (globalThis as any).browser;
  });
});

