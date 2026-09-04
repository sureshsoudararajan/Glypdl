#!/usr/bin/env bash
set -e

APP_DIR="AppDir"
rm -rf "$APP_DIR" Glypdl-x86_64.AppImage

mkdir -p "$APP_DIR/usr/bin"
mkdir -p "$APP_DIR/usr/share/glypdl/glypdl"
mkdir -p "$APP_DIR/usr/share/glypdl/bin"
mkdir -p "$APP_DIR/usr/lib/python3/dist-packages/glypdl"
mkdir -p "$APP_DIR/usr/lib/girepository-1.0"
mkdir -p "$APP_DIR/usr/lib/x86_64-linux-gnu"
mkdir -p "$APP_DIR/usr/share/applications"
mkdir -p "$APP_DIR/usr/share/metainfo"
mkdir -p "$APP_DIR/usr/share/icons/hicolor/scalable/apps"
mkdir -p "$APP_DIR/usr/share/icons/hicolor/512x512/apps"

# Copy application source
cp -r src/glypdl/* "$APP_DIR/usr/share/glypdl/glypdl/"
cp -r src/glypdl/* "$APP_DIR/usr/lib/python3/dist-packages/glypdl/"

# Copy launcher and host binary
cp bin/glypdl "$APP_DIR/usr/bin/glypdl"
chmod +x "$APP_DIR/usr/bin/glypdl"
cp bin/glypdl-host "$APP_DIR/usr/bin/glypdl-host"
chmod +x "$APP_DIR/usr/bin/glypdl-host"

# Download latest standalone official yt-dlp binary into private bundle
echo "Downloading latest official yt-dlp binary into private bundle..."
curl -sL https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o "$APP_DIR/usr/share/glypdl/bin/yt-dlp"
chmod +x "$APP_DIR/usr/share/glypdl/bin/yt-dlp"

# Bundle GObject Introspection typelibs (Gtk-4.0, Adw-1, GLib, Gio, Gdk, Gsk, Graphene, Pango, Cairo, etc.)
for tldir in /usr/lib/x86_64-linux-gnu/girepository-1.0 /usr/lib64/girepository-1.0 /usr/lib/girepository-1.0; do
    if [ -d "$tldir" ]; then
        cp -r "$tldir"/*.typelib "$APP_DIR/usr/lib/girepository-1.0/" 2>/dev/null || true
    fi
done

# Bundle GTK4 & Libadwaita shared libraries
for libdir in /usr/lib/x86_64-linux-gnu /usr/lib64 /usr/lib; do
    if [ -d "$libdir" ]; then
        cp -d "$libdir"/libgtk-4.so* "$APP_DIR/usr/lib/x86_64-linux-gnu/" 2>/dev/null || true
        cp -d "$libdir"/libadwaita-1.so* "$APP_DIR/usr/lib/x86_64-linux-gnu/" 2>/dev/null || true
        cp -d "$libdir"/libgraphene-1.0.so* "$APP_DIR/usr/lib/x86_64-linux-gnu/" 2>/dev/null || true
    fi
done

# Copy AppRun & desktop integration
cp packaging/appimage/AppRun "$APP_DIR/AppRun"
chmod +x "$APP_DIR/AppRun"

cp data/desktop/io.github.sureshsoudararajan.Glypdl.desktop "$APP_DIR/io.github.sureshsoudararajan.Glypdl.desktop"
cp data/desktop/io.github.sureshsoudararajan.Glypdl.desktop "$APP_DIR/usr/share/applications/io.github.sureshsoudararajan.Glypdl.desktop"
cp data/metainfo/io.github.sureshsoudararajan.Glypdl.metainfo.xml "$APP_DIR/usr/share/metainfo/"
cp data/icons/hicolor/scalable/apps/io.github.sureshsoudararajan.Glypdl.svg "$APP_DIR/io.github.sureshsoudararajan.Glypdl.svg"
cp data/icons/hicolor/scalable/apps/io.github.sureshsoudararajan.Glypdl.svg "$APP_DIR/usr/share/icons/hicolor/scalable/apps/"
cp data/icons/hicolor/512x512/apps/io.github.sureshsoudararajan.Glypdl.png "$APP_DIR/usr/share/icons/hicolor/512x512/apps/"

if command -v appimagetool >/dev/null 2>&1; then
    ARCH=x86_64 appimagetool --appimage-extract-and-run "$APP_DIR" Glypdl-x86_64.AppImage || ARCH=x86_64 appimagetool "$APP_DIR" Glypdl-x86_64.AppImage
    echo "AppImage created successfully: Glypdl-x86_64.AppImage"
else
    echo "AppDir structured at $APP_DIR. Install appimagetool to package into a single .AppImage binary."
fi
