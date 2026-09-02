# Glypdl Extension Development Guide

## Prerequisites

* **Node.js**: >= 18.0.0
* **npm**: >= 9.0.0
* **Firefox**: Developer Edition or standard Firefox >= 109.0

## Getting Started

```bash
cd extension
npm install
```

### Development Mode (Watch Mode)
```bash
npm run dev
```

### Running Tests
```bash
npm test
```

### Building Production Package
```bash
npm run build
```
This will compile TypeScript, bundle CSS, and output the standalone `glypdl-firefox-extension.xpi` installer.

---

## Testing in Firefox

1. Open Firefox and navigate to `about:debugging#/runtime/this-firefox`.
2. Click **Load Temporary Add-on…**.
3. Select `extension/manifest.json`.
4. Open any media page (e.g. YouTube, Vimeo) to test detection, overlay buttons, and popup actions.
