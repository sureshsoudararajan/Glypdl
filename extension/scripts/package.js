import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');
const distDir = path.resolve(rootDir, 'dist');

// Copy manifest.json, icons, locales to dist
fs.copyFileSync(path.resolve(rootDir, 'manifest.json'), path.resolve(distDir, 'manifest.json'));

function copyDir(src, dest) {
  if (!fs.existsSync(src)) return;
  fs.mkdirSync(dest, { recursive: true });
  const entries = fs.readdirSync(src, { withFileTypes: true });
  for (const entry of entries) {
    const srcPath = path.join(src, entry.name);
    const destPath = path.join(dest, entry.name);
    if (entry.isDirectory()) {
      copyDir(srcPath, destPath);
    } else {
      fs.copyFileSync(srcPath, destPath);
    }
  }
}

copyDir(path.resolve(rootDir, 'icons'), path.resolve(distDir, 'icons'));
copyDir(path.resolve(rootDir, '_locales'), path.resolve(distDir, '_locales'));

// Duplicate popup/options to root level in dist if nested under src
if (fs.existsSync(path.resolve(distDir, 'src/popup/index.html'))) {
  fs.mkdirSync(path.resolve(distDir, 'popup'), { recursive: true });
  fs.copyFileSync(path.resolve(distDir, 'src/popup/index.html'), path.resolve(distDir, 'popup/index.html'));
}
if (fs.existsSync(path.resolve(distDir, 'src/options/index.html'))) {
  fs.mkdirSync(path.resolve(distDir, 'options'), { recursive: true });
  fs.copyFileSync(path.resolve(distDir, 'src/options/index.html'), path.resolve(distDir, 'options/index.html'));
}

// Create ZIP / XPI package using Python zipfile
const xpiPath = path.resolve(rootDir, 'glypdl-firefox-extension.xpi');
const zipPath = path.resolve(rootDir, 'glypdl-firefox-extension.zip');

const pythonScript = `
import os
import zipfile

dist_dir = r"${distDir}"
xpi_path = r"${xpiPath}"
zip_path = r"${zipPath}"

for out_path in [xpi_path, zip_path]:
    with zipfile.ZipFile(out_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for root, dirs, files in os.walk(dist_dir):
            for file in files:
                abs_file = os.path.join(root, file)
                rel_file = os.path.relpath(abs_file, dist_dir)
                zf.write(abs_file, rel_file)

print(f"Created XPI: {xpi_path}")
print(f"Created ZIP: {zip_path}")
`;

try {
  execSync(`python3 -c '${pythonScript}'`, { stdio: 'inherit' });
} catch (e) {
  console.error('Packaging error:', e);
}
