"""Format and quality selection widget using robust Gtk.DropDown selectors."""

import os
import gi

gi.require_version('Gtk', '4.0')
gi.require_version('Adw', '1')
from gi.repository import Gtk, Adw, GObject

from glypdl.models.download import DownloadMode
from glypdl.utils.formatting import format_size


class FormatSelector(Gtk.Box):
    """Allows selecting download mode (Video+Audio, Video, Audio), resolution/audio format, cookie profile, and inspecting streams."""
    __gtype_name__ = 'GlypdlFormatSelector'

    STANDARD_QUALITIES = ['2160p', '1440p', '1080p', '720p', '480p', '360p', '240p', '144p']

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.set_orientation(Gtk.Orientation.VERTICAL)
        self.set_spacing(10)

        self.pref_group = Adw.PreferencesGroup()
        self.append(self.pref_group)

        # 1. Download Mode Selector
        self.mode_model = Gtk.StringList.new(['Video + Audio', 'Video Only', 'Audio Only'])
        self.mode_dd = Gtk.DropDown(model=self.mode_model, valign=Gtk.Align.CENTER)
        self.mode_dd.connect('notify::selected', self._on_mode_changed)
        
        self.mode_row = Adw.ActionRow(title='Download Type')
        self.mode_row.add_suffix(self.mode_dd)
        self.mode_row.set_activatable_widget(self.mode_dd)
        self.pref_group.add(self.mode_row)

        # 2. Quality / Format Selector
        self._current_qualities = ['Best'] + self.STANDARD_QUALITIES
        self.video_quality_model = Gtk.StringList.new(self._current_qualities)
        self.audio_quality_model = Gtk.StringList.new(['Best', 'MP3', 'M4A', 'Opus', 'FLAC', 'WAV'])
        
        self.quality_dd = Gtk.DropDown(model=self.video_quality_model, valign=Gtk.Align.CENTER)
        self.quality_row = Adw.ActionRow(title='Quality / Format')
        self.quality_row.add_suffix(self.quality_dd)
        self.quality_row.set_activatable_widget(self.quality_dd)
        self.pref_group.add(self.quality_row)

        # 3. Cookie Profile Selector
        # 3. Authentication Cookie Selector
        self._cookie_options = [{"type": "none", "label": "No Cookies", "spec": "", "path": "", "desc": "No authentication cookies"}]
        self.cookie_model = Gtk.StringList.new(['No Cookies'])
        
        self.cookie_dd = Gtk.DropDown(model=self.cookie_model, valign=Gtk.Align.CENTER)
        self.cookie_dd.connect('notify::selected', self._on_cookie_selection_changed)
        
        self.cookie_row = Adw.ActionRow(title='Authentication Cookies')
        self.cookie_row.add_suffix(self.cookie_dd)
        self.cookie_row.set_activatable_widget(self.cookie_dd)
        self.cookie_row.set_visible(False)
        self.pref_group.add(self.cookie_row)

        # 4. Advanced Stream Details Expander
        self.advanced_expander = Adw.ExpanderRow(title='Advanced Stream Details')
        self.advanced_expander.set_subtitle('View individual video and audio streams')
        self.pref_group.add(self.advanced_expander)

        self.format_list = Gtk.ListBox()
        self.format_list.add_css_class('boxed-list')
        self.format_list.set_selection_mode(Gtk.SelectionMode.NONE)
        self.advanced_expander.add_row(self.format_list)

        self.metadata = {}
        self._available_qualities_set = set()

    def _on_mode_changed(self, dropdown, pspec):
        selected = dropdown.get_selected()
        if selected == 2:  # Audio Only
            self.quality_dd.set_model(self.audio_quality_model)
            self.quality_row.set_title('Audio Format')
        else:
            self.quality_dd.set_model(self.video_quality_model)
            self.quality_row.set_title('Video Quality')

    def _on_cookie_selection_changed(self, dropdown, pspec):
        idx = dropdown.get_selected()
        if 0 <= idx < len(self._cookie_options):
            opt = self._cookie_options[idx]
            self.cookie_row.set_subtitle(opt.get("desc") or opt.get("label") or "No cookies attached")

    def set_cookie_profiles(
        self,
        profiles: list = None,
        default_cookie_path: str = "",
        active_cookie_path: str = "",
        cookie_method: str = "none",
        active_browser_spec: str = "",
        installed_browsers: list = None,
        use_cookies: bool = False
    ):
        """Populate cookie profiles dropdown with both browser cookies and file profiles."""
        options = []
        labels = []
        selected_idx = 0

        # 1. Active / Configured Browser Option
        if active_browser_spec:
            spec_display = active_browser_spec
            # Extract friendly browser name if possible
            b_name = active_browser_spec.split(':')[0].split('+')[0].capitalize()
            p_name = active_browser_spec.split(':')[-1] if ':' in active_browser_spec else ""
            prof_disp = f" ({p_name})" if p_name and p_name != active_browser_spec else ""
            opt_label = f"🌐 Browser: {b_name}{prof_disp}"
            options.append({
                "type": "browser",
                "label": opt_label,
                "spec": active_browser_spec,
                "path": "",
                "desc": f"Extracting cookies directly from {b_name}{prof_disp}"
            })
            labels.append(opt_label)

        # 2. Add other installed browsers if discovered
        if installed_browsers:
            for b in installed_browsers:
                if b.get("is_installed"):
                    b_id = b.get("id")
                    b_title = b.get("name")
                    for prof in b.get("profiles", ["Default"]):
                        b_spec = f"{b_id}:{prof}" if prof and prof != "Default" else b_id
                        if b_id == "librewolf" and prof:
                            b_spec = f"librewolf:{prof}"
                        
                        # Don't duplicate if already added as active_browser_spec
                        if any(o.get("spec") == b_spec for o in options):
                            continue

                        p_disp = f" ({prof})" if prof else ""
                        opt_label = f"🌐 Browser: {b_title}{p_disp}"
                        options.append({
                            "type": "browser",
                            "label": opt_label,
                            "spec": b_spec,
                            "path": "",
                            "desc": f"Extract cookies directly from {b_title}{p_disp}"
                        })
                        labels.append(opt_label)

        # 3. Add Saved File Profiles
        for p in profiles or []:
            name = p.get('name', 'Profile')
            path = p.get('path', '')
            if path:
                base = os.path.basename(path)
                opt_label = f"📄 File: {name} ({base})"
                options.append({
                    "type": "file",
                    "label": opt_label,
                    "spec": "",
                    "path": path,
                    "desc": path
                })
                labels.append(opt_label)

        # 4. Default / Custom Cookie File if present and not in profiles
        if default_cookie_path and not any(o.get("path") == default_cookie_path for o in options):
            base = os.path.basename(default_cookie_path)
            opt_label = f"📄 Default File ({base})"
            options.append({
                "type": "file",
                "label": opt_label,
                "spec": "",
                "path": default_cookie_path,
                "desc": default_cookie_path
            })
            labels.append(opt_label)

        if active_cookie_path and not any(o.get("path") == active_cookie_path for o in options):
            base = os.path.basename(active_cookie_path)
            opt_label = f"📄 Custom File ({base})"
            options.append({
                "type": "file",
                "label": opt_label,
                "spec": "",
                "path": active_cookie_path,
                "desc": active_cookie_path
            })
            labels.append(opt_label)

        # 5. Always provide "No Cookies" option
        options.append({
            "type": "none",
            "label": "No Cookies (Anonymous)",
            "spec": "",
            "path": "",
            "desc": "No authentication cookies attached"
        })
        labels.append("No Cookies (Anonymous)")

        self._cookie_options = options
        self.cookie_model = Gtk.StringList.new(labels)
        self.cookie_dd.set_model(self.cookie_model)

        # Make cookie row visible if any cookie options are available or cookies are active
        has_any_cookies = (len(options) > 1)
        self.cookie_row.set_visible(has_any_cookies)

        # Auto-selection logic
        if active_browser_spec:
            for idx, opt in enumerate(options):
                if opt["type"] == "browser" and opt["spec"] == active_browser_spec:
                    selected_idx = idx
                    break
        elif active_cookie_path:
            for idx, opt in enumerate(options):
                if opt["type"] == "file" and opt["path"] == active_cookie_path:
                    selected_idx = idx
                    break
        elif cookie_method == "browser" and len(options) > 1:
            selected_idx = 0
        elif cookie_method == "file" and default_cookie_path:
            for idx, opt in enumerate(options):
                if opt["type"] == "file" and opt["path"] == default_cookie_path:
                    selected_idx = idx
                    break
        else:
            # Select No Cookies (last item)
            selected_idx = len(options) - 1

        self.cookie_dd.set_selected(selected_idx)
        self._on_cookie_selection_changed(self.cookie_dd, None)

    def get_selected_cookie_config(self) -> tuple:
        """Return (cookie_file, cookies_from_browser) based on dropdown selection."""
        idx = self.cookie_dd.get_selected()
        if 0 <= idx < len(self._cookie_options):
            opt = self._cookie_options[idx]
            if opt["type"] == "browser":
                return (None, opt["spec"])
            elif opt["type"] == "file":
                return (opt["path"], None)
        return (None, None)

    def get_selected_cookie_file(self) -> str:
        """Return the file path if a file profile is selected."""
        file_path, _ = self.get_selected_cookie_config()
        return file_path or ""

    def get_selected_cookies_from_browser(self) -> str:
        """Return the browser spec if a browser cookie is selected."""
        _, browser_spec = self.get_selected_cookie_config()
        return browser_spec or ""

    def set_formats(self, metadata: dict):
        """Populate available qualities and detailed formats from metadata dict."""
        self.metadata = metadata or {}
        formats = metadata.get('formats', [])
        
        # Populate available video qualities
        self._available_qualities_set = set()
        for f in formats:
            height = f.get('height')
            if height and f.get('vcodec') != 'none':
                self._available_qualities_set.add(f"{height}p")

        # Keep standard qualities in dropdown so users can select any standard resolution
        all_q = ['Best'] + self.STANDARD_QUALITIES
        self._current_qualities = all_q
        self.video_quality_model = Gtk.StringList.new(all_q)
        if self.mode_dd.get_selected() != 2:
            self.quality_dd.set_model(self.video_quality_model)

        # Clear and repopulate stream list
        while child := self.format_list.get_first_child():
            self.format_list.remove(child)

        for fmt in formats:
            fmt_id = str(fmt.get('format_id', ''))
            ext = fmt.get('ext', '')
            res = fmt.get('resolution') or (f"{fmt.get('width')}x{fmt.get('height')}" if fmt.get('height') else '')
            vcodec = fmt.get('vcodec', 'none')
            acodec = fmt.get('acodec', 'none')
            fps = fmt.get('fps', '')
            filesize = fmt.get('filesize') or fmt.get('filesize_approx') or 0
            size_str = format_size(filesize) if filesize else "Unknown size"

            desc_parts = [f"ID: {fmt_id}", f"Ext: {ext}"]
            if res and res != 'audio only':
                desc_parts.append(f"Res: {res}")
            if fps:
                desc_parts.append(f"FPS: {fps}")
            if vcodec != 'none':
                desc_parts.append(f"Video: {vcodec}")
            if acodec != 'none':
                desc_parts.append(f"Audio: {acodec}")
            desc_parts.append(size_str)

            row = Adw.ActionRow(
                title=f"{ext.upper()} {res}".strip(),
                subtitle=" • ".join(desc_parts)
            )
            self.format_list.append(row)

    def get_selected_mode(self) -> DownloadMode:
        selected = self.mode_dd.get_selected()
        if selected == 0:
            return DownloadMode.VIDEO_AUDIO
        elif selected == 1:
            return DownloadMode.VIDEO
        else:
            return DownloadMode.AUDIO

    def get_selected_quality(self) -> str:
        model = self.quality_dd.get_model()
        selected = self.quality_dd.get_selected()
        if model and selected < model.get_n_items():
            return model.get_string(selected)
        return 'Best'

    def get_available_qualities(self) -> list:
        def q_key(q):
            try:
                return int(q.replace('p', ''))
            except ValueError:
                return 0
        return sorted(list(self._available_qualities_set), key=q_key, reverse=True)

    def is_quality_available(self, quality: str) -> bool:
        if quality in ('Best', ''):
            return True
        if not self._available_qualities_set:  # If no format metadata, assume available
            return True
        return quality in self._available_qualities_set

    def get_audio_format(self) -> str:
        if self.get_selected_mode() == DownloadMode.AUDIO:
            return self.get_selected_quality()
        return 'Best'


GlypdlFormatSelector = FormatSelector
