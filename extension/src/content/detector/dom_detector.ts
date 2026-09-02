import { Html5Strategy } from '../../detection/strategies/html5';
import { ExtensionSettings, MediaItem } from '../../shared/types';

export class DomMediaObserver {
  private observer: MutationObserver | null = null;
  private onMediaDetected: (item: MediaItem) => void;
  private settings: ExtensionSettings;
  private boundElements = new WeakSet<HTMLMediaElement>();

  constructor(settings: ExtensionSettings, onMediaDetected: (item: MediaItem) => void) {
    this.settings = settings;
    this.onMediaDetected = onMediaDetected;
  }

  start(): void {
    // 1. Initial scan of existing media elements
    this.scanAndBindMedia(document);

    // 2. Targeted MutationObserver for dynamically inserted media nodes
    this.observer = new MutationObserver((mutations) => {
      for (const mutation of mutations) {
        if (mutation.type === 'childList') {
          for (const node of mutation.addedNodes) {
            if (node instanceof HTMLMediaElement) {
              this.bindElement(node);
            } else if (node instanceof HTMLElement) {
              const children = node.querySelectorAll<HTMLMediaElement>('video, audio');
              children.forEach((el) => this.bindElement(el));
            }
          }
        }
      }
    });

    this.observer.observe(document.body || document.documentElement, {
      childList: true,
      subtree: true
    });
  }

  stop(): void {
    if (this.observer) {
      this.observer.disconnect();
      this.observer = null;
    }
  }

  private scanAndBindMedia(root: ParentNode): void {
    const elements = root.querySelectorAll<HTMLMediaElement>('video, audio');
    elements.forEach((el) => this.bindElement(el));
  }

  private bindElement(element: HTMLMediaElement): void {
    if (this.boundElements.has(element)) return;
    this.boundElements.add(element);

    const handleEvent = () => {
      const item = Html5Strategy.detectFromElement(element, location.href, document);
      if (item) {
        this.onMediaDetected(item);
      }
    };

    element.addEventListener('loadedmetadata', handleEvent, { passive: true });
    element.addEventListener('canplay', handleEvent, { passive: true });
    element.addEventListener('play', handleEvent, { passive: true });

    // Initial check if already loaded
    if (element.readyState >= 1 || element.currentSrc || element.src) {
      handleEvent();
    }
  }
}
