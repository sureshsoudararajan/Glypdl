import { MediaItem } from '../../shared/types';

export class PlayerOverlayButton {
  private button: HTMLElement | null = null;
  private currentItem: MediaItem | null = null;
  private onDownloadClick: (item: MediaItem) => void;

  constructor(onDownload: (item: MediaItem) => void) {
    this.onDownloadClick = onDownload;
  }

  attachToElement(videoElement: HTMLVideoElement, item: MediaItem): void {
    this.currentItem = item;
    if (this.button && document.contains(this.button)) {
      return;
    }

    const container = videoElement.parentElement;
    if (!container) return;

    // Ensure parent container has relative positioning
    const computed = window.getComputedStyle(container);
    if (computed.position === 'static') {
      container.style.position = 'relative';
    }

    this.button = document.createElement('button');
    this.button.className = 'glypdl-player-btn';
    this.button.innerHTML = `
      <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
        <path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/>
      </svg>
      <span>Download</span>
    `;

    this.button.addEventListener('click', (e) => {
      e.stopPropagation();
      e.preventDefault();
      if (this.currentItem) {
        this.onDownloadClick(this.currentItem);
        if (this.button) {
          this.button.innerHTML = `<span>✓ Sent to Glypdl</span>`;
          setTimeout(() => {
            if (this.button) {
              this.button.innerHTML = `
                <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/>
                </svg>
                <span>Download</span>
              `;
            }
          }, 2500);
        }
      }
    });

    container.appendChild(this.button);
  }

  remove(): void {
    if (this.button && this.button.parentElement) {
      this.button.parentElement.removeChild(this.button);
      this.button = null;
    }
  }
}
