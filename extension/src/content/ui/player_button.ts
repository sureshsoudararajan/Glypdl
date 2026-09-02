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

    // Determine the best player container
    let container: HTMLElement | null = null;

    // 1. YouTube specific player container
    if (location.hostname.includes('youtube.com')) {
      container = document.querySelector('#movie_player, .html5-video-player, ytd-player') as HTMLElement | null;
    }

    // 2. Generic player container lookup
    if (!container) {
      container = videoElement.closest(
        '[class*="player"], [id*="player"], [class*="video-container"], .media-container, [data-testid*="video"]'
      ) as HTMLElement | null;
    }

    // 3. Fallback to parent element
    if (!container) {
      container = videoElement.parentElement;
    }

    if (!container) return;

    // Ensure container has relative/absolute positioning so absolute button aligns properly
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
