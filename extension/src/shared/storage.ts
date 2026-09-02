import { DEFAULT_SETTINGS } from './constants';
import { ExtensionSettings, SiteRule } from './types';

// Browser storage abstraction supporting Firefox (browser), Chrome (chrome), and test mock
const getBrowserStorage = () => {
  if (typeof globalThis !== 'undefined') {
    // @ts-expect-error browser is global in webextension
    if (typeof browser !== 'undefined' && browser.storage?.local) {
      // @ts-expect-error browser is global in webextension
      return browser.storage.local;
    }
    // @ts-expect-error chrome is global in webextension
    if (typeof chrome !== 'undefined' && chrome.storage?.local) {
      // @ts-expect-error chrome is global in webextension
      return chrome.storage.local;
    }
  }
  // In-memory fallback for unit tests
  const mem = new Map<string, unknown>();
  return {
    get: async (keys: string | string[]) => {
      const res: Record<string, unknown> = {};
      const keyList = Array.isArray(keys) ? keys : [keys];
      keyList.forEach((k) => {
        if (mem.has(k)) res[k] = mem.get(k);
      });
      return res;
    },
    set: async (items: Record<string, unknown>) => {
      Object.entries(items).forEach(([k, v]) => mem.set(k, v));
    }
  };
};

export class StorageService {
  private static storage = getBrowserStorage();

  static async getSettings(): Promise<ExtensionSettings> {
    try {
      const res = await this.storage.get('settings');
      if (res && res.settings) {
        return { ...DEFAULT_SETTINGS, ...(res.settings as ExtensionSettings) };
      }
    } catch {
      // Return defaults on error
    }
    return { ...DEFAULT_SETTINGS };
  }

  static async saveSettings(patch: Partial<ExtensionSettings>): Promise<ExtensionSettings> {
    const current = await this.getSettings();
    const updated = { ...current, ...patch };
    await this.storage.set({ settings: updated });
    return updated;
  }

  static async getSiteRule(domain: string): Promise<SiteRule> {
    const settings = await this.getSettings();
    return settings.siteRules[domain] || 'always';
  }

  static async setSiteRule(domain: string, rule: SiteRule): Promise<void> {
    const settings = await this.getSettings();
    settings.siteRules[domain] = rule;
    await this.saveSettings({ siteRules: settings.siteRules });
  }
}
