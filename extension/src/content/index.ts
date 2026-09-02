import { MediaDetector } from '../detection';
import { StorageService } from '../shared/storage';
import { ExtensionSettings, MediaItem } from '../shared/types';
import { extractDomain } from '../shared/utils';
import { DomMediaObserver } from './detector/dom_detector';
import { SpaObserver } from './observers/spa_observer';
import { PlayerOverlayButton } from './ui/player_button';
import './ui/styles.css';

// @ts-expect-error browser runtime
const browserApi = typeof browser !== 'undefined' ? browser : typeof chrome !== 'undefined' ? chrome : null;

class ContentController {
  private detector = new MediaDetector();
  private settings: ExtensionSettings | null = null;
  private domObserver: DomMediaObserver | null = null;
  private spaObserver: SpaObserver | null = null;
  private playerButton: PlayerOverlayButton | null = null;

  async init(): Promise<void> {
    this.settings = await StorageService.getSettings();
    if (!this.settings.autoDetect) return;

    // Check site rule
    const domain = extractDomain(location.href);
    const siteRule = await StorageService.getSiteRule(domain);
    if (siteRule === 'never') return;

    this.playerButton = new PlayerOverlayButton((item) => this.sendDownload(item));

    // Initial DOM scans with staggered retries for late-loading players (e.g. YouTube, Pexels)
    this.runScan();
    setTimeout(() => this.runScan(), 600);
    setTimeout(() => this.runScan(), 1800);
    setTimeout(() => this.runScan(), 3500);

    // DOM Observer for dynamically inserted media nodes
    this.domObserver = new DomMediaObserver(this.settings, (item) => {
      this.handleNewMedia(item);
    });
    this.domObserver.start();

    // SPA Observer for dynamic route changes
    this.spaObserver = new SpaObserver(() => {
      this.detector.clear();
      this.runScan();
      setTimeout(() => this.runScan(), 800);
    });
    this.spaObserver.start();

    // Listen for scan requests from popup or background
    browserApi?.runtime?.onMessage?.addListener((message: any, _sender: any, sendResponse: any) => {
      if (message?.action === 'scan_now') {
        this.runScan();
        sendResponse({ items: this.detector.getItems() });
        return true;
      }
      return false;
    });
  }

  private runScan(): void {
    if (!this.settings) return;
    const items = this.detector.scanDocument(location.href, this.settings, document);
    this.syncWithBackground(items);

    if (items.length > 0) {
      const topItem = items[0];
      this.attachPlayerUi(topItem);
    }
  }

  private handleNewMedia(item: MediaItem): void {
    if (!this.settings) return;
    const isNew = this.detector.addItem(item, this.settings);
    const allItems = this.detector.getItems();
    this.syncWithBackground(allItems);

    if (isNew || allItems.length > 0) {
      this.attachPlayerUi(item);
    }
  }

  private attachPlayerUi(item: MediaItem): void {
    if (!this.settings) return;

    // Attach overlay button to video players
    if (this.settings.showPlayerButton) {
      const videoElem = document.querySelector('video') as HTMLVideoElement | null;
      if (videoElem && this.playerButton) {
        this.playerButton.attachToElement(videoElem, item);
      }
    }
  }

  private syncWithBackground(items: MediaItem[]): void {
    if (!browserApi?.runtime?.sendMessage) return;
    try {
      browserApi.runtime.sendMessage({
        action: 'media_detected',
        items
      });
    } catch {
      // Ignored
    }
  }

  private sendDownload(item: MediaItem): void {
    if (!browserApi?.runtime?.sendMessage) return;
    try {
      browserApi.runtime.sendMessage({
        action: 'download_item',
        item
      });
    } catch {
      // Ignored
    }
  }
}

const controller = new ContentController();
controller.init();
