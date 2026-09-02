/**
 * Desktop notification helper for the Glypdl extension.
 */
export class NotificationService {
  static showDownloadQueued(title: string): void {
    // @ts-expect-error browser notifications
    const browserApi = typeof browser !== 'undefined' ? browser : typeof chrome !== 'undefined' ? chrome : null;
    if (!browserApi || !browserApi.notifications) return;

    try {
      browserApi.notifications.create({
        type: 'basic',
        iconUrl: 'icons/icon-48.png',
        title: 'Glypdl',
        message: `Download added: ${title}`
      });
    } catch {
      // Ignored if notifications not allowed
    }
  }

  static showError(message: string): void {
    // @ts-expect-error browser notifications
    const browserApi = typeof browser !== 'undefined' ? browser : typeof chrome !== 'undefined' ? chrome : null;
    if (!browserApi || !browserApi.notifications) return;

    try {
      browserApi.notifications.create({
        type: 'basic',
        iconUrl: 'icons/icon-48.png',
        title: 'Glypdl Download Error',
        message
      });
    } catch {
      // Ignored
    }
  }
}
