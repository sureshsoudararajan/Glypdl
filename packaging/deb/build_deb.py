#!/usr/bin/env python3
"""Universal Debian (.deb) package builder in pure Python."""

import os
import sys
import shutil
import tarfile
import tempfile
from pathlib import Path

VERSION = "1.0.0"
PACKAGE = "glypdl"
DEB_NAME = f"{PACKAGE}_{VERSION}_all.deb"


def create_deb():
    root_dir = Path(__file__).resolve().parent.parent.parent
    os.chdir(root_dir)

    with tempfile.TemporaryDirectory() as tmpdir:
        tmp_path = Path(tmpdir)
        pkg_root = tmp_path / "pkg"
        
        # Directory structure
        (pkg_root / "usr/bin").mkdir(parents=True, exist_ok=True)
        (pkg_root / "usr/share/glypdl/glypdl").mkdir(parents=True, exist_ok=True)
        (pkg_root / "usr/share/applications").mkdir(parents=True, exist_ok=True)
        (pkg_root / "usr/share/metainfo").mkdir(parents=True, exist_ok=True)
        (pkg_root / "usr/share/icons/hicolor/scalable/apps").mkdir(parents=True, exist_ok=True)
        (pkg_root / "usr/share/icons/hicolor/512x512/apps").mkdir(parents=True, exist_ok=True)
        (pkg_root / "usr/share/doc/glypdl").mkdir(parents=True, exist_ok=True)

        # 1. Copy application files (excluding __pycache__)
        src_dir = root_dir / "src" / "glypdl"
        dest_src = pkg_root / "usr/share/glypdl/glypdl"
        for item in src_dir.rglob("*"):
            if "__pycache__" in item.parts or item.suffix in ('.pyc', '.pyo'):
                continue
            rel = item.relative_to(src_dir)
            target = dest_src / rel
            if item.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                shutil.copy2(item, target)

        # 2. Copy launcher
        launcher_src = root_dir / "bin" / "glypdl"
        launcher_dest = pkg_root / "usr/bin/glypdl"
        shutil.copy2(launcher_src, launcher_dest)
        launcher_dest.chmod(0o755)

        # 3. Copy desktop, metainfo, icon, and docs
        shutil.copy2(root_dir / "data/desktop/io.github.suresh.Glypdl.desktop", pkg_root / "usr/share/applications/")
        shutil.copy2(root_dir / "data/metainfo/io.github.suresh.Glypdl.metainfo.xml", pkg_root / "usr/share/metainfo/")
        shutil.copy2(root_dir / "data/icons/hicolor/scalable/apps/io.github.suresh.Glypdl.svg", pkg_root / "usr/share/icons/hicolor/scalable/apps/")
        if (root_dir / "data/icons/hicolor/512x512/apps/io.github.suresh.Glypdl.png").exists():
            shutil.copy2(root_dir / "data/icons/hicolor/512x512/apps/io.github.suresh.Glypdl.png", pkg_root / "usr/share/icons/hicolor/512x512/apps/")
        if (root_dir / "LICENSE").exists():
            shutil.copy2(root_dir / "LICENSE", pkg_root / "usr/share/doc/glypdl/copyright")

        # 4. Create control file
        control_dir = tmp_path / "control"
        control_dir.mkdir(parents=True, exist_ok=True)
        control_content = f"""Package: {PACKAGE}
Version: {VERSION}
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
"""
        (control_dir / "control").write_text(control_content)

        # 5. Create control.tar.gz
        control_tar = tmp_path / "control.tar.gz"
        with tarfile.open(control_tar, "w:gz") as tar:
            for item in control_dir.iterdir():
                tar.add(item, arcname=item.name)

        # 6. Create data.tar.gz
        data_tar = tmp_path / "data.tar.gz"
        with tarfile.open(data_tar, "w:gz") as tar:
            for item in (pkg_root / "usr").rglob("*"):
                arcname = str(item.relative_to(pkg_root))
                tar.add(item, arcname=arcname)

        # 7. Create debian-binary
        deb_binary = tmp_path / "debian-binary"
        deb_binary.write_bytes(b"2.0\n")

        # 8. Assemble AR (.deb) archive
        out_deb = root_dir / DEB_NAME
        
        def write_ar_header(f, name, size):
            header = f"{name:<16}{0:<12}{0:<6}{0:<6}{'100644':<8}{size:<10}`\n".encode('ascii')
            f.write(header)

        with open(out_deb, "wb") as f:
            f.write(b"!<arch>\n")
            
            b_data = deb_binary.read_bytes()
            write_ar_header(f, "debian-binary", len(b_data))
            f.write(b_data)
            if len(b_data) % 2 != 0:
                f.write(b"\n")
                
            c_data = control_tar.read_bytes()
            write_ar_header(f, "control.tar.gz", len(c_data))
            f.write(c_data)
            if len(c_data) % 2 != 0:
                f.write(b"\n")
                
            d_data = data_tar.read_bytes()
            write_ar_header(f, "data.tar.gz", len(d_data))
            f.write(d_data)
            if len(d_data) % 2 != 0:
                f.write(b"\n")

        print(f"Successfully generated {out_deb.name} ({os.path.getsize(out_deb)} bytes)")


if __name__ == '__main__':
    create_deb()
