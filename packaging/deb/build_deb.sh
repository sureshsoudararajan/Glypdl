#!/usr/bin/env bash
set -e

VERSION="${VERSION:-1.2.0}"
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

# Download latest standalone official yt-dlp binary into private /usr/share/glypdl/bin
echo "Downloading latest official yt-dlp binary into private bundle..."
curl -sL https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o "$PKG_DIR/usr/share/glypdl/bin/yt-dlp"
chmod +x "$PKG_DIR/usr/share/glypdl/bin/yt-dlp"

# Universal Launcher and Native Host
cp bin/glypdl "$PKG_DIR/usr/bin/glypdl"
chmod +x "$PKG_DIR/usr/bin/glypdl"
cp bin/glypdl-host "$PKG_DIR/usr/bin/glypdl-host"
chmod +x "$PKG_DIR/usr/bin/glypdl-host"

# Desktop, Metainfo, Icons
cp data/desktop/io.github.sureshsoudararajan.Glypdl.desktop "$PKG_DIR/usr/share/applications/"
cp data/metainfo/io.github.sureshsoudararajan.Glypdl.metainfo.xml "$PKG_DIR/usr/share/metainfo/"
cp data/icons/hicolor/scalable/apps/io.github.sureshsoudararajan.Glypdl.svg "$PKG_DIR/usr/share/icons/hicolor/scalable/apps/"
cp data/icons/hicolor/512x512/apps/io.github.sureshsoudararajan.Glypdl.png "$PKG_DIR/usr/share/icons/hicolor/512x512/apps/"

# Native Messaging Host Manifests for Firefox and Chromium browsers
mkdir -p "$PKG_DIR/usr/lib/mozilla/native-messaging-hosts"
mkdir -p "$PKG_DIR/etc/opt/chrome/native-messaging-hosts"
mkdir -p "$PKG_DIR/etc/chromium/native-messaging-hosts"

cat << EOF > "$PKG_DIR/usr/lib/mozilla/native-messaging-hosts/io.github.sureshsoudararajan.glypdl.json"
{
  "name": "io.github.sureshsoudararajan.glypdl",
  "description": "Glypdl Native Messaging Host for Firefox and LibreWolf",
  "path": "/usr/bin/glypdl-host",
  "type": "stdio",
  "allowed_extensions": [
    "glypdl@suresh.io"
  ]
}
EOF

cat << EOF > "$PKG_DIR/etc/opt/chrome/native-messaging-hosts/io.github.sureshsoudararajan.glypdl.json"
{
  "name": "io.github.sureshsoudararajan.glypdl",
  "description": "Glypdl Native Messaging Host for Google Chrome",
  "path": "/usr/bin/glypdl-host",
  "type": "stdio",
  "allowed_origins": [
    "chrome-extension://*/"
  ]
}
EOF

cp "$PKG_DIR/etc/opt/chrome/native-messaging-hosts/io.github.sureshsoudararajan.glypdl.json" \
   "$PKG_DIR/etc/chromium/native-messaging-hosts/io.github.sureshsoudararajan.glypdl.json"

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

# Post-installation triggers
cat << 'EOF' > "$PKG_DIR/DEBIAN/postinst"
#!/bin/sh
set -e

if [ "$1" = "configure" ]; then
    if which update-desktop-database >/dev/null 2>&1; then
        update-desktop-database -q /usr/share/applications || true
    fi
    if which gtk-update-icon-cache >/dev/null 2>&1; then
        gtk-update-icon-cache -q -t -f /usr/share/icons/hicolor || true
    fi
fi
EOF
chmod 755 "$PKG_DIR/DEBIAN/postinst"

dpkg-deb --build "$PKG_DIR"
rm -rf "$PKG_DIR"
echo "Built $PKG_DIR.deb successfully with private bundled latest yt-dlp!"
