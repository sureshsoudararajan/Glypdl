"""Main application window for Glypdl."""

import os
import gi

gi.require_version('Gtk', '4.0')
gi.require_version('Adw', '1')
from gi.repository import Gtk, Adw, Gio, GLib, Gdk, Pango

from glypdl.models.download import DownloadItem, DownloadState, DownloadMode
from glypdl.widgets.url_input import UrlInput
from glypdl.widgets.download_card import DownloadCard
from glypdl.widgets.progress_card import MetadataPreviewCard
from glypdl.widgets.playlist_card import PlaylistPreviewCard
from glypdl.widgets.preferences import PreferencesDialog
from glypdl.utils.formatting import format_size
from glypdl.utils.paths import get_default_download_dir
from glypdl.utils.thumbnails import load_thumbnail_async, load_scaled_texture


class GlypdlWindow(Adw.ApplicationWindow):
    """Main window hosting Downloads, History, and Navigation."""
    __gtype_name__ = 'GlypdlWindow'

    def __init__(self, app):
        super().__init__(application=app, title="Glypdl", default_width=920, default_height=720)
        self.app = app
        self.downloads = []  # list[DownloadItem]
        self._cards = {}     # dict[str, DownloadCard]

        self._build_ui()
        self._setup_actions()
        self._load_history()

    def _build_ui(self):
        main_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
        self.set_content(main_box)

        # HeaderBar
        self.header_bar = Adw.HeaderBar()
        main_box.append(self.header_bar)

        # Centered ViewSwitcher title
        self.view_switcher_title = Adw.ViewSwitcherTitle(title="Glypdl")
        self.header_bar.set_title_widget(self.view_switcher_title)

        # Header bar actions
        settings_button = Gtk.Button(icon_name="emblem-system-symbolic", tooltip_text="Preferences")
        settings_button.set_action_name("app.preferences")
        self.header_bar.pack_end(settings_button)

        menu = Gio.Menu()
        menu.append("Preferences", "app.preferences")
        menu.append("About Glypdl", "app.about")
        menu.append("Quit", "app.quit")

        menu_button = Gtk.MenuButton(icon_name="open-menu-symbolic", menu_model=menu, tooltip_text="Main Menu")
        self.header_bar.pack_end(menu_button)

        # ViewStack for pages
        self.view_stack = Adw.ViewStack()
        self.view_stack.set_vexpand(True)
        main_box.append(self.view_stack)

        self.view_switcher_title.set_stack(self.view_stack)

        # ViewSwitcherBar at the bottom for responsive screens
        self.switcher_bar = Adw.ViewSwitcherBar(stack=self.view_stack)
        main_box.append(self.switcher_bar)

        # Add Downloads page
        self.downloads_page = self._build_downloads_page()
        self.view_stack.add_titled_with_icon(
            self.downloads_page,
            "downloads",
            "Downloads",
            "folder-download-symbolic"
        )

        # Add History page
        self.history_page = self._build_history_page()
        self.view_stack.add_titled_with_icon(
            self.history_page,
            "history",
            "History",
            "document-open-recent-symbolic"
        )

    def _build_downloads_page(self) -> Gtk.Widget:
        scrolled = Gtk.ScrolledWindow()
        scrolled.set_vexpand(True)
        scrolled.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        
        clamp = Adw.Clamp(maximum_size=780)
        clamp.set_margin_top(16)
        clamp.set_margin_bottom(16)
        clamp.set_margin_start(16)
        clamp.set_margin_end(16)
        scrolled.set_child(clamp)

        content_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=14)
        content_box.set_hexpand(True)
        clamp.set_child(content_box)

        # Top URL Input Bar
        self.url_input = UrlInput()
        self.url_input.connect('url-submitted', self._on_url_submitted)
        content_box.append(self.url_input)

        # Metadata Preview Container
        self.preview_container = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=8)
        self.preview_container.set_hexpand(True)
        content_box.append(self.preview_container)

        # Fetching Spinner & Status
        self.fetch_spinner_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=10, halign=Gtk.Align.CENTER)
        self.fetch_spinner_box.set_margin_top(8)
        self.fetch_spinner_box.set_margin_bottom(8)
        self.fetch_spinner = Gtk.Spinner()
        self.fetch_status_label = Gtk.Label(label="Retrieving media information…")
        self.fetch_spinner_box.append(self.fetch_spinner)
        self.fetch_spinner_box.append(self.fetch_status_label)
        self.fetch_spinner_box.set_visible(False)
        self.preview_container.append(self.fetch_spinner_box)

        content_box.append(Gtk.Separator(orientation=Gtk.Orientation.HORIZONTAL))

        # Active Downloads Section
        self.active_heading = Gtk.Label(label="Active Downloads", halign=Gtk.Align.START)
        self.active_heading.add_css_class("heading")
        self.active_heading.set_margin_top(6)
        content_box.append(self.active_heading)

        self.downloads_list = Gtk.ListBox()
        self.downloads_list.add_css_class("boxed-list")
        self.downloads_list.set_hexpand(True)
        self.downloads_list.set_selection_mode(Gtk.SelectionMode.NONE)
        content_box.append(self.downloads_list)

        # Queued Downloads Section
        self.queued_heading = Gtk.Label(label="Queued Downloads", halign=Gtk.Align.START)
        self.queued_heading.add_css_class("heading")
        self.queued_heading.set_margin_top(12)
        content_box.append(self.queued_heading)

        self.queued_list = Gtk.ListBox()
        self.queued_list.add_css_class("boxed-list")
        self.queued_list.set_hexpand(True)
        self.queued_list.set_selection_mode(Gtk.SelectionMode.NONE)
        content_box.append(self.queued_list)

        # Finished Section Header with Clear button
        self.completed_header_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        self.completed_header_box.set_margin_top(12)
        content_box.append(self.completed_header_box)

        self.completed_heading = Gtk.Label(label="Finished Downloads", halign=Gtk.Align.START)
        self.completed_heading.add_css_class("heading")
        self.completed_heading.set_hexpand(True)
        self.completed_header_box.append(self.completed_heading)

        self.clear_completed_btn = Gtk.Button(label="Clear Finished", valign=Gtk.Align.CENTER)
        self.clear_completed_btn.connect('clicked', self._on_clear_completed_clicked)
        self.completed_header_box.append(self.clear_completed_btn)

        # Finished downloads list wrapped in a scrollable view with max height for 4-5 items
        self.completed_scrolled = Gtk.ScrolledWindow()
        self.completed_scrolled.set_propagate_natural_height(True)
        self.completed_scrolled.set_max_content_height(280)
        self.completed_scrolled.set_min_content_height(75)
        self.completed_scrolled.set_hexpand(True)
        self.completed_scrolled.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)

        self.completed_list = Gtk.ListBox()
        self.completed_list.add_css_class("boxed-list")
        self.completed_list.set_hexpand(True)
        self.completed_list.set_selection_mode(Gtk.SelectionMode.NONE)
        self.completed_scrolled.set_child(self.completed_list)
        content_box.append(self.completed_scrolled)

        # Empty State
        self.empty_downloads_status = Adw.StatusPage(
            title="No Downloads",
            description="Paste a video or playlist URL above to start downloading",
            icon_name="folder-download-symbolic"
        )
        content_box.append(self.empty_downloads_status)

        self._update_downloads_ui()
        return scrolled

    def _build_history_page(self) -> Gtk.Widget:
        page_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
        page_box.set_vexpand(True)

        # History Toolbar
        toolbar = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=10)
        toolbar.set_margin_top(12)
        toolbar.set_margin_bottom(8)
        toolbar.set_margin_start(16)
        toolbar.set_margin_end(16)
        page_box.append(toolbar)

        self.history_search = Gtk.SearchEntry(hexpand=True)
        self.history_search.set_placeholder_text("Search download history…")
        self.history_search.connect('search-changed', self._on_history_search_changed)
        toolbar.append(self.history_search)

        clear_btn = Gtk.Button(label="Clear All", icon_name="edit-clear-all-symbolic")
        clear_btn.connect('clicked', self._on_clear_history_clicked)
        toolbar.append(clear_btn)

        # Scrolled History List - configured for smooth scrolling of large histories
        scrolled = Gtk.ScrolledWindow()
        scrolled.set_vexpand(True)
        scrolled.set_hexpand(True)
        scrolled.set_min_content_height(350)
        scrolled.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        page_box.append(scrolled)

        clamp = Adw.Clamp(maximum_size=780)
        clamp.set_margin_top(8)
        clamp.set_margin_bottom(16)
        clamp.set_margin_start(16)
        clamp.set_margin_end(16)
        scrolled.set_child(clamp)

        history_content = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=12)
        clamp.set_child(history_content)

        self.history_list = Gtk.ListBox()
        self.history_list.add_css_class("boxed-list")
        self.history_list.set_selection_mode(Gtk.SelectionMode.NONE)
        history_content.append(self.history_list)

        self.empty_history_status = Adw.StatusPage(
            title="No Download History",
            description="Completed and past downloads will appear here",
            icon_name="document-open-recent-symbolic"
        )
        history_content.append(self.empty_history_status)

        return page_box

    def _setup_actions(self):
        focus_url_action = Gio.SimpleAction.new("focus-url", None)
        focus_url_action.connect("activate", lambda a, p: self._focus_url_input())
        self.add_action(focus_url_action)
        self.app.set_accels_for_action("win.focus-url", ["<Primary>l"])

        show_history_action = Gio.SimpleAction.new("show-history", None)
        show_history_action.connect("activate", lambda a, p: self.view_stack.set_visible_child_name("history"))
        self.add_action(show_history_action)
        self.app.set_accels_for_action("win.show-history", ["<Primary>h"])

        paste_url_action = Gio.SimpleAction.new("paste-url", None)
        paste_url_action.connect("activate", self._on_paste_url_action)
        self.add_action(paste_url_action)
        self.app.set_accels_for_action("win.paste-url", ["<Primary>v"])

        escape_action = Gio.SimpleAction.new("escape", None)
        escape_action.connect("activate", lambda a, p: self._dismiss_preview())
        self.add_action(escape_action)
        self.app.set_accels_for_action("win.escape", ["Escape"])

    def _focus_url_input(self):
        self.view_stack.set_visible_child_name("downloads")
        self.url_input.grab_focus()

    def _on_paste_url_action(self, action, param):
        self.view_stack.set_visible_child_name("downloads")
        clipboard = self.get_display().get_clipboard()
        clipboard.read_text_async(None, self._on_clipboard_text_read)

    def _on_clipboard_text_read(self, clipboard, result):
        try:
            text = clipboard.read_text_finish(result)
            if text and (text.startswith("http://") or text.startswith("https://")):
                self.url_input.set_text(text)
                self._on_url_submitted(self.url_input, text)
        except Exception:
            pass

    def handle_external_url(self, url: str, job_dict: dict = None):
        """Handle a download request sent externally from the browser extension."""
        if not url:
            return
        self.set_visible(True)
        self.unminimize()
        self.present()
        self._external_job_context = job_dict or {}

        # Handle temporary site cookies if sent via "Using Cookie Download"
        cookie_override = None
        browser_override = None
        if job_dict and (job_dict.get('is_temp_cookie') or job_dict.get('cookies_txt')):
            cookies_txt = job_dict.get('cookies_txt', '')
            if cookies_txt:
                try:
                    import tempfile
                    fd, temp_path = tempfile.mkstemp(prefix='glypdl_cookie_', suffix='.txt')
                    with os.fdopen(fd, 'w', encoding='utf-8') as f:
                        f.write(cookies_txt)
                    cookie_override = temp_path
                    browser_override = ""  # Explicitly override and bypass saved browser cookie settings
                    self._current_temp_cookie = temp_path
                except Exception:
                    cookie_override = None

        self.view_stack.set_visible_child_name("downloads")
        self.url_input.entry.set_text(url)
        self._on_url_submitted(
            self.url_input,
            url,
            cookie_override=cookie_override,
            browser_override=browser_override
        )

    def _on_url_submitted(self, widget, url: str, cookie_override: str = None, browser_override: str = None):
        preserve_temp = bool(cookie_override and cookie_override == getattr(self, '_current_temp_cookie', None))
        self._dismiss_preview(preserve_temp_cookie=preserve_temp)
        self.fetch_spinner_box.set_visible(True)
        self.fetch_spinner.start()

        cookie_file = cookie_override
        cookies_from_browser = browser_override
        if cookie_file is None and cookies_from_browser is None:
            method = self.app.settings.get_cookie_method()
            if method == 'browser':
                b_name = self.app.settings.get_browser_name()
                b_prof = self.app.settings.get_browser_profile()
                b_key = self.app.settings.get_browser_keyring()
                if self.app.cookie_service:
                    cookies_from_browser = self.app.cookie_service.build_browser_spec(b_name, profile=b_prof, keyring=b_key)
            elif method == 'file':
                cookie_file = self.app.settings.get('cookie_file', '')

        self.app.metadata_service.fetch_async(
            url,
            callback=self._on_metadata_fetched,
            error_callback=lambda err, target_url=url: self._on_metadata_error(err, target_url),
            cookie_file=cookie_file,
            cookies_from_browser=cookies_from_browser
        )

    def _on_metadata_fetched(self, metadata: dict):
        self.fetch_spinner.stop()
        self.fetch_spinner_box.set_visible(False)

        if not metadata:
            return

        # Merge rich metadata sent from browser extension if available
        if hasattr(self, '_external_job_context') and self._external_job_context:
            ext_thumb = self._external_job_context.get('thumbnailUrl') or (self._external_job_context.get('source') or {}).get('thumbnailUrl')
            if ext_thumb and (not metadata.get('thumbnail') or not metadata.get('thumbnail_path')):
                metadata['thumbnail'] = ext_thumb

            ext_title = self._external_job_context.get('title') or (self._external_job_context.get('source') or {}).get('title')
            curr_title = metadata.get('title', '')
            if ext_title and (not curr_title or curr_title.lower() in ['master', 'index', 'playlist', 'stream', 'video', 'untitled media']):
                metadata['title'] = ext_title

            ext_dur = self._external_job_context.get('duration') or (self._external_job_context.get('source') or {}).get('duration')
            if ext_dur and (not metadata.get('duration') or metadata.get('duration') == 0):
                metadata['duration'] = ext_dur

        used_cookie_file = metadata.get('used_cookie_file', '')
        used_browser_spec = metadata.get('used_cookies_from_browser', '')
        cookie_method = self.app.settings.get_cookie_method()
        default_cookie = self.app.settings.get('cookie_file', '')
        
        if not used_browser_spec and cookie_method == 'browser':
            b_name = self.app.settings.get_browser_name()
            b_prof = self.app.settings.get_browser_profile()
            b_key = self.app.settings.get_browser_keyring()
            if self.app.cookie_service:
                used_browser_spec = self.app.cookie_service.build_browser_spec(b_name, profile=b_prof, keyring=b_key)

        installed_browsers = self.app.cookie_service.discover_installed_browsers() if self.app.cookie_service else []
        profiles = self.app.cookie_service.get_profiles() if self.app.cookie_service else []

        # Check if returned metadata represents a YouTube playlist or multi-item album
        if metadata.get('_type') == 'playlist' or 'entries' in metadata:
            preview = PlaylistPreviewCard(playlist_data=metadata)
            preview.format_selector.set_cookie_profiles(
                profiles=profiles,
                default_cookie_path=default_cookie,
                active_cookie_path=used_cookie_file,
                cookie_method=cookie_method,
                active_browser_spec=used_browser_spec,
                installed_browsers=installed_browsers,
                use_cookies=self.app.settings.get('use_cookies', False)
            )
            preview.connect('download-playlist-requested', self._on_download_playlist_requested)
            preview.connect('cancel-preview', lambda _: self._dismiss_preview())
            self.preview_container.append(preview)
        else:
            preview = MetadataPreviewCard(metadata=metadata)
            preview.format_selector.set_cookie_profiles(
                profiles=profiles,
                default_cookie_path=default_cookie,
                active_cookie_path=used_cookie_file,
                cookie_method=cookie_method,
                active_browser_spec=used_browser_spec,
                installed_browsers=installed_browsers,
                use_cookies=self.app.settings.get('use_cookies', False)
            )
            preview.connect('download-requested', self._on_download_requested)
            preview.connect('cancel-preview', lambda _: self._dismiss_preview())
            self.preview_container.append(preview)
            self.present()

    def _on_metadata_error(self, error_msg: str, url: str = ""):
        self.fetch_spinner.stop()
        self.fetch_spinner_box.set_visible(False)

        # Discard any temporary cookie file if metadata fetching failed
        if hasattr(self, '_current_temp_cookie') and self._current_temp_cookie:
            try:
                if os.path.isfile(self._current_temp_cookie):
                    os.unlink(self._current_temp_cookie)
            except Exception:
                pass
            self._current_temp_cookie = None

        # Check if authentication / cookies are likely required
        auth_keywords = ['sign in', 'login', 'cookie', 'bot', 'confirm', 'private', 'members', '403', 'forbidden', 'authenticate', 'permission', 'unauthorized', 'account', 'secretstorage', 'unreachable']
        is_auth_error = any(k in error_msg.lower() for k in auth_keywords)

        profiles = self.app.cookie_service.get_profiles() if self.app.cookie_service else []
        default_cookie = self.app.settings.get('cookie_file', '')
        installed_browsers = self.app.cookie_service.discover_installed_browsers() if self.app.cookie_service else []

        dialog = Adw.MessageDialog(
            transient_for=self,
            heading="Authentication / Cookies Required" if is_auth_error else "Could Not Fetch Media Information",
            body=(
                f"This video or site requires user authentication or cookies:\n\n{error_msg[:300]}\n\nSelect a browser or cookie file to retry:"
                if is_auth_error else
                f"yt-dlp reported an issue:\n\n{error_msg[:300]}"
            )
        )

        container = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=8)
        container.set_margin_top(8)
        container.set_margin_bottom(8)

        auth_options = []
        labels = []

        # 1. Add detected installed browsers
        for b in installed_browsers:
            if b.get("is_installed"):
                b_id = b.get("id")
                b_name = b.get("name")
                for prof in b.get("profiles", ["Default"]):
                    spec = self.app.cookie_service.build_browser_spec(b_id, profile=prof)
                    prof_disp = f" ({prof})" if prof else ""
                    opt_label = f"🌐 Browser: {b_name}{prof_disp}"
                    auth_options.append({"type": "browser", "spec": spec})
                    labels.append(opt_label)

        # 2. Add custom browser selector option
        auth_options.append({"type": "custom_browser"})
        labels.append("🌐 Select Another Browser / Profile…")

        # 3. Add default active cookie file if set
        if default_cookie and os.path.isfile(default_cookie):
            auth_options.append({"type": "file", "path": default_cookie})
            labels.append(f"📄 Default Cookie File ({os.path.basename(default_cookie)})")

        # 4. Add saved cookie profiles
        for p in profiles:
            p_name = p.get('name', 'Profile')
            p_path = p.get('path', '')
            if p_path and os.path.isfile(p_path):
                auth_options.append({"type": "file", "path": p_path})
                labels.append(f"📄 Saved File: {p_name} ({os.path.basename(p_path)})")

        # 5. Add browse for file
        auth_options.append({"type": "browse"})
        labels.append("📁 Browse for Netscape cookies.txt file…")

        lbl = Gtk.Label(label="Select Authentication Method to retry:", halign=Gtk.Align.START)
        lbl.add_css_class("dim-label")
        container.append(lbl)

        combo_model = Gtk.StringList.new(labels)
        dropdown = Gtk.DropDown(model=combo_model, hexpand=True)
        container.append(dropdown)

        dialog.set_extra_child(container)
        dialog.add_response("cancel", "Cancel")
        dialog.add_response("retry", "Authenticate & Retry")
        dialog.set_response_appearance("retry", Adw.ResponseAppearance.SUGGESTED)

        def _on_error_dialog_response(dlg, resp):
            if resp == "retry":
                sel_idx = dropdown.get_selected()
                if 0 <= sel_idx < len(auth_options):
                    chosen = auth_options[sel_idx]
                    c_type = chosen.get("type")
                    if c_type == "browser":
                        self._on_url_submitted(self.url_input, url, browser_override=chosen.get("spec"))
                    elif c_type == "custom_browser":
                        self._show_browser_picker_and_retry(url)
                    elif c_type == "file":
                        self._on_url_submitted(self.url_input, url, cookie_override=chosen.get("path"))
                    elif c_type == "browse":
                        self._browse_and_retry_metadata(url)

        dialog.connect("response", _on_error_dialog_response)
        dialog.present()

    def _show_browser_picker_and_retry(self, url: str):
        """Show a quick browser selection popup and retry metadata fetching."""
        installed = self.app.cookie_service.discover_installed_browsers() if self.app.cookie_service else []
        if not installed:
            self._browse_and_retry_metadata(url)
            return

        dialog = Adw.MessageDialog(
            transient_for=self,
            heading="Select Browser for Cookies",
            body="Choose the web browser to extract session cookies from:"
        )

        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=10)
        box.set_margin_top(8)
        box.set_margin_bottom(8)

        # Browser Dropdown
        b_labels = [f"{b['name']}{' (Installed)' if b.get('is_installed') else ''}" for b in installed]
        b_model = Gtk.StringList.new(b_labels)
        b_dd = Gtk.DropDown(model=b_model, hexpand=True)
        box.append(b_dd)

        # Profile Dropdown
        p_model = Gtk.StringList()
        p_dd = Gtk.DropDown(model=p_model, hexpand=True)
        box.append(p_dd)

        def _update_p_model():
            b_idx = b_dd.get_selected()
            if 0 <= b_idx < len(installed):
                profs = installed[b_idx].get("profiles", ["Default"])
                while p_model.get_n_items() > 0:
                    p_model.remove(0)
                for p in profs:
                    p_model.append(p)
                p_dd.set_selected(0)

        b_dd.connect("notify::selected", lambda *_: _update_p_model())
        _update_p_model()

        dialog.set_extra_child(box)
        dialog.add_response("cancel", "Cancel")
        dialog.add_response("retry", "Use Browser & Retry")
        dialog.set_response_appearance("retry", Adw.ResponseAppearance.SUGGESTED)

        def _on_resp(dlg, resp):
            if resp == "retry":
                b_idx = b_dd.get_selected()
                p_idx = p_dd.get_selected()
                if 0 <= b_idx < len(installed):
                    b_id = installed[b_idx]["id"]
                    profs = installed[b_idx].get("profiles", ["Default"])
                    p_name = profs[p_idx] if 0 <= p_idx < len(profs) else "Default"
                    spec = self.app.cookie_service.build_browser_spec(b_id, profile=p_name)
                    self._on_url_submitted(self.url_input, url, browser_override=spec)

        dialog.connect("response", _on_resp)
        dialog.present()

    def _browse_and_retry_metadata(self, url: str):
        file_dlg = Gtk.FileDialog.new()
        file_dlg.set_title("Select Netscape Cookie File (cookies.txt)")
        
        def _on_file_selected(d, res):
            try:
                gfile = d.open_finish(res)
                if gfile:
                    cookie_path = gfile.get_path()
                    if cookie_path and os.path.isfile(cookie_path):
                        self._on_url_submitted(self.url_input, url, cookie_override=cookie_path)
            except Exception:
                pass
                
        file_dlg.open(self, None, _on_file_selected)

    def _dismiss_preview(self, preserve_temp_cookie: bool = False):
        while child := self.preview_container.get_first_child():
            if child == self.fetch_spinner_box:
                break
            self.preview_container.remove(child)

        sibling = self.fetch_spinner_box.get_next_sibling()
        while sibling:
            next_s = sibling.get_next_sibling()
            self.preview_container.remove(sibling)
            sibling = next_s

        # Discard temporary cookie file if preview was closed without starting download
        if not preserve_temp_cookie:
            if hasattr(self, '_current_temp_cookie') and self._current_temp_cookie:
                try:
                    if os.path.isfile(self._current_temp_cookie):
                        os.unlink(self._current_temp_cookie)
                except Exception:
                    pass
                self._current_temp_cookie = None

    def _on_download_requested(self, widget, url: str, mode: DownloadMode, quality: str, audio_format: str):
        # 1. Check if selected video quality is available in metadata
        if mode != DownloadMode.AUDIO and quality not in ('Best', ''):
            format_sel = getattr(widget, 'format_selector', None)
            if format_sel and not format_sel.is_quality_available(quality):
                avail = format_sel.get_available_qualities()
                avail_str = ', '.join(avail) if avail else 'None listed'
                
                dialog = Adw.MessageDialog(
                    transient_for=self,
                    heading="Selected Quality Not Available",
                    body=f"The selected resolution ({quality}) is not available for this video.\n\nAvailable resolutions: {avail_str}\n\nPlease choose a different quality or use the default best quality."
                )
                dialog.add_response("choose", "Choose Different Quality")
                dialog.add_response("best", "Use Best Available Quality")
                dialog.set_response_appearance("choose", Adw.ResponseAppearance.SUGGESTED)
                dialog.set_response_appearance("best", Adw.ResponseAppearance.DEFAULT)
                
                def _on_format_unavailable_resp(dlg, resp):
                    if resp == "best":
                        self._proceed_download(widget, url, mode, 'Best', audio_format)
                
                dialog.connect("response", _on_format_unavailable_resp)
                dialog.present()
                return

        self._proceed_download(widget, url, mode, quality, audio_format)

    def _proceed_download(self, widget, url: str, mode: DownloadMode, quality: str, audio_format: str):
        # 2. Check if the exact same file in this format already exists in history
        existing_file = self._find_existing_download(url, mode, quality, audio_format)
        if existing_file and os.path.isfile(existing_file):
            mode_str = "Video + Audio" if mode == DownloadMode.VIDEO_AUDIO else ("Video" if mode == DownloadMode.VIDEO else f"Audio ({audio_format})")
            quality_str = f" • {quality}" if quality and mode != DownloadMode.AUDIO else ""
            
            dialog = Adw.MessageDialog(
                transient_for=self,
                heading="File Already Downloaded",
                body=f"You already have this video downloaded in this format ({mode_str}{quality_str}):\n\n{existing_file}\n\nWould you like to play the existing file or download it again?"
            )
            dialog.add_response("cancel", "Cancel")
            dialog.add_response("open", "Play Media")
            dialog.add_response("download", "Download Again")
            dialog.set_response_appearance("open", Adw.ResponseAppearance.SUGGESTED)
            
            def _on_duplicate_response(dlg, resp):
                if resp == "open":
                    self._open_file(existing_file)
                elif resp == "download":
                    self._start_download_item(widget, url, mode, quality, audio_format)
            
            dialog.connect("response", _on_duplicate_response)
            dialog.present()
            return

        self._start_download_item(widget, url, mode, quality, audio_format)

    def _on_download_playlist_requested(self, widget, selected_entries: list, mode: DownloadMode, quality: str, audio_format: str):
        temp_cookie_to_transfer = getattr(self, '_current_temp_cookie', None)
        self._current_temp_cookie = None
        self._dismiss_preview()

        cookie_file = None
        cookies_from_browser = None

        if temp_cookie_to_transfer and os.path.isfile(temp_cookie_to_transfer):
            cookie_file = temp_cookie_to_transfer
            cookies_from_browser = ""  # Override and bypass any saved browser cookies
        else:
            method = self.app.settings.get_cookie_method()
            if method == 'browser':
                b_name = self.app.settings.get_browser_name()
                b_prof = self.app.settings.get_browser_profile()
                b_key = self.app.settings.get_browser_keyring()
                cookies_from_browser = self.app.cookie_service.build_browser_spec(b_name, profile=b_prof, keyring=b_key)
            elif method == 'file':
                cookie_file = self.app.settings.get('cookie_file', '')

            if hasattr(widget, 'format_selector'):
                sel_file, sel_browser = widget.format_selector.get_selected_cookie_config()
                if sel_browser:
                    cookies_from_browser = sel_browser
                    cookie_file = None
                elif sel_file:
                    cookie_file = sel_file
                    cookies_from_browser = None

        dl_dir = self.app.settings.get('download_dir') or str(get_default_download_dir())
        fallback_url = getattr(widget, 'playlist_data', {}).get('original_url') or getattr(widget, 'playlist_data', {}).get('webpage_url') or ''
        queued_count = 0

        for i, entry in enumerate(selected_entries):
            target_url = entry.get('url') or entry.get('webpage_url') or entry.get('original_url') or fallback_url
            if not target_url:
                continue

            entry_cookie_file = cookie_file
            if temp_cookie_to_transfer and cookie_file:
                # If multiple items, make independent copy for each so they clean up independently
                if i > 0:
                    try:
                        import tempfile
                        import shutil
                        fd, c_path = tempfile.mkstemp(prefix='glypdl_cookie_', suffix='.txt')
                        os.close(fd)
                        shutil.copyfile(cookie_file, c_path)
                        entry_cookie_file = c_path
                    except Exception:
                        entry_cookie_file = cookie_file

            item = DownloadItem(
                url=target_url,
                title=entry.get('title', 'Untitled Video'),
                uploader=entry.get('uploader', ''),
                duration=entry.get('duration', 0),
                thumbnail_url=entry.get('thumbnail', ''),
                thumbnail_path='',
                mode=mode,
                quality=quality,
                audio_format=audio_format,
                download_dir=dl_dir,
                filename_template=self.app.settings.get('filename_template', '%(title)s.%(ext)s'),
                cookie_file=entry_cookie_file or '',
                cookies_from_browser=cookies_from_browser or '',
                is_temp_cookie=bool(temp_cookie_to_transfer)
            )

            self.downloads.append(item)
            self._add_download_card(item)
            self.app.download_manager.start_download(item)
            queued_count += 1

        self._update_downloads_ui()
        if queued_count > 0:
            self._send_notification("Playlist Queued", f"Added {queued_count} media items to download queue")

    def _find_existing_download(self, url: str, mode: DownloadMode, quality: str, audio_format: str) -> str:
        """Return path of existing downloaded file if matching URL and format is found in history."""
        if not self.app.history_service:
            return ""
        entries = self.app.history_service.search(url)
        for entry in entries:
            if entry.get('url') == url:
                # Compare format / quality / mode
                entry_mode = str(entry.get('mode', ''))
                entry_quality = str(entry.get('quality', ''))
                entry_path = entry.get('download_path', '')
                
                mode_matches = (str(mode) in entry_mode) or (entry_mode in str(mode))
                quality_matches = (quality == entry_quality) or (mode == DownloadMode.AUDIO and audio_format in entry_quality)
                
                if mode_matches and quality_matches and entry_path and os.path.isfile(entry_path):
                    return entry_path
        return ""

    def _start_download_item(self, widget, url: str, mode: DownloadMode, quality: str, audio_format: str):
        temp_cookie_to_transfer = getattr(self, '_current_temp_cookie', None)
        self._current_temp_cookie = None
        self._dismiss_preview()

        cookie_file = None
        cookies_from_browser = None

        if temp_cookie_to_transfer and os.path.isfile(temp_cookie_to_transfer):
            cookie_file = temp_cookie_to_transfer
            cookies_from_browser = ""  # Override and bypass any saved browser cookies
        else:
            method = self.app.settings.get_cookie_method()
            if method == 'browser':
                b_name = self.app.settings.get_browser_name()
                b_prof = self.app.settings.get_browser_profile()
                b_key = self.app.settings.get_browser_keyring()
                cookies_from_browser = self.app.cookie_service.build_browser_spec(b_name, profile=b_prof, keyring=b_key)
            elif method == 'file':
                cookie_file = self.app.settings.get('cookie_file', '')

            if hasattr(widget, 'format_selector'):
                sel_file, sel_browser = widget.format_selector.get_selected_cookie_config()
                if sel_browser:
                    cookies_from_browser = sel_browser
                    cookie_file = None
                elif sel_file:
                    cookie_file = sel_file
                    cookies_from_browser = None

        dl_dir = self.app.settings.get('download_dir') or str(get_default_download_dir())
        target_url = url or getattr(widget, 'metadata', {}).get('webpage_url') or getattr(widget, 'metadata', {}).get('original_url') or ''
        if not target_url:
            return

        item = DownloadItem(
            url=target_url,
            title=getattr(widget, 'metadata', {}).get('title', target_url),
            uploader=getattr(widget, 'metadata', {}).get('uploader', ''),
            duration=getattr(widget, 'metadata', {}).get('duration', 0),
            thumbnail_url=getattr(widget, 'metadata', {}).get('thumbnail', ''),
            thumbnail_path=getattr(widget, 'metadata', {}).get('thumbnail_path', ''),
            mode=mode,
            quality=quality,
            audio_format=audio_format,
            download_dir=dl_dir,
            filename_template=self.app.settings.get('filename_template', '%(title)s.%(ext)s'),
            cookie_file=cookie_file or '',
            cookies_from_browser=cookies_from_browser or '',
            is_temp_cookie=bool(temp_cookie_to_transfer)
        )

        self.downloads.append(item)
        self._add_download_card(item)
        self.app.download_manager.start_download(item)
        self._update_downloads_ui()

    def _add_download_card(self, item: DownloadItem):
        card = DownloadCard(download_item=item)
        self._cards[item.id] = card

        card.connect('cancel-requested', lambda c, d_id: self.app.download_manager.cancel_download(d_id))
        card.connect('retry-requested', lambda c, d_id: self._retry_download(item))
        card.connect('remove-requested', lambda c, d_id: self._remove_download(item))
        card.connect('open-file-requested', lambda c, d_id: self._open_file(item.output_path))
        card.connect('open-folder-requested', lambda c, d_id: self._open_folder(item.output_path or item.download_dir))
        
        item.connect('notify::state', lambda obj, pspec, c=card: self._on_item_state_changed(obj, c))

        self._place_card_in_correct_list(item, card)

    def _place_card_in_correct_list(self, item: DownloadItem, card: DownloadCard):
        row = card.get_ancestor(Gtk.ListBoxRow)
        current_list = row.get_parent() if row else None

        if item.state == DownloadState.QUEUED:
            target_list = self.queued_list
        elif item.state in (DownloadState.COMPLETED, DownloadState.FAILED, DownloadState.CANCELLED):
            target_list = self.completed_list
        else:
            target_list = self.downloads_list

        if current_list != target_list:
            if row and current_list:
                row.set_child(None)
                current_list.remove(row)
            target_list.append(card)

    def _on_item_state_changed(self, item: DownloadItem, card: DownloadCard):
        self._place_card_in_correct_list(item, card)

        if item.state == DownloadState.COMPLETED:
            self.app.history_service.add_entry(item)
            self._load_history()
            self._send_notification("Download Complete", f"Successfully downloaded {item.title or item.url}")
        elif item.state == DownloadState.FAILED:
            self.app.history_service.add_entry(item)
            self._load_history()
            self._send_notification("Download Failed", f"Failed downloading {item.title or item.url}")

        self._update_downloads_ui()

    def _remove_download(self, item: DownloadItem):
        if item in self.downloads:
            self.downloads.remove(item)
        card = self._cards.pop(item.id, None)
        if card:
            row = card.get_ancestor(Gtk.ListBoxRow)
            if row:
                list_box = row.get_parent()
                if list_box:
                    row.set_child(None)
                    list_box.remove(row)
        self._update_downloads_ui()

    def _on_clear_completed_clicked(self, *args):
        completed_items = [
            d for d in self.downloads
            if d.state in (DownloadState.COMPLETED, DownloadState.FAILED, DownloadState.CANCELLED)
        ]
        for item in completed_items:
            self._remove_download(item)

    def _retry_download(self, item: DownloadItem):
        self.app.download_manager.retry_download(item)
        self._update_downloads_ui()

    def _update_downloads_ui(self):
        has_items = len(self.downloads) > 0
        self.empty_downloads_status.set_visible(not has_items)

        active_count = len([d for d in self.downloads if d.state not in (DownloadState.QUEUED, DownloadState.COMPLETED, DownloadState.FAILED, DownloadState.CANCELLED)])
        queued_count = len([d for d in self.downloads if d.state == DownloadState.QUEUED])
        completed_count = len([d for d in self.downloads if d.state in (DownloadState.COMPLETED, DownloadState.FAILED, DownloadState.CANCELLED)])

        self.active_heading.set_visible(active_count > 0)
        self.downloads_list.set_visible(active_count > 0)

        self.queued_heading.set_visible(queued_count > 0)
        self.queued_list.set_visible(queued_count > 0)

        self.completed_header_box.set_visible(completed_count > 0)
        self.completed_scrolled.set_visible(completed_count > 0)

    # ----------------------------------------------------
    # History Page Logic
    # ----------------------------------------------------
    def _load_history(self, query: str = ""):
        while child := self.history_list.get_first_child():
            self.history_list.remove(child)

        if not self.app.history_service:
            return

        entries = self.app.history_service.search(query) if query else self.app.history_service.get_all()
        has_history = len(entries) > 0

        self.empty_history_status.set_visible(not has_history)
        self.history_list.set_visible(has_history)

        for entry in entries:
            self._create_history_row(entry)

    def _create_history_row(self, entry: dict):
        title = entry.get('title') or entry.get('url') or "Unknown"
        timestamp = entry.get('timestamp') or ""
        status = entry.get('status') or "COMPLETED"
        size = entry.get('file_size') or 0
        out_path = entry.get('download_path') or ""
        thumb_path = entry.get('thumbnail_path') or ""
        thumb_url = entry.get('thumbnail_url') or ""
        entry_id = entry.get('id')

        # Auto-heal download path if missing or file moved
        if not out_path or not os.path.exists(out_path):
            dl_dir = self.app.settings.get('download_dir') or str(get_default_download_dir())
            if dl_dir and os.path.isdir(dl_dir):
                # Check for matching filename in dl_dir
                for ext in ['.mp3', '.m4a', '.opus', '.flac', '.wav', '.mp4', '.webm', '.mkv']:
                    cand = os.path.join(dl_dir, f"{title}{ext}")
                    if os.path.isfile(cand):
                        out_path = cand
                        if entry_id:
                            self.app.history_service.update_download_path(entry_id, cand)
                        break

        # Check real file size on disk
        if out_path and os.path.isfile(out_path):
            try:
                disk_size = os.path.getsize(out_path)
                if disk_size > 0:
                    size = disk_size
                    if entry_id and entry.get('file_size') != disk_size:
                        self.app.history_service.update_file_size(entry_id, disk_size)
            except Exception:
                pass

        size_str = format_size(size) if size else ""

        # Format descriptive subtitle
        mode_val = str(entry.get('mode') or '')
        quality_val = str(entry.get('quality') or '')
        if 'AUDIO' in mode_val.upper() and 'VIDEO' not in mode_val.upper():
            type_label = f"Audio ({quality_val or 'MP3'})"
        elif 'VIDEO' in mode_val.upper():
            type_label = f"Video ({quality_val or 'Best'})"
        else:
            type_label = quality_val if quality_val else ""

        subtitle_parts = [status.capitalize()]
        if type_label:
            subtitle_parts.append(type_label)
        if timestamp:
            subtitle_parts.append(timestamp[:16].replace('T', ' '))
        if size_str:
            subtitle_parts.append(size_str)

        subtitle = " • ".join(subtitle_parts)

        # Card container with generous row size
        escaped_title = GLib.markup_escape_text(title)
        escaped_subtitle = GLib.markup_escape_text(subtitle)
        row = Adw.ActionRow(title=escaped_title, subtitle=escaped_subtitle)
        row.set_title_lines(1)
        row.set_subtitle_lines(1)
        row.set_margin_top(2)
        row.set_margin_bottom(2)

        # Prefix thumbnail with strict fixed size container
        thumb_frame = Gtk.Box(halign=Gtk.Align.CENTER, valign=Gtk.Align.CENTER)
        thumb_frame.set_size_request(80, 48)
        thumb_frame.set_hexpand(False)
        thumb_frame.set_vexpand(False)
        row.add_prefix(thumb_frame)

        thumb_picture = Gtk.Picture()
        thumb_picture.set_size_request(80, 48)
        thumb_picture.set_can_shrink(True)
        thumb_picture.set_content_fit(Gtk.ContentFit.COVER)
        thumb_picture.set_hexpand(False)
        thumb_picture.set_vexpand(False)
        thumb_picture.add_css_class('card')
        thumb_frame.append(thumb_picture)

        # Load local scaled thumbnail or fetch asynchronously if missing
        if thumb_path and os.path.isfile(thumb_path):
            texture = load_scaled_texture(thumb_path, 80, 48)
            if texture:
                thumb_picture.set_paintable(texture)
            else:
                try:
                    thumb_picture.set_file(Gio.File.new_for_path(thumb_path))
                except Exception:
                    pass
        elif thumb_url:
            def _on_thumb_cached(cached_file):
                if cached_file and os.path.isfile(cached_file):
                    texture = load_scaled_texture(cached_file, 80, 48)
                    if texture:
                        thumb_picture.set_paintable(texture)
                    else:
                        thumb_picture.set_file(Gio.File.new_for_path(cached_file))
                    if entry_id:
                        self.app.history_service.update_thumbnail_path(entry_id, cached_file)
            load_thumbnail_async(thumb_url, _on_thumb_cached)
        else:
            thumb_picture.set_paintable(None)

        # Context action buttons
        btn_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6, valign=Gtk.Align.CENTER)
        row.add_suffix(btn_box)

        if out_path and os.path.exists(out_path):
            # Play / Open Media button with standard play icon
            play_btn = Gtk.Button(icon_name="media-playback-start-symbolic", tooltip_text="Play / Open Media", valign=Gtk.Align.CENTER)
            play_btn.connect('clicked', lambda _, p=out_path: self._open_file(p))
            btn_box.append(play_btn)

            folder_btn = Gtk.Button(icon_name="folder-open-symbolic", tooltip_text="Open Containing Folder", valign=Gtk.Align.CENTER)
            folder_btn.connect('clicked', lambda _, p=out_path: self._open_folder(p))
            btn_box.append(folder_btn)

        copy_btn = Gtk.Button(icon_name="edit-copy-symbolic", tooltip_text="Copy URL", valign=Gtk.Align.CENTER)
        copy_btn.connect('clicked', lambda _, u=entry.get('url', ''): self._copy_to_clipboard(u))
        btn_box.append(copy_btn)

        redownload_btn = Gtk.Button(icon_name="folder-download-symbolic", tooltip_text="Download Again", valign=Gtk.Align.CENTER)
        redownload_btn.connect('clicked', lambda _, u=entry.get('url', ''): self._redownload_url(u))
        btn_box.append(redownload_btn)

        del_btn = Gtk.Button(icon_name="user-trash-symbolic", tooltip_text="Remove Record", valign=Gtk.Align.CENTER)
        del_btn.add_css_class("destructive-action")
        del_btn.connect('clicked', lambda _, e_id=entry.get('id'): self._delete_history_entry(e_id, row))
        btn_box.append(del_btn)

        self.history_list.append(row)

    def _on_history_search_changed(self, search_entry):
        query = search_entry.get_text().strip()
        self._load_history(query=query)

    def _on_clear_history_clicked(self, *args):
        dialog = Adw.MessageDialog(
            transient_for=self,
            heading="Clear Download History?",
            body="This will delete all download records from the history database. Downloaded files on disk will not be removed."
        )
        dialog.add_response("cancel", "Cancel")
        dialog.add_response("clear", "Clear All")
        dialog.set_response_appearance("clear", Adw.ResponseAppearance.DESTRUCTIVE)
        dialog.connect("response", self._on_clear_history_response)
        dialog.present()

    def _on_clear_history_response(self, dialog, response):
        if response == "clear":
            self.app.history_service.clear_all()
            self._load_history()

    def _delete_history_entry(self, entry_id: str, row: Adw.ActionRow):
        if entry_id:
            self.app.history_service.remove_entry(entry_id)
        self.history_list.remove(row)
        if not self.history_list.get_first_child():
            self.empty_history_status.set_visible(True)
            self.history_list.set_visible(False)

    def _redownload_url(self, url: str):
        if url:
            self.view_stack.set_visible_child_name("downloads")
            self.url_input.set_text(url)
            self._on_url_submitted(self.url_input, url)

    # ----------------------------------------------------
    # External Actions & File Management
    # ----------------------------------------------------
    def _open_file(self, file_path: str):
        if file_path and os.path.exists(file_path):
            try:
                gfile = Gio.File.new_for_path(file_path)
                launcher = Gtk.FileLauncher.new(gfile)
                launcher.launch(self, None, None)
            except Exception:
                GLib.spawn_command_line_async(f"xdg-open '{file_path}'")

    def _open_folder(self, target_path: str):
        """Navigate inside folder in file manager, highlighting the file if available."""
        if not target_path:
            target_path = str(get_default_download_dir())
        
        # If target_path is an existing file, open its containing folder and highlight it
        if os.path.isfile(target_path):
            try:
                gfile = Gio.File.new_for_path(target_path)
                launcher = Gtk.FileLauncher.new(gfile)
                launcher.open_containing_folder(self, None, None)
                return
            except Exception:
                pass
            target_folder = os.path.dirname(target_path)
        else:
            target_folder = target_path if os.path.isdir(target_path) else os.path.dirname(target_path)

        # Open the folder directly
        if os.path.exists(target_folder):
            try:
                gfile = Gio.File.new_for_path(target_folder)
                launcher = Gtk.FileLauncher.new(gfile)
                launcher.launch(self, None, None)
            except Exception:
                GLib.spawn_command_line_async(f"xdg-open '{target_folder}'")

    def _copy_to_clipboard(self, text: str):
        if text:
            clipboard = self.get_display().get_clipboard()
            clipboard.set(text)

    def _send_notification(self, title: str, body: str):
        if not self.app.settings.get('notifications', True):
            return
        try:
            notification = Gio.Notification.new(title)
            notification.set_body(body)
            notification.set_icon(Gio.ThemedIcon.new_with_default_fallbacks("folder-download-symbolic"))
            self.app.send_notification("glypdl-download", notification)
        except Exception:
            pass
