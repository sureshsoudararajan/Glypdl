import { resolve } from 'path';
import { fileURLToPath } from 'url';
import { build } from 'vite';
import { execSync } from 'child_process';
import fs from 'fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = resolve(__filename, '..');
const rootDir = resolve(__dirname, '..');
const distDir = resolve(rootDir, 'dist');

async function runBuild() {
  // Clean dist
  if (fs.existsSync(distDir)) {
    fs.rmSync(distDir, { recursive: true, force: true });
  }
  fs.mkdirSync(distDir, { recursive: true });

  console.log('1. Building Popup and Options HTML pages...');
  await build({
    configFile: false,
    root: rootDir,
    build: {
      outDir: distDir,
      emptyOutDir: false,
      rollupOptions: {
        input: {
          popup: resolve(rootDir, 'src/popup/index.html'),
          options: resolve(rootDir, 'src/options/index.html')
        }
      }
    }
  });

  console.log('2. Building Content Script as standalone IIFE (Zero-import bundle)...');
  await build({
    configFile: false,
    root: rootDir,
    build: {
      outDir: distDir,
      emptyOutDir: false,
      lib: {
        entry: resolve(rootDir, 'src/content/index.ts'),
        name: 'GlypdlContent',
        formats: ['iife'],
        fileName: () => 'content/index.js'
      },
      rollupOptions: {
        output: {
          assetFileNames: (assetInfo) => {
            if (assetInfo.name === 'style.css' || assetInfo.name?.endsWith('.css')) {
              return 'content/index.css';
            }
            return 'assets/[name][extname]';
          }
        }
      }
    }
  });

  console.log('3. Building Background Service as standalone IIFE...');
  await build({
    configFile: false,
    root: rootDir,
    build: {
      outDir: distDir,
      emptyOutDir: false,
      lib: {
        entry: resolve(rootDir, 'src/background/index.ts'),
        name: 'GlypdlBackground',
        formats: ['iife'],
        fileName: () => 'background/index.js'
      }
    }
  });

  console.log('4. Packaging extension for Firefox...');
  execSync(`node "${resolve(rootDir, 'scripts/package.js')}"`, { stdio: 'inherit' });
}

runBuild().catch((err) => {
  console.error('Build failed:', err);
  process.exit(1);
});
