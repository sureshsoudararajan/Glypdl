"""Main application module for Glypdl."""

import os
import sys
from pathlib import Path
import gi

gi.require_version('Gtk', '4.0')
gi.require_version('Adw', '1')
from gi.repository import Gtk, Adw, Gio, GLib, Gdk

from glypdl import __version__, APP_ID
from glypdl.models.settings import Settings
from glypdl.services.ytdlp import YtDlpService
from glypdl.services.metadata import MetadataService
from glypdl.services.downloader import DownloadManager
from glypdl.services.history import HistoryService
from glypdl.services.cookies import CookieService
from glypdl.utils.paths import ensure_dirs
from glypdl.window import GlypdlWindow
from glypdl.widgets.preferences import PreferencesDialog


class GlypdlApp(Adw.Application):
    """Native Linux GTK4/libadwaita download manager application."""

    def __init__(self):
        super().__init__(
            application_id=APP_ID,
            flags=Gio.ApplicationFlags.FLAGS_NONE
        )
        self.win = None
        self.settings = None
        self.ytdlp_service = None
        self.metadata_service = None
        self.download_manager = None
        self.history_service = None
        self.cookie_service = None

    def do_startup(self):
        Adw.Application.do_startup(self)
        
        # Ensure XDG directories exist
        ensure_dirs()

        # Register application icons into Gtk.IconTheme
        self._setup_icon_theme()

        # Initialize core services
        self.settings = Settings()
        self.ytdlp_service = YtDlpService(settings=self.settings)
        self.cookie_service = CookieService()
        self.metadata_service = MetadataService(self.ytdlp_service)
        self.download_manager = DownloadManager(self.ytdlp_service, self.settings)
        self.history_service = HistoryService()

        # Apply appearance / theme
        self._apply_theme()

        # Actions and shortcuts
        self._setup_actions()
        self._setup_shortcuts()

    def _setup_icon_theme(self):
        """Add application icon paths to Gtk.IconTheme search paths."""
        try:
            display = Gdk.Display.get_default()
            if display:
                theme = Gtk.IconTheme.get_for_display(display)
                data_icons = Path(__file__).parent.parent.parent / "data" / "icons"
                if data_icons.exists():
                    theme.add_search_path(str(data_icons))
                    hicolor_scalable = data_icons / "hicolor" / "scalable" / "apps"
                    if hicolor_scalable.exists():
                        theme.add_search_path(str(hicolor_scalable))
                    hicolor_512 = data_icons / "hicolor" / "512x512" / "apps"
                    if hicolor_512.exists():
                        theme.add_search_path(str(hicolor_512))
        except Exception:
            pass

    def do_activate(self):
        if not self.win:
            self.win = GlypdlWindow(self)

        self.win.present()

        # Verify yt-dlp availability on startup
        if not self.ytdlp_service.is_available():
            self._show_missing_ytdlp_dialog()

    def _apply_theme(self):
        style_manager = Adw.StyleManager.get_default()
        color_scheme = self.settings.get_color_scheme()
        if color_scheme == "dark":
            style_manager.set_color_scheme(Adw.ColorScheme.FORCE_DARK)
        elif color_scheme == "light":
            style_manager.set_color_scheme(Adw.ColorScheme.FORCE_LIGHT)
        else:
            style_manager.set_color_scheme(Adw.ColorScheme.DEFAULT)

    def _show_missing_ytdlp_dialog(self):
        distro_name, install_cmd = self._get_distro_install_command()
        
        dialog = Adw.MessageDialog(
            transient_for=self.win,
            heading="yt-dlp Was Not Found",
            body=(
                "Glypdl requires the yt-dlp command-line tool to download and process media.\n\n"
                f"Detected System: {distro_name}\n"
                f"Install Command:\n  {install_cmd}"
            )
        )
        dialog.add_response("ok", "OK")
        dialog.add_response("copy", "Copy Command")
        dialog.set_response_appearance("copy", Adw.ResponseAppearance.SUGGESTED)

        def _on_response(dlg, resp):
            if resp == "copy":
                display = Gdk.Display.get_default()
                if display:
                    clipboard = display.get_clipboard()
                    clipboard.set(install_cmd)

        dialog.connect("response", _on_response)
        dialog.present()

    def _get_distro_install_command(self) -> tuple[str, str]:
        distro_id = ""
        id_like = ""
        name = "Linux"
        if os.path.exists("/etc/os-release"):
            try:
                with open("/etc/os-release", "r") as f:
                    for line in f:
                        if line.startswith("ID="):
                            distro_id = line.strip().split("=", 1)[1].strip('"\'').lower()
                        elif line.startswith("ID_LIKE="):
                            id_like = line.strip().split("=", 1)[1].strip('"\'').lower()
                        elif line.startswith("NAME="):
                            name = line.strip().split("=", 1)[1].strip('"\'')
            except Exception:
                pass

        combined = f"{distro_id} {id_like}"
        if any(k in combined for k in ["arch", "manjaro", "endeavouros", "cachyos", "artix"]):
            return name, "sudo pacman -S yt-dlp ffmpeg"
        elif any(k in combined for k in ["ubuntu", "debian", "mint", "pop", "elementary", "zorin"]):
            return name, "sudo apt install yt-dlp ffmpeg"
        elif any(k in combined for k in ["fedora", "rhel", "centos", "rocky", "almalinux"]):
            return name, "sudo dnf install yt-dlp ffmpeg"
        elif any(k in combined for k in ["suse", "opensuse"]):
            return name, "sudo zypper install yt-dlp ffmpeg"
        elif "alpine" in combined:
            return name, "sudo apk add yt-dlp ffmpeg"
        elif "void" in combined:
            return name, "sudo xbps-install -S yt-dlp ffmpeg"
        else:
            return name, "sudo apt install yt-dlp ffmpeg"

    def _setup_actions(self):
        quit_action = Gio.SimpleAction.new("quit", None)
        quit_action.connect("activate", lambda a, p: self.quit())
        self.add_action(quit_action)

        about_action = Gio.SimpleAction.new("about", None)
        about_action.connect("activate", self._show_about)
        self.add_action(about_action)

        preferences_action = Gio.SimpleAction.new("preferences", None)
        preferences_action.connect("activate", self._show_preferences)
        self.add_action(preferences_action)

    def _setup_shortcuts(self):
        self.set_accels_for_action("app.quit", ["<Primary>q"])
        self.set_accels_for_action("app.preferences", ["<Primary>comma"])

    def _show_about(self, action, param):
        # GNOME standard about dialog using the official app icon
        about = Adw.AboutDialog(
            application_name="Glypdl",
            application_icon="io.github.sureshsoudararajan.Glypdl",
            developer_name="Suresh Soundararajan",
            version=__version__,
            comments="A lightweight native Linux graphical frontend for yt-dlp.",
            website="https://github.com/sureshsoudararajan/Glypdl",
            issue_url="https://github.com/sureshsoudararajan/Glypdl/issues",
            license_type=Gtk.License.GPL_3_0_ONLY,
            copyright="© 2026 Suresh Soundararajan"
        )
        about.add_acknowledgement_section("Powered by", ["yt-dlp", "GTK4", "libadwaita", "ffmpeg"])
        about.present(self.win)

    def _show_preferences(self, action, param):
        dialog = PreferencesDialog(self.settings, self.cookie_service)
        dialog.present(self.win)


def main():
    app = GlypdlApp()
    return app.run(sys.argv)


if __name__ == "__main__":
    sys.exit(main())
