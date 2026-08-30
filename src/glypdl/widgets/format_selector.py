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
        self._cookie_paths = [""]
        self.cookie_model = Gtk.StringList.new(['No Cookies'])
        
        self.cookie_dd = Gtk.DropDown(model=self.cookie_model, valign=Gtk.Align.CENTER)
        self.cookie_dd.connect('notify::selected', self._on_cookie_selection_changed)
        
        self.cookie_row = Adw.ActionRow(title='Cookie Profile')
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
        if 0 <= idx < len(self._cookie_paths):
            path = self._cookie_paths[idx]
            if path:
                self.cookie_row.set_subtitle(path)
            else:
                self.cookie_row.set_subtitle("No cookies attached")

    def set_cookie_profiles(self, profiles: list, default_cookie_path: str = "", active_cookie_path: str = "", use_cookies: bool = False):
        """Populate cookie profiles dropdown and auto-select active cookie file if used during retrieval."""
        labels = ["No Cookies"]
        paths = [""]
        seen_paths = set()

        for p in profiles or []:
            name = p.get('name', 'Profile')
            path = p.get('path', '')
            if path:
                base = os.path.basename(path)
                labels.append(f"{name} ({base})")
                paths.append(path)
                seen_paths.add(path)

        if default_cookie_path and default_cookie_path not in seen_paths:
            base = os.path.basename(default_cookie_path)
            labels.append(f"Default Cookie ({base})")
            paths.append(default_cookie_path)
            seen_paths.add(default_cookie_path)

        if active_cookie_path and active_cookie_path not in seen_paths:
            base = os.path.basename(active_cookie_path)
            labels.append(f"Custom Cookie ({base})")
            paths.append(active_cookie_path)
            seen_paths.add(active_cookie_path)

        self._cookie_paths = paths
        self.cookie_model = Gtk.StringList.new(labels)
        self.cookie_dd.set_model(self.cookie_model)

        # Show if we have profiles, or if cookies are enabled, or if an active cookie was used
        has_profiles = len(paths) > 1
        self.cookie_row.set_visible(has_profiles or use_cookies or bool(active_cookie_path))
        
        # Priority selection:
        # 1. active_cookie_path (the exact cookie used to successfully fetch metadata)
        # 2. default_cookie_path (if use_cookies is True)
        # 3. No Cookies (index 0)
        selected_idx = 0
        if active_cookie_path:
            for i, p in enumerate(paths):
                if p == active_cookie_path:
                    selected_idx = i
                    break
        elif use_cookies and default_cookie_path:
            for i, p in enumerate(paths):
                if p == default_cookie_path:
                    selected_idx = i
                    break
            if selected_idx == 0 and len(paths) > 1:
                selected_idx = 1

        self.cookie_dd.set_selected(selected_idx)
        self._on_cookie_selection_changed(self.cookie_dd, None)

    def get_selected_cookie_file(self) -> str:
        """Return the file path of the selected cookie profile."""
        idx = self.cookie_dd.get_selected()
        if 0 <= idx < len(self._cookie_paths):
            return self._cookie_paths[idx]
        return ""

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
