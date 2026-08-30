#!/usr/bin/env bash
set -e

VERSION="1.0.0"
PKGNAME="glypdl"
BUILD_DIR="$(pwd)/build-rpm"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"/{BUILD,BUILDROOT,RPMS,SOURCES,SPECS,SRPMS}

# Fetch latest standalone official yt-dlp binary
echo "Downloading latest official yt-dlp binary..."
curl -sL https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o "$BUILD_DIR/SOURCES/yt-dlp"
chmod +x "$BUILD_DIR/SOURCES/yt-dlp"

# Create source tarball
tar --exclude-vcs --exclude="build-*" --exclude="*.pkg.tar.zst" --exclude="*.deb" --exclude="*.rpm" --exclude="*.AppImage" \
    --transform "s,^\.,${PKGNAME}-${VERSION}," \
    -czf "$BUILD_DIR/SOURCES/${PKGNAME}-${VERSION}.tar.gz" .

# Copy spec file
cp packaging/rpm/glypdl.spec "$BUILD_DIR/SPECS/"

# Build RPM
rpmbuild --define "_topdir $BUILD_DIR" -ba "$BUILD_DIR/SPECS/glypdl.spec"

# Copy generated RPMs to current directory
find "$BUILD_DIR/RPMS" -name "*.rpm" -exec cp {} . \;
echo "RPM built successfully with bundled latest yt-dlp: $(ls *.rpm)"
