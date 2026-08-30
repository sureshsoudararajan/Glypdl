# Glypdl Packaging Guide

This guide details how to build and distribute **Glypdl** for all major Linux package managers and formats.

---

## 1. Arch Linux / Manjaro / EndeavourOS (`.pkg.tar.zst`)

### Build with `makepkg`
To build a pacman package directly from the local repository:

```bash
# Build the package
makepkg -f --nodeps

# Install the built package locally
sudo pacman -U glypdl-1.0.0-1-any.pkg.tar.zst
```

### For AUR (Arch User Repository)
The AUR PKGBUILD is located at `packaging/arch/PKGBUILD`. When publishing a new release:
1. Update `pkgver` in `packaging/arch/PKGBUILD`.
2. Generate checksums: `updpkgsums`.
3. Generate `.SRCINFO`: `makepkg --printsrcinfo > .SRCINFO`.
4. Commit and push to your AUR git repository.

---

## 2. Debian / Ubuntu / Linux Mint / Pop!_OS (`.deb`)

### Method A: Pure Python Builder (Works on ANY Linux distribution)
You can build a `.deb` package on any machine without needing `dpkg-deb` installed:

```bash
python3 packaging/deb/build_deb.py
```
> Outputs: `glypdl_1.0.0_all.deb`

### Method B: Using `dpkg-deb` (On Debian / Ubuntu)
```bash
bash packaging/deb/build_deb.sh
```

### Install the `.deb` package:
```bash
sudo apt install ./glypdl_1.0.0_all.deb
# or
sudo dpkg -i glypdl_1.0.0_all.deb
sudo apt-get install -f
```

---

## 3. Fedora / RHEL / openSUSE (`.rpm`)

### Prerequisites on Fedora/RHEL:
```bash
sudo dnf install rpm-build rpmdevtools python3-devel
```

### Build Steps:
1. Create the RPM build tree:
```bash
rpmdev-setuptree
# Or manually:
mkdir -p ~/rpmbuild/{BUILD,RPMS,SOURCES,SPECS,SRPMS}
```

2. Create the source tarball:
```bash
tar --exclude-vcs --exclude='*.deb' --exclude='*.pkg.tar.zst' \
    -czvf ~/rpmbuild/SOURCES/glypdl-1.0.0.tar.gz \
    --transform 's,^\.,glypdl-1.0.0,' .
```

3. Copy spec file and build RPM:
```bash
cp packaging/rpm/glypdl.spec ~/rpmbuild/SPECS/
rpmbuild -ba ~/rpmbuild/SPECS/glypdl.spec
```
> The built RPM will be located at `~/rpmbuild/RPMS/noarch/glypdl-1.0.0-1.*.noarch.rpm`.

### Install the `.rpm` package:
```bash
sudo dnf install ~/rpmbuild/RPMS/noarch/glypdl-1.0.0-1.*.noarch.rpm
```

---

## 4. Flatpak (Cross-Distribution)

The Flatpak manifest is located at `packaging/flatpak/io.github.suresh.Glypdl.yaml`.

### Prerequisites:
```bash
# Install Flatpak and Flatpak Builder
# On Arch: sudo pacman -S flatpak flatpak-builder
# On Fedora: sudo dnf install flatpak flatpak-builder
# On Ubuntu: sudo apt install flatpak flatpak-builder

# Install the GNOME 46 runtime and SDK:
flatpak remote-add --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo
flatpak install flathub org.gnome.Platform//46 org.gnome.Sdk//46
```

### Build & Install Locally:
```bash
flatpak-builder --user --install --force-clean build-dir packaging/flatpak/io.github.suresh.Glypdl.yaml
```

### Run the Flatpak:
```bash
flatpak run io.github.suresh.Glypdl
```

### Export Flatpak Bundle (`.flatpak` single file):
```bash
flatpak-builder --repo=repo --force-clean build-dir packaging/flatpak/io.github.suresh.Glypdl.yaml
flatpak build-bundle repo glypdl.flatpak io.github.suresh.Glypdl
```
