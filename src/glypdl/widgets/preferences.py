"""Preferences window for configuring Glypdl with browser cookie and cookies.txt support."""

import os
import gi

gi.require_version('Gtk', '4.0')
gi.require_version('Adw', '1')
from gi.repository import Gtk, Adw, Gio, GLib, Pango

from glypdl.models.settings import Settings
from glypdl.services.cookies import CookieService, is_flatpak_environment
from glypdl.services.ytdlp import YtDlpService
from glypdl.utils.paths import get_default_download_dir


class PreferencesDialog(Adw.PreferencesDialog):
    """Preferences dialog conforming to GNOME Human Interface Guidelines."""
    __gtype_name__ = 'GlypdlPreferencesDialog'

    def __init__(self, settings: Settings, cookie_service: CookieService, ytdlp_service: YtDlpService = None, **kwargs):
        super().__init__(**kwargs)
        self.set_title("Preferences")
        self.settings = settings
        self.cookie_service = cookie_service
        self.ytdlp_service = ytdlp_service or YtDlpService(settings=settings)
        self._profile_rows = []
        self._discovered_browsers = []

        # ----------------------------------------------------
        # Page 1: General
        # ----------------------------------------------------
        general_page = Adw.PreferencesPage(
            title="General",
            icon_name="preferences-other-symbolic"
        )
        self.add(general_page)

        # Download Location Group
        dir_group = Adw.PreferencesGroup(title="Downloads Location")
        general_page.add(dir_group)

        current_dl_dir = self.settings.get('download_dir') or str(get_default_download_dir())
        self.dir_row = Adw.ActionRow(
            title="Default Download Folder",
            subtitle=current_dl_dir
        )
        choose_dir_btn = Gtk.Button(label="Choose Folder…", valign=Gtk.Align.CENTER)
        choose_dir_btn.connect('clicked', self._on_choose_folder)
        self.dir_row.add_suffix(choose_dir_btn)
        dir_group.add(self.dir_row)

        # Queue and Notifications Group
        queue_group = Adw.PreferencesGroup(title="Queue and Notifications")
        general_page.add(queue_group)

        # Max simultaneous downloads
        self.max_downloads_row = Adw.SpinRow.new_with_range(1, 10, 1)
        self.max_downloads_row.set_title("Maximum Simultaneous Downloads")
        self.max_downloads_row.set_value(self.settings.get('max_concurrent', 2))
        self.max_downloads_row.connect('notify::value', lambda row, pspec: self.settings.set('max_concurrent', int(row.get_value())))
        queue_group.add(self.max_downloads_row)

        # Notifications toggle
        self.notify_row = Adw.SwitchRow(
            title="Desktop Notifications",
            subtitle="Show notification when download completes or fails"
        )
        self.notify_row.set_active(self.settings.get('notifications', True))
        self.notify_row.connect('notify::active', lambda row, pspec: self.settings.set('notifications', row.get_active()))
        queue_group.add(self.notify_row)

        # ----------------------------------------------------
        # Page 2: Appearance
        # ----------------------------------------------------
        appearance_page = Adw.PreferencesPage(
            title="Appearance",
            icon_name="applications-graphics-symbolic"
        )
        self.add(appearance_page)

        theme_group = Adw.PreferencesGroup(title="Color Scheme")
        appearance_page.add(theme_group)

        self.theme_model = Gtk.StringList.new(["System", "Light", "Dark"])
        self.theme_dd = Gtk.DropDown(model=self.theme_model, valign=Gtk.Align.CENTER)
        self.theme_row = Adw.ActionRow(title="Application Theme")
        self.theme_row.add_suffix(self.theme_dd)

        current_theme = self.settings.get_color_scheme()
        if current_theme == "light":
            self.theme_dd.set_selected(1)
        elif current_theme == "dark":
            self.theme_dd.set_selected(2)
        else:
            self.theme_dd.set_selected(0)

        self.theme_dd.connect('notify::selected', self._on_theme_changed)
        theme_group.add(self.theme_row)

        # ----------------------------------------------------
        # Page 3: Downloads Formatting
        # ----------------------------------------------------
        dl_page = Adw.PreferencesPage(
            title="Downloads",
            icon_name="folder-download-symbolic"
        )
        self.add(dl_page)

        template_group = Adw.PreferencesGroup(
            title="Filename Pattern",
            description="yt-dlp output template pattern for saved files"
        )
        dl_page.add(template_group)

        self.template_row = Adw.EntryRow(title="Filename Template")
        self.template_row.set_text(self.settings.get('filename_template', '%(title)s.%(ext)s'))
        self.template_row.connect('notify::text', lambda row, pspec: self.settings.set('filename_template', row.get_text()))
        template_group.add(self.template_row)

        overwrite_group = Adw.PreferencesGroup(title="File Overwrite")
        dl_page.add(overwrite_group)

        self.overwrite_row = Adw.SwitchRow(
            title="Overwrite Existing Files",
            subtitle="Replace files with the same name if they already exist"
        )
        self.overwrite_row.set_active(self.settings.get('overwrite', False))
        self.overwrite_row.connect('notify::active', lambda row, pspec: self.settings.set('overwrite', row.get_active()))
        overwrite_group.add(self.overwrite_row)

        # ----------------------------------------------------
        # Page 4: Cookies (Direct Browser + cookies.txt)
        # ----------------------------------------------------
        cookies_page = Adw.PreferencesPage(
            title="Cookies",
            icon_name="security-high-symbolic"
        )
        self.add(cookies_page)

        # 1. Authentication Method Selector
        method_group = Adw.PreferencesGroup(
            title="Authentication Method",
            description="Select how Glypdl should authenticate for media requiring user login"
        )
        cookies_page.add(method_group)

        self.method_model = Gtk.StringList.new([
            "None (Disabled)",
            "Browser Cookies (Direct from Installed Browser)",
            "Cookie File (Netscape cookies.txt)"
        ])
        self.method_dd = Gtk.DropDown(model=self.method_model, valign=Gtk.Align.CENTER)
        self.method_row = Adw.ActionRow(title="Active Method")
        self.method_row.add_suffix(self.method_dd)
        method_group.add(self.method_row)

        # 2. Browser Cookies Group (visible when method == 'browser')
        self.browser_group = Adw.PreferencesGroup(
            title="Browser Configuration",
            description="yt-dlp will extract session cookies directly from your web browser"
        )
        cookies_page.add(self.browser_group)

        # Flatpak Sandbox Warning (if applicable)
        if is_flatpak_environment():
            self.flatpak_notice = Adw.ActionRow(
                title="Flatpak Sandbox Limitation",
                subtitle="Direct host browser cookies may be restricted by the Flatpak sandbox. If extraction fails, use a cookies.txt file."
            )
            use_txt_btn = Gtk.Button(label="Switch to Cookie File", valign=Gtk.Align.CENTER)
            use_txt_btn.connect('clicked', lambda _: self._select_cookie_method(2))
            self.flatpak_notice.add_suffix(use_txt_btn)
            self.browser_group.add(self.flatpak_notice)

        # Browser Dropdown Row
        self.browser_model = Gtk.StringList()
        self.browser_dd = Gtk.DropDown(model=self.browser_model, valign=Gtk.Align.CENTER)
        self.browser_row = Adw.ActionRow(title="Web Browser")
        self.browser_row.add_suffix(self.browser_dd)
        self.browser_group.add(self.browser_row)

        # Profile Dropdown Row
        self.profile_model = Gtk.StringList()
        self.profile_dd = Gtk.DropDown(model=self.profile_model, valign=Gtk.Align.CENTER)
        self.profile_row = Adw.ActionRow(title="Browser Profile")
        self.profile_row.add_suffix(self.profile_dd)
        self.browser_group.add(self.profile_row)

        # Decryption Keyring Row (Advanced)
        self.keyring_model = Gtk.StringList.new([k["name"] for k in self.cookie_service.get_supported_keyrings()])
        self.keyring_dd = Gtk.DropDown(model=self.keyring_model, valign=Gtk.Align.CENTER)
        self.keyring_row = Adw.ActionRow(
            title="Decryption Keyring",
            subtitle="Keyring used by Chromium-based browsers on Linux"
        )
        self.keyring_row.add_suffix(self.keyring_dd)
        self.browser_group.add(self.keyring_row)

        # Action Buttons Row (Test & Refresh)
        self.test_row = Adw.ActionRow(
            title="Test Connection",
            subtitle="Verify that yt-dlp can access cookies from this browser"
        )
        btn_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8, valign=Gtk.Align.CENTER)
        self.test_row.add_suffix(btn_box)

        self.refresh_btn = Gtk.Button(icon_name="view-refresh-symbolic", tooltip_text="Scan for newly installed browsers & profiles")
        self.refresh_btn.connect('clicked', lambda _: self._refresh_browsers(force=True))
        btn_box.append(self.refresh_btn)

        self.test_btn = Gtk.Button(label="Test Browser Cookies")
        self.test_btn.add_css_class("suggested-action")
        self.test_btn.connect('clicked', self._on_test_browser_cookies)
        btn_box.append(self.test_btn)

        self.browser_group.add(self.test_row)

        # Live Test Result Banner Row
        self.test_result_row = Adw.ActionRow(title="Ready to test")
        self.test_result_row.set_visible(False)
        self.browser_group.add(self.test_result_row)

        # Security Note Row
        security_note = Adw.ActionRow(
            title="🔒 Local Privacy Guarantee",
            subtitle="Glypdl never reads, stores, or transmits your cookies. Extraction and decryption are handled entirely by yt-dlp on your local system."
        )
        self.browser_group.add(security_note)

        # 3. Cookie File Group (visible when method == 'file')
        self.file_group = Adw.PreferencesGroup(
            title="Cookie File (cookies.txt)",
            description="Use a Netscape cookies.txt file exported from a browser extension"
        )
        cookies_page.add(self.file_group)

        self.cookie_file_row = Adw.ActionRow(
            title="Active Cookie File",
            subtitle=self.settings.get('cookie_file') or "None selected"
        )
        choose_cookie_btn = Gtk.Button(label="Choose File…", valign=Gtk.Align.CENTER)
        choose_cookie_btn.connect('clicked', self._on_choose_cookie_file)
        self.cookie_file_row.add_suffix(choose_cookie_btn)

        clear_cookie_btn = Gtk.Button(icon_name="edit-clear-symbolic", tooltip_text="Clear Active Cookie File", valign=Gtk.Align.CENTER)
        clear_cookie_btn.connect('clicked', self._on_clear_cookie_file)
        self.cookie_file_row.add_suffix(clear_cookie_btn)
        self.file_group.add(self.cookie_file_row)

        # Saved Profiles list
        self.profiles_group = Adw.PreferencesGroup(
            title="Saved Cookie Profiles",
            description="Save cookies.txt files for different websites and click 'Use Profile' to switch"
        )
        cookies_page.add(self.profiles_group)
        self._populate_profiles()

        # Connect Cookie Method & Browser Signals
        self._init_cookie_ui_state()

        # ----------------------------------------------------
        # Page 5: Advanced
        # ----------------------------------------------------
        advanced_page = Adw.PreferencesPage(
            title="Advanced",
            icon_name="applications-system-symbolic"
        )
        self.add(advanced_page)

        binaries_group = Adw.PreferencesGroup(
            title="Custom Executables",
            description="Leave blank to use system-installed binaries"
        )
        advanced_page.add(binaries_group)

        self.ytdlp_row = Adw.EntryRow(title="yt-dlp Path")
        self.ytdlp_row.set_text(self.settings.get('ytdlp_path', ''))
        self.ytdlp_row.connect('notify::text', lambda row, pspec: self.settings.set('ytdlp_path', row.get_text().strip()))
        binaries_group.add(self.ytdlp_row)

        self.ffmpeg_row = Adw.EntryRow(title="ffmpeg Path")
        self.ffmpeg_row.set_text(self.settings.get('ffmpeg_path', ''))
        self.ffmpeg_row.connect('notify::text', lambda row, pspec: self.settings.set('ffmpeg_path', row.get_text().strip()))
        binaries_group.add(self.ffmpeg_row)

        extra_group = Adw.PreferencesGroup(title="Additional yt-dlp Arguments")
        advanced_page.add(extra_group)

        self.extra_args_row = Adw.EntryRow(title="Extra CLI Arguments")
        self.extra_args_row.set_text(self.settings.get('extra_args', ''))
        self.extra_args_row.connect('notify::text', lambda row, pspec: self.settings.set('extra_args', row.get_text()))
        extra_group.add(self.extra_args_row)

    # =========================================================================
    # Cookie UI Logic & Signal Handlers
    # =========================================================================

    def _init_cookie_ui_state(self):
        """Initialize dropdown selections and visibility from settings."""
        # 1. Method
        method = self.settings.get_cookie_method()
        if method == "browser":
            self.method_dd.set_selected(1)
        elif method == "file":
            self.method_dd.set_selected(2)
        else:
            self.method_dd.set_selected(0)

        self._update_cookie_sections_visibility(self.method_dd.get_selected())
        self.method_dd.connect('notify::selected', self._on_method_changed)

        # 2. Keyring
        keyring = self.settings.get_browser_keyring()
        keyrings = self.cookie_service.get_supported_keyrings()
        for idx, k in enumerate(keyrings):
            if k["id"] == keyring:
                self.keyring_dd.set_selected(idx)
                break
        self.keyring_dd.connect('notify::selected', self._on_keyring_changed)

        # 3. Discovered Browsers & Profiles
        self._refresh_browsers(force=False)
        self.browser_dd.connect('notify::selected', self._on_browser_changed)
        self.profile_dd.connect('notify::selected', self._on_profile_changed)

    def _select_cookie_method(self, index: int):
        self.method_dd.set_selected(index)

    def _on_method_changed(self, dropdown, pspec):
        idx = dropdown.get_selected()
        self._update_cookie_sections_visibility(idx)
        if idx == 1:
            self.settings.set_cookie_method('browser')
        elif idx == 2:
            self.settings.set_cookie_method('file')
        else:
            self.settings.set_cookie_method('none')

    def _update_cookie_sections_visibility(self, method_idx: int):
        self.browser_group.set_visible(method_idx == 1)
        self.file_group.set_visible(method_idx == 2)
        self.profiles_group.set_visible(method_idx == 2)

    def _refresh_browsers(self, force: bool = False):
        """Populate browser and profile dropdowns based on discovery."""
        self._discovered_browsers = self.cookie_service.discover_installed_browsers(force_refresh=force)
        
        # Rebuild browser StringList model
        while self.browser_model.get_n_items() > 0:
            self.browser_model.remove(0)

        selected_browser_id = self.settings.get_browser_name()
        selected_browser_idx = 0

        for idx, b in enumerate(self._discovered_browsers):
            status_suffix = " (Installed)" if b.get("is_installed") else ""
            self.browser_model.append(f"{b['name']}{status_suffix}")
            if b["id"] == selected_browser_id:
                selected_browser_idx = idx

        self.browser_dd.set_selected(selected_browser_idx)
        self._update_profile_dropdown(selected_browser_idx)

    def _on_browser_changed(self, dropdown, pspec):
        idx = dropdown.get_selected()
        if 0 <= idx < len(self._discovered_browsers):
            b_info = self._discovered_browsers[idx]
            self.settings.set('browser_name', b_info["id"])
            self._update_profile_dropdown(idx)

    def _update_profile_dropdown(self, browser_idx: int):
        if not (0 <= browser_idx < len(self._discovered_browsers)):
            return

        b_info = self._discovered_browsers[browser_idx]
        profiles = b_info.get("profiles", ["Default"])

        while self.profile_model.get_n_items() > 0:
            self.profile_model.remove(0)

        saved_profile = self.settings.get_browser_profile()
        selected_prof_idx = 0

        for idx, p in enumerate(profiles):
            self.profile_model.append(p)
            if p == saved_profile:
                selected_prof_idx = idx

        self.profile_dd.set_selected(selected_prof_idx)

    def _on_profile_changed(self, dropdown, pspec):
        idx = dropdown.get_selected()
        b_idx = self.browser_dd.get_selected()
        if 0 <= b_idx < len(self._discovered_browsers):
            profiles = self._discovered_browsers[b_idx].get("profiles", ["Default"])
            if 0 <= idx < len(profiles):
                self.settings.set('browser_profile', profiles[idx])

    def _on_keyring_changed(self, dropdown, pspec):
        idx = dropdown.get_selected()
        keyrings = self.cookie_service.get_supported_keyrings()
        if 0 <= idx < len(keyrings):
            self.settings.set('browser_keyring', keyrings[idx]["id"])

    def _on_test_browser_cookies(self, btn):
        """Run an asynchronous verification check using yt-dlp."""
        b_name = self.settings.get_browser_name()
        profile = self.settings.get_browser_profile()
        keyring = self.settings.get_browser_keyring()
        spec = self.cookie_service.build_browser_spec(b_name, profile=profile, keyring=keyring)

        if not spec:
            self.test_result_row.set_visible(True)
            self.test_result_row.set_title("⚠ Invalid Configuration")
            self.test_result_row.set_subtitle("Please select a valid web browser.")
            return

        self.test_btn.set_sensitive(False)
        self.test_result_row.set_visible(True)
        self.test_result_row.set_title("Testing cookie extraction…")
        self.test_result_row.set_subtitle(f"Running check for '{spec}'…")

        def _on_test_done(success: bool, msg: str, details: str):
            self.test_btn.set_sensitive(True)
            if success:
                self.test_result_row.set_title("✓ Cookies Accessible")
                self.test_result_row.set_subtitle(msg)
            else:
                self.test_result_row.set_title("⚠ Cookie Extraction Issue")
                self.test_result_row.set_subtitle(msg)

        self.cookie_service.test_browser_cookies_async(self.ytdlp_service, spec, _on_test_done)

    # =========================================================================
    # General Preferences & Cookie File Profiles
    # =========================================================================

    def _on_theme_changed(self, dropdown, pspec):
        selected = dropdown.get_selected()
        style_mgr = Adw.StyleManager.get_default()
        if selected == 1:
            self.settings.set_color_scheme("light")
            style_mgr.set_color_scheme(Adw.ColorScheme.FORCE_LIGHT)
        elif selected == 2:
            self.settings.set_color_scheme("dark")
            style_mgr.set_color_scheme(Adw.ColorScheme.FORCE_DARK)
        else:
            self.settings.set_color_scheme("system")
            style_mgr.set_color_scheme(Adw.ColorScheme.DEFAULT)

    def _on_choose_folder(self, *args):
        dialog = Gtk.FileDialog.new()
        dialog.set_title("Select Download Directory")
        dialog.select_folder(self.get_root(), None, self._on_folder_dialog_response)

    def _on_folder_dialog_response(self, dialog, result):
        try:
            folder = dialog.select_folder_finish(result)
            if folder:
                path_str = folder.get_path()
                self.settings.set('download_dir', path_str)
                self.dir_row.set_subtitle(path_str)
        except Exception:
            pass

    def _on_choose_cookie_file(self, *args):
        dialog = Gtk.FileDialog.new()
        dialog.set_title("Select Netscape Cookie File")
        dialog.open(self.get_root(), None, self._on_cookie_dialog_response)

    def _on_cookie_dialog_response(self, dialog, result):
        try:
            gfile = dialog.open_finish(result)
            if gfile:
                path_str = gfile.get_path()
                self.settings.set('cookie_file', path_str)
                self.settings.set_cookie_method('file')
                self.method_dd.set_selected(2)
                self.cookie_file_row.set_subtitle(path_str)
                self._populate_profiles()
        except Exception:
            pass

    def _on_clear_cookie_file(self, *args):
        self.settings.set('cookie_file', '')
        self.cookie_file_row.set_subtitle("None selected")
        self._populate_profiles()

    def _populate_profiles(self):
        for r in self._profile_rows:
            self.profiles_group.remove(r)
        self._profile_rows = []

        profiles = self.cookie_service.get_profiles() if self.cookie_service else []
        active_cookie = self.settings.get('cookie_file', '')
        method = self.settings.get_cookie_method()

        if not profiles:
            empty_row = Adw.ActionRow(
                title="No Saved Profiles",
                subtitle="Save cookie profiles for different websites to quickly authenticate"
            )
            add_btn = Gtk.Button(label="Add Profile…", valign=Gtk.Align.CENTER)
            add_btn.connect('clicked', self._on_add_profile_clicked)
            empty_row.add_suffix(add_btn)
            self.profiles_group.add(empty_row)
            self._profile_rows.append(empty_row)
        else:
            for p in profiles:
                p_name = p.get('name', 'Profile')
                p_path = p.get('path', '')
                is_active = (p_path == active_cookie and method == 'file')

                row = Adw.ActionRow(title=p_name, subtitle=p_path)

                btn_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6, valign=Gtk.Align.CENTER)
                row.add_suffix(btn_box)

                if is_active:
                    active_badge = Gtk.Button(label="Active", sensitive=False)
                    active_badge.add_css_class("suggested-action")
                    btn_box.append(active_badge)
                else:
                    use_btn = Gtk.Button(label="Use Profile", tooltip_text="Switch to this cookie profile")
                    use_btn.connect('clicked', lambda btn, path=p_path: self._set_active_cookie_profile(path))
                    btn_box.append(use_btn)

                del_btn = Gtk.Button(icon_name="user-trash-symbolic", tooltip_text="Delete Profile")
                del_btn.add_css_class("destructive-action")
                del_btn.connect('clicked', lambda btn, name=p_name, r=row: self._delete_profile(name, r))
                btn_box.append(del_btn)

                self.profiles_group.add(row)
                self._profile_rows.append(row)

            # Add another profile row
            add_row = Adw.ActionRow(title="Add Profile")
            add_btn = Gtk.Button(label="Add Profile…", valign=Gtk.Align.CENTER)
            add_btn.connect('clicked', self._on_add_profile_clicked)
            add_row.add_suffix(add_btn)
            self.profiles_group.add(add_row)
            self._profile_rows.append(add_row)

    def _set_active_cookie_profile(self, path: str):
        self.settings.set('cookie_file', path)
        self.settings.set_cookie_method('file')
        self.method_dd.set_selected(2)
        self.cookie_file_row.set_subtitle(path)
        self._populate_profiles()

    def _on_add_profile_clicked(self, *args):
        dialog = Gtk.FileDialog.new()
        dialog.set_title("Select Netscape Cookie File for Profile")
        dialog.open(self.get_root(), None, self._on_add_profile_dialog_response)

    def _on_add_profile_dialog_response(self, dialog, result):
        try:
            gfile = dialog.open_finish(result)
            if gfile:
                path_str = gfile.get_path()
                default_name = os.path.splitext(os.path.basename(path_str))[0].capitalize()

                entry = Gtk.Entry(text=default_name)
                entry.set_margin_top(8)
                entry.set_margin_bottom(8)

                msg_dialog = Adw.MessageDialog(
                    transient_for=self.get_root(),
                    heading="Name Cookie Profile",
                    body=f"Enter a profile name for {path_str}:"
                )
                msg_dialog.set_extra_child(entry)
                msg_dialog.add_response("cancel", "Cancel")
                msg_dialog.add_response("add", "Add Profile")
                msg_dialog.set_response_appearance("add", Adw.ResponseAppearance.SUGGESTED)

                def _on_name_response(dlg, resp):
                    if resp == "add":
                        p_name = entry.get_text().strip() or default_name
                        if self.cookie_service:
                            self.cookie_service.add_profile(p_name, path_str)
                            self._populate_profiles()

                msg_dialog.connect("response", _on_name_response)
                msg_dialog.present()
        except Exception:
            pass

    def _delete_profile(self, name: str, row: Adw.ActionRow):
        if self.cookie_service:
            self.cookie_service.remove_profile(name)
        if row in self._profile_rows:
            self._profile_rows.remove(row)
        self.profiles_group.remove(row)
        if not self.cookie_service or not self.cookie_service.get_profiles():
            self._populate_profiles()
