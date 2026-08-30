"""Preferences window for configuring Glypdl."""

import os
import gi

gi.require_version('Gtk', '4.0')
gi.require_version('Adw', '1')
from gi.repository import Gtk, Adw, Gio, GLib

from glypdl.models.settings import Settings
from glypdl.services.cookies import CookieService
from glypdl.utils.paths import get_default_download_dir


class PreferencesDialog(Adw.PreferencesDialog):
    """Preferences dialog conforming to GNOME HIG."""
    __gtype_name__ = 'GlypdlPreferencesDialog'

    def __init__(self, settings: Settings, cookie_service: CookieService, **kwargs):
        super().__init__(**kwargs)
        self.set_title("Preferences")
        self.settings = settings
        self.cookie_service = cookie_service
        self._profile_rows = []

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
        # Page 4: Cookies
        # ----------------------------------------------------
        cookies_page = Adw.PreferencesPage(
            title="Cookies",
            icon_name="security-high-symbolic"
        )
        self.add(cookies_page)

        cookie_group = Adw.PreferencesGroup(
            title="Authentication Cookies",
            description="Use a Netscape cookies.txt file for sites requiring login"
        )
        cookies_page.add(cookie_group)

        self.use_cookies_row = Adw.SwitchRow(title="Enable Cookies")
        self.use_cookies_row.set_active(self.settings.get('use_cookies', False))
        self.use_cookies_row.connect('notify::active', self._on_use_cookies_toggled)
        cookie_group.add(self.use_cookies_row)

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
        cookie_group.add(self.cookie_file_row)

        # Saved Profiles list
        self.profiles_group = Adw.PreferencesGroup(
            title="Saved Cookie Profiles",
            description="Save cookies.txt files for different websites and click 'Use Profile' to switch"
        )
        cookies_page.add(self.profiles_group)
        self._populate_profiles()

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

    def _on_use_cookies_toggled(self, row, pspec):
        self.settings.set('use_cookies', row.get_active())
        self._populate_profiles()

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
        use_cookies = self.settings.get('use_cookies', False)
        
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
                is_active = (p_path == active_cookie and use_cookies)

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
        self.settings.set('use_cookies', True)
        self.use_cookies_row.set_active(True)
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
