#!/usr/bin/env bash
set -e

VERSION="1.0.0"
PKG_DIR="glypdl_${VERSION}_all"
rm -rf "$PKG_DIR" "$PKG_DIR.deb"

mkdir -p "$PKG_DIR/DEBIAN"
mkdir -p "$PKG_DIR/usr/lib/python3/dist-packages/glypdl"
mkdir -p "$PKG_DIR/usr/share/glypdl/glypdl"
mkdir -p "$PKG_DIR/usr/bin"
mkdir -p "$PKG_DIR/usr/share/glypdl/bin"
mkdir -p "$PKG_DIR/usr/share/applications"
mkdir -p "$PKG_DIR/usr/share/metainfo"
mkdir -p "$PKG_DIR/usr/share/icons/hicolor/scalable/apps"
mkdir -p "$PKG_DIR/usr/share/icons/hicolor/512x512/apps"

# Copy python source code to both standard dist-packages and universal /usr/share/glypdl
cp -r src/glypdl/* "$PKG_DIR/usr/lib/python3/dist-packages/glypdl/"
cp -r src/glypdl/* "$PKG_DIR/usr/share/glypdl/glypdl/"

# Download latest standalone official yt-dlp binary
echo "Downloading latest official yt-dlp binary..."
curl -sL https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o "$PKG_DIR/usr/bin/yt-dlp"
chmod +x "$PKG_DIR/usr/bin/yt-dlp"
cp "$PKG_DIR/usr/bin/yt-dlp" "$PKG_DIR/usr/share/glypdl/bin/yt-dlp"

# Universal Launcher
cp bin/glypdl "$PKG_DIR/usr/bin/glypdl"
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
Depends: python3, python3-gi, gir1.2-gtk-4.0, gir1.2-adw-1
Recommends: ffmpeg
Maintainer: Suresh Soundararajan <sureshsoundararajan18@gmail.com>
Homepage: https://github.com/sureshsoudararajan/Glypdl
Description: Native Linux GTK4/libadwaita download manager frontend for yt-dlp
 Glypdl is a fast, lightweight GTK4/libadwaita download manager that comes
 bundled with the latest standalone yt-dlp binary to download video and
 audio media with real-time metrics, queue management, and history.
EOF

dpkg-deb --build "$PKG_DIR"
rm -rf "$PKG_DIR"
echo "Built $PKG_DIR.deb successfully with bundled latest yt-dlp!"
