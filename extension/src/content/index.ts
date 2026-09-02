import { MediaDetector } from '../detection';
import { StorageService } from '../shared/storage';
import { ExtensionSettings, MediaItem } from '../shared/types';
import { extractDomain } from '../shared/utils';
import { DomMediaObserver } from './detector/dom_detector';
import { SpaObserver } from './observers/spa_observer';
import { FloatingMediaPanel } from './ui/floating_panel';
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
  private floatingPanel: FloatingMediaPanel | null = null;
  private isDismissed = false;

  async init(): Promise<void> {
    this.settings = await StorageService.getSettings();
    if (!this.settings.autoDetect) return;

    // Check site rule
    const domain = extractDomain(location.href);
    const siteRule = await StorageService.getSiteRule(domain);
    if (siteRule === 'never') return;

    this.playerButton = new PlayerOverlayButton((item) => this.sendDownload(item));
    this.floatingPanel = new FloatingMediaPanel(
      this.settings,
      (item) => this.sendDownload(item),
      () => {
        this.isDismissed = true;
      }
    );

    // Initial DOM scan
    this.runScan();

    // DOM Observer for newly inserted video/audio tags
    this.domObserver = new DomMediaObserver(this.settings, (item) => {
      this.handleNewMedia(item);
    });
    this.domObserver.start();

    // SPA Observer for dynamic route changes
    this.spaObserver = new SpaObserver(() => {
      this.isDismissed = false;
      this.detector.clear();
      this.runScan();
    });
    this.spaObserver.start();
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
    if (isNew) {
      const allItems = this.detector.getItems();
      this.syncWithBackground(allItems);
      this.attachPlayerUi(item);
    }
  }

  private attachPlayerUi(item: MediaItem): void {
    if (!this.settings) return;

    // Attach overlay button to video players
    if (this.settings.showPlayerButton) {
      const videoElem = document.querySelector('video');
      if (videoElem && this.playerButton) {
        this.playerButton.attachToElement(videoElem, item);
      }
    }

    // Show floating detection panel
    if (!this.isDismissed && this.floatingPanel) {
      this.floatingPanel.show(item);
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
