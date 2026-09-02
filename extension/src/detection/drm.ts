/**
 * DRM (Digital Rights Management) detector.
 * Identifies Encrypted Media Extensions (EME) and flags protected media.
 * Glypdl does NOT attempt to circumvent or bypass DRM.
 */

export class DrmDetector {
  /**
   * Check if a video/audio HTML element uses Encrypted Media Extensions (EME).
   */
  static isElementDrmProtected(element: HTMLMediaElement): boolean {
    if (!element) return false;
    // Check for mediaKeys property attached to HTMLMediaElement
    // @ts-expect-error mediaKeys is standard in modern HTMLMediaElement
    if (element.mediaKeys && typeof element.mediaKeys === 'object') {
      return true;
    }
    return false;
  }

  /**
   * Check if a URL or MIME type indicates DRM/license exchange.
   */
  static isDrmUrlOrMime(url: string, mime?: string): boolean {
    const cleanUrl = url.toLowerCase();
    if (
      cleanUrl.includes('widevine') ||
      cleanUrl.includes('playready') ||
      cleanUrl.includes('fairplay') ||
      cleanUrl.includes('drm/license') ||
      cleanUrl.includes('license.php')
    ) {
      return true;
    }

    if (mime) {
      const cleanMime = mime.toLowerCase();
      if (cleanMime.includes('drm') || cleanMime.includes('widevine') || cleanMime.includes('playready')) {
        return true;
      }
    }

    return false;
  }
}
