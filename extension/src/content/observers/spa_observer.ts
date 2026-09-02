/**
 * Single Page Application (SPA) navigation observer.
 * Listens for client-side routing, YouTube events, and URL changes.
 */
export class SpaObserver {
  private lastUrl = location.href;
  private onNavigateCallback: (newUrl: string) => void;
  private intervalId: any = null;

  constructor(onNavigate: (newUrl: string) => void) {
    this.onNavigateCallback = onNavigate;
  }

  start(): void {
    // 1. YouTube & custom framework DOM events
    window.addEventListener('yt-navigate-finish', () => this.checkUrlChange());
    window.addEventListener('yt-page-data-updated', () => this.checkUrlChange());
    document.addEventListener('yt-navigate-finish', () => this.checkUrlChange());

    // 2. Standard browser navigation events
    window.addEventListener('popstate', () => this.checkUrlChange());
    window.addEventListener('hashchange', () => this.checkUrlChange());

    // 3. Fallback poll (every 400ms) to ensure instant detection when URL changes in SPAs
    this.intervalId = setInterval(() => this.checkUrlChange(), 400);

    // 4. Observe title changes
    const titleEl = document.querySelector('title');
    if (titleEl) {
      const titleObs = new MutationObserver(() => this.checkUrlChange());
      titleObs.observe(titleEl, { childList: true, characterData: true });
    }
  }

  stop(): void {
    if (this.intervalId) {
      clearInterval(this.intervalId);
      this.intervalId = null;
    }
  }

  private checkUrlChange(): void {
    if (location.href !== this.lastUrl) {
      this.lastUrl = location.href;
      this.onNavigateCallback(this.lastUrl);
    }
  }
}
