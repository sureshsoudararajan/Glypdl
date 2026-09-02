# Glypdl Security Policy

## Security Architecture

The communication bridge between the browser extension and the native desktop application is security-critical:

1. **Native Messaging Host Isolation**:
   * The Native Messaging Host (`glypdl-host`) enforces a strict payload size cap of **1 MB** (`MAX_MESSAGE_SIZE`).
   * JSON payloads are parsed using Python's `json.loads` with comprehensive error boundary protection.
   * Remote code execution (`eval`, `exec`, shell command interpolation) is strictly prohibited. Subprocesses are always invoked with argument arrays and `shell=False`.

2. **URL Validation & Scheme Whitelisting**:
   * Download requests must use `http://` or `https://` schemes.
   * `javascript:`, `data:`, `file:`, and local resource URLs are rejected immediately.

3. **DRM Handling**:
   * Content utilizing Encrypted Media Extensions (EME) or DRM license endpoints (Widevine, PlayReady, FairPlay) is marked as `🔒 DRM Protected`.
   * Glypdl does not circumvent DRM or extract encryption keys.

4. **Extension ID Restriction**:
   * The native host manifest restricts execution exclusively to the official extension ID (`glypdl@suresh.io`).
