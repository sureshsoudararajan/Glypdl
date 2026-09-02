/**
 * Single Page Application (SPA) navigation observer.
 * Listens for client-side routing and URL changes without continuous polling.
 */
export class SpaObserver {
  private lastUrl = location.href;
  private onNavigateCallback: (newUrl: string) => void;

  constructor(onNavigate: (newUrl: string) => void) {
    this.onNavigateCallback = onNavigate;
  }

  start(): void {
    // Intercept pushState and replaceState
    const originalPush = history.pushState;
    const originalReplace = history.replaceState;
    const self = this;

    history.pushState = function (...args) {
      originalPush.apply(this, args);
      self.checkUrlChange();
    };

    history.replaceState = function (...args) {
      originalReplace.apply(this, args);
      self.checkUrlChange();
    };

    window.addEventListener('popstate', () => this.checkUrlChange());
    window.addEventListener('hashchange', () => this.checkUrlChange());
  }

  private checkUrlChange(): void {
    if (location.href !== this.lastUrl) {
      this.lastUrl = location.href;
      this.onNavigateCallback(this.lastUrl);
    }
  }
}
