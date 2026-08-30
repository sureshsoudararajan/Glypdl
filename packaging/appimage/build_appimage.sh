#!/usr/bin/env bash
set -e

APP_DIR="AppDir"
rm -rf "$APP_DIR" Glypdl-x86_64.AppImage

mkdir -p "$APP_DIR/usr/bin"
mkdir -p "$APP_DIR/usr/lib/python3/dist-packages"
mkdir -p "$APP_DIR/usr/share/applications"
mkdir -p "$APP_DIR/usr/share/metainfo"
mkdir -p "$APP_DIR/usr/share/icons/hicolor/scalable/apps"

# Copy application source
cp -r src/glypdl "$APP_DIR/usr/lib/python3/dist-packages/"

# Copy launcher binary
cat << 'EOF' > "$APP_DIR/usr/bin/glypdl"
#!/usr/bin/env python3
import sys
from glypdl.app import main
if __name__ == '__main__':
    sys.exit(main())
EOF
chmod +x "$APP_DIR/usr/bin/glypdl"

# Copy AppRun & desktop integration
cp packaging/appimage/AppRun "$APP_DIR/AppRun"
chmod +x "$APP_DIR/AppRun"

cp data/desktop/io.github.suresh.Glypdl.desktop "$APP_DIR/io.github.suresh.Glypdl.desktop"
cp data/desktop/io.github.suresh.Glypdl.desktop "$APP_DIR/usr/share/applications/io.github.suresh.Glypdl.desktop"
cp data/metainfo/io.github.suresh.Glypdl.metainfo.xml "$APP_DIR/usr/share/metainfo/"
cp data/icons/hicolor/scalable/apps/io.github.suresh.Glypdl.svg "$APP_DIR/io.github.suresh.Glypdl.svg"
cp data/icons/hicolor/scalable/apps/io.github.suresh.Glypdl.svg "$APP_DIR/usr/share/icons/hicolor/scalable/apps/"

if command -v appimagetool >/dev/null 2>&1; then
    ARCH=x86_64 appimagetool "$APP_DIR" Glypdl-x86_64.AppImage
    echo "AppImage created successfully: Glypdl-x86_64.AppImage"
else
    echo "AppDir structured at $APP_DIR. Install appimagetool to package into a single .AppImage binary."
fi
