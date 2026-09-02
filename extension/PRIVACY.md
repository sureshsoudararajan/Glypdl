# Glypdl Privacy Policy

The Glypdl Firefox Extension is built with a **strict privacy-first design**:

1. **No Data Collection**:
   * We do not collect, store, or transmit your browsing history, visited URLs, IP addresses, search queries, or personal telemetry.
   * No analytics or tracking scripts are included.

2. **No Remote Servers**:
   * The extension connects exclusively to your local machine (`localhost` via Native Messaging to the Glypdl desktop app). No requests are sent to third-party servers.

3. **No Cookie Access**:
   * The extension **does not request the `cookies` permission** and never reads, stores, or transmits your browser cookies.
   * Authentication for sites like YouTube or Instagram is handled locally by the Glypdl desktop app on your own system.

4. **Minimal Permissions**:
   * `nativeMessaging`: Required to communicate with the local Glypdl desktop application.
   * `activeTab`: Required to inspect media elements on the active page only when triggered.
   * `storage`: Required to save user preferences locally in your browser.
   * `contextMenus`: Required to provide right-click download actions.
   * `notifications`: Required to show notifications when downloads are queued.
