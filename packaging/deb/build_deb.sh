#!/usr/bin/env bash
set -e

VERSION="1.0.0"
PKG_DIR="glypdl_${VERSION}_all"
rm -rf "$PKG_DIR" "$PKG_DIR.deb"

mkdir -p "$PKG_DIR/DEBIAN"
mkdir -p "$PKG_DIR/usr/lib/python3/dist-packages/glypdl"
mkdir -p "$PKG_DIR/usr/bin"
mkdir -p "$PKG_DIR/usr/share/applications"
mkdir -p "$PKG_DIR/usr/share/metainfo"
mkdir -p "$PKG_DIR/usr/share/icons/hicolor/scalable/apps"

# Copy python source code
cp -r src/glypdl/* "$PKG_DIR/usr/lib/python3/dist-packages/glypdl/"

# Launcher wrapper
cat << 'EOF' > "$PKG_DIR/usr/bin/glypdl"
#!/usr/bin/env python3
import sys
from glypdl.app import main
if __name__ == '__main__':
    sys.exit(main())
EOF
chmod +x "$PKG_DIR/usr/bin/glypdl"

# Desktop, Metainfo, Icons
cp data/desktop/io.github.sureshsoudararajan.Glypdl.desktop "$PKG_DIR/usr/share/applications/"
cp data/metainfo/io.github.sureshsoudararajan.Glypdl.metainfo.xml "$PKG_DIR/usr/share/metainfo/"
cp data/icons/hicolor/scalable/apps/io.github.sureshsoudararajan.Glypdl.svg "$PKG_DIR/usr/share/icons/hicolor/scalable/apps/"
cp data/icons/hicolor/512x512/apps/io.github.sureshsoudararajan.Glypdl.png "$PKG_DIR/usr/share/icons/hicolor/512x512/apps/"

# Control file
cat << EOF > "$PKG_DIR/DEBIAN/control"
Package: glypdl
Version: ${VERSION}
Section: utils
Priority: optional
Architecture: all
Depends: python3, python3-gi, gir1.2-gtk-4.0, gir1.2-adw-1, yt-dlp, ffmpeg
Maintainer: Suresh Soundararajan <sureshsoundararajan18@gmail.com>
Homepage: https://github.com/sureshsoudararajan/Glypdl
Description: Native Linux GTK4/libadwaita download manager frontend for yt-dlp
 Glypdl is a fast, lightweight GTK4/libadwaita download manager that uses
 the system-installed yt-dlp binary to download video and audio media with
 real-time network metrics, queue management, and download history.
EOF

dpkg-deb --build "$PKG_DIR"
rm -rf "$PKG_DIR"
echo "Built $PKG_DIR.deb successfully!"
