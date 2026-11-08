# Maintainer: Suresh Soundararajan <sureshsoundararajan18@gmail.com>
pkgname=glypdl
pkgver=2.1.0
pkgrel=1
pkgdesc="A lightweight native Linux graphical frontend for yt-dlp"
arch=('any')
url="https://github.com/sureshsoudararajan/Glypdl"
license=('GPL-3.0-or-later')
depends=(
    'python'
    'python-gobject'
    'gtk4'
    'libadwaita'
    'yt-dlp'
    'ffmpeg'
)

prepare() {
    cd "$startdir"
    mkdir -p bin
    echo "Downloading latest official yt-dlp binary into package bundle..."
    curl -sL https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o bin/yt-dlp-standalone
    chmod +x bin/yt-dlp-standalone
}

package() {
    cd "$startdir"
    
    install -d "$pkgdir/usr/share/glypdl"
    cp -r src/glypdl "$pkgdir/usr/share/glypdl/"
    install -Dm755 bin/glypdl "$pkgdir/usr/bin/glypdl"
    install -Dm755 bin/glypdl-host "$pkgdir/usr/bin/glypdl-host"
    if [ -f bin/yt-dlp-standalone ]; then
        install -Dm755 bin/yt-dlp-standalone "$pkgdir/usr/share/glypdl/bin/yt-dlp"
    fi

    install -Dm644 data/desktop/io.github.sureshsoudararajan.Glypdl.desktop "$pkgdir/usr/share/applications/io.github.sureshsoudararajan.Glypdl.desktop"
    install -Dm644 data/metainfo/io.github.sureshsoudararajan.Glypdl.metainfo.xml "$pkgdir/usr/share/metainfo/io.github.sureshsoudararajan.Glypdl.metainfo.xml"
    install -Dm644 data/icons/hicolor/scalable/apps/io.github.sureshsoudararajan.Glypdl.svg "$pkgdir/usr/share/icons/hicolor/scalable/apps/io.github.sureshsoudararajan.Glypdl.svg"
    install -Dm644 data/icons/hicolor/512x512/apps/io.github.sureshsoudararajan.Glypdl.png "$pkgdir/usr/share/icons/hicolor/512x512/apps/io.github.sureshsoudararajan.Glypdl.png"
    install -Dm644 LICENSE "$pkgdir/usr/share/licenses/$pkgname/LICENSE"
}
