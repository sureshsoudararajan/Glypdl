Name:           glypdl
Version:        1.0.0
Release:        1%{?dist}
Summary:        A lightweight native Linux graphical frontend for yt-dlp

License:        GPLv3+
URL:            https://github.com/sureshsoudararajan/Glypdl
Source0:        %{name}-%{version}.tar.gz

BuildArch:      noarch
BuildRequires:  python3-devel
BuildRequires:  python3-setuptools

Requires:       python3
Requires:       python3-gobject
Requires:       gtk4
Requires:       libadwaita
Requires:       yt-dlp
Requires:       ffmpeg

%description
A lightweight native Linux graphical frontend for yt-dlp.
Built with GTK4 and libadwaita conforming to GNOME HIG.

%prep
%autosetup

%build
%py3_build

%install
%py3_install
install -Dm644 data/desktop/io.github.suresh.Glypdl.desktop %{buildroot}%{_datadir}/applications/io.github.suresh.Glypdl.desktop
install -Dm644 data/metainfo/io.github.suresh.Glypdl.metainfo.xml %{buildroot}%{_datadir}/metainfo/io.github.suresh.Glypdl.metainfo.xml
install -Dm644 data/icons/hicolor/scalable/apps/io.github.suresh.Glypdl.svg %{buildroot}%{_datadir}/icons/hicolor/scalable/apps/io.github.suresh.Glypdl.svg
install -Dm644 data/icons/hicolor/512x512/apps/io.github.suresh.Glypdl.png %{buildroot}%{_datadir}/icons/hicolor/512x512/apps/io.github.suresh.Glypdl.png

%files
%license LICENSE
%doc README.md
%{_bindir}/glypdl
%{python3_sitelib}/glypdl*
%{_datadir}/applications/io.github.suresh.Glypdl.desktop
%{_datadir}/metainfo/io.github.suresh.Glypdl.metainfo.xml
%{_datadir}/icons/hicolor/scalable/apps/io.github.suresh.Glypdl.svg
%{_datadir}/icons/hicolor/512x512/apps/io.github.suresh.Glypdl.png

%changelog
* Sun Aug 30 2026 Suresh Soundararajan <sureshsoundararajan18@gmail.com> - 1.0.0-1
- Initial release of Glypdl for Fedora / RHEL
