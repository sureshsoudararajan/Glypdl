# Maintainer: Suresh Soundararajan <sureshsoundararajan18@gmail.com>
pkgname=glypdl
pkgver=1.0.0
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

package() {
    cd "$startdir"
    
    # 1. Install Python module into /usr/share/glypdl/glypdl
    install -d "$pkgdir/usr/share/glypdl"
    cd "$startdir/src"
    find glypdl -type f -name "*.py" -not -path "*/__pycache__/*" -exec install -Dm644 "{}" "$pkgdir/usr/share/glypdl/{}" \;
    
    # 2. Also install into system Python site-packages
    python_site=$(python3 -c "import site; print(site.getsitepackages()[0])" 2>/dev/null || echo "/usr/lib/python3.14/site-packages")
    install -d "$pkgdir$python_site"
    find glypdl -type f -name "*.py" -not -path "*/__pycache__/*" -exec install -Dm644 "{}" "$pkgdir$python_site/{}" \;
    cd "$startdir"

    # 3. Launcher binary
    install -Dm755 bin/glypdl "$pkgdir/usr/bin/glypdl"

    # 4. Desktop entry, Metainfo, and Official App Icons
    install -Dm644 data/desktop/io.github.sureshsoudararajan.Glypdl.desktop "$pkgdir/usr/share/applications/io.github.sureshsoudararajan.Glypdl.desktop"
    install -Dm644 data/metainfo/io.github.sureshsoudararajan.Glypdl.metainfo.xml "$pkgdir/usr/share/metainfo/io.github.sureshsoudararajan.Glypdl.metainfo.xml"
    install -Dm644 data/icons/hicolor/scalable/apps/io.github.sureshsoudararajan.Glypdl.svg "$pkgdir/usr/share/icons/hicolor/scalable/apps/io.github.sureshsoudararajan.Glypdl.svg"
    install -Dm644 data/icons/hicolor/512x512/apps/io.github.sureshsoudararajan.Glypdl.png "$pkgdir/usr/share/icons/hicolor/512x512/apps/io.github.sureshsoudararajan.Glypdl.png"
    install -Dm644 LICENSE "$pkgdir/usr/share/licenses/$pkgname/LICENSE"
}
