"""Playlist preview card allowing per-video selection, thumbnails, select-all, and batch format configuration."""

import os
import gi

gi.require_version('Gtk', '4.0')
gi.require_version('Adw', '1')
from gi.repository import Gtk, Adw, Gio, GObject, Pango, GLib

from glypdl.widgets.format_selector import FormatSelector
from glypdl.models.download import DownloadMode
from glypdl.utils.formatting import format_duration
from glypdl.utils.thumbnails import load_thumbnail_async, load_scaled_texture


class PlaylistPreviewCard(Gtk.Box):
    """Card displaying playlist videos with interactive checkboxes, thumbnails, select-all, and format selection."""
    __gtype_name__ = 'GlypdlPlaylistPreviewCard'

    __gsignals__ = {
        'download-playlist-requested': (GObject.SignalFlags.RUN_LAST, None, (object, object, str, str)),
        'cancel-preview': (GObject.SignalFlags.RUN_LAST, None, ())
    }

    def __init__(self, playlist_data: dict = None, **kwargs):
        super().__init__(**kwargs)
        self.set_orientation(Gtk.Orientation.VERTICAL)
        self.add_css_class('card')
        self.set_spacing(12)
        self.set_margin_top(8)
        self.set_margin_bottom(8)
        self.set_margin_start(8)
        self.set_margin_end(8)

        self.playlist_data = playlist_data or {}
        self.entries = []
        self._check_buttons = []

        self._build_ui()
        if playlist_data:
            self.set_playlist(playlist_data)

    def _build_ui(self):
        # 1. Playlist Header
        header_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)
        header_box.set_margin_top(12)
        header_box.set_margin_start(14)
        header_box.set_margin_end(14)
        self.append(header_box)

        self.title_label = Gtk.Label(halign=Gtk.Align.START, xalign=0.0)
        self.title_label.add_css_class('title-2')
        self.title_label.set_wrap(True)
        self.title_label.set_wrap_mode(Pango.WrapMode.WORD_CHAR)
        header_box.append(self.title_label)

        self.subtitle_label = Gtk.Label(halign=Gtk.Align.START, xalign=0.0)
        self.subtitle_label.add_css_class('dim-label')
        header_box.append(self.subtitle_label)

        # 2. Selection Toolbar
        toolbar = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=12)
        toolbar.set_margin_start(14)
        toolbar.set_margin_end(14)
        self.append(toolbar)

        self.select_all_btn = Gtk.Button(label="Select All")
        self.select_all_btn.connect('clicked', self._on_toggle_all_clicked)
        toolbar.append(self.select_all_btn)

        self.selection_count_label = Gtk.Label(label="", halign=Gtk.Align.START)
        self.selection_count_label.add_css_class('dim-label')
        self.selection_count_label.set_hexpand(True)
        toolbar.append(self.selection_count_label)

        # 3. Scrollable List of Playlist Videos
        self.scrolled = Gtk.ScrolledWindow()
        self.scrolled.set_propagate_natural_height(True)
        self.scrolled.set_max_content_height(340)
        self.scrolled.set_min_content_height(140)
        self.scrolled.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        self.scrolled.set_margin_start(14)
        self.scrolled.set_margin_end(14)
        self.append(self.scrolled)

        self.items_list = Gtk.ListBox()
        self.items_list.add_css_class('boxed-list')
        self.items_list.set_selection_mode(Gtk.SelectionMode.NONE)
        self.scrolled.set_child(self.items_list)

        # 4. Format Selector
        self.format_selector = FormatSelector()
        self.format_selector.set_margin_start(14)
        self.format_selector.set_margin_end(14)
        self.append(self.format_selector)

        # 5. Action Buttons Box
        action_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=12)
        action_box.set_halign(Gtk.Align.END)
        action_box.set_margin_bottom(12)
        action_box.set_margin_end(14)
        self.append(action_box)

        self.cancel_btn = Gtk.Button(label='Dismiss')
        self.cancel_btn.connect('clicked', lambda _: self.emit('cancel-preview'))
        action_box.append(self.cancel_btn)

        self.download_btn = Gtk.Button(label='Download Selected')
        self.download_btn.add_css_class('suggested-action')
        self.download_btn.connect('clicked', self._on_download_clicked)
        action_box.append(self.download_btn)

    def set_playlist(self, playlist_data: dict):
        """Populate the playlist card with video entries, thumbnails, and checkboxes."""
        self.playlist_data = playlist_data or {}
        self.entries = list(playlist_data.get('entries', []))
        self._check_buttons = []

        title = playlist_data.get('title') or "YouTube Playlist"
        uploader = playlist_data.get('uploader') or ""
        count = len(self.entries)

        self.title_label.set_text(title)
        subtitle_parts = []
        if uploader:
            subtitle_parts.append(uploader)
        subtitle_parts.append(f"{count} videos")
        self.subtitle_label.set_text(" • ".join(subtitle_parts))

        # Clear existing rows
        while child := self.items_list.get_first_child():
            self.items_list.remove(child)

        # Build each video row
        for idx, entry in enumerate(self.entries):
            row = self._create_entry_row(entry, idx)
            self.items_list.append(row)

        self._update_selection_ui()

    def _create_entry_row(self, entry: dict, idx: int) -> Gtk.Widget:
        row = Adw.ActionRow()
        row.set_title(GLib.markup_escape_text(entry.get('title', 'Untitled Video')))
        row.set_title_lines(1)

        dur = entry.get('duration') or 0
        dur_str = format_duration(dur) if dur else ""
        uploader = entry.get('uploader') or ""
        sub_parts = []
        if uploader:
            sub_parts.append(uploader)
        if dur_str:
            sub_parts.append(dur_str)
        if sub_parts:
            row.set_subtitle(GLib.markup_escape_text(" • ".join(sub_parts)))
            row.set_subtitle_lines(1)

        # Checkbox prefix
        chk = Gtk.CheckButton()
        chk.set_active(entry.get('selected', True))
        chk.connect('toggled', self._on_checkbox_toggled, entry)
        self._check_buttons.append(chk)
        row.add_prefix(chk)

        # Thumbnail prefix
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

        thumb_url = entry.get('thumbnail')
        if thumb_url:
            def _on_thumb_cached(cached_path):
                if cached_path and os.path.isfile(cached_path):
                    texture = load_scaled_texture(cached_path, 80, 48)
                    if texture:
                        thumb_picture.set_paintable(texture)
                    else:
                        thumb_picture.set_file(Gio.File.new_for_path(cached_path))
            load_thumbnail_async(thumb_url, _on_thumb_cached)

        # Click on row toggles checkbox
        row.set_activatable_widget(chk)
        return row

    def _on_checkbox_toggled(self, chk: Gtk.CheckButton, entry: dict):
        entry['selected'] = chk.get_active()
        self._update_selection_ui()

    def _on_toggle_all_clicked(self, *args):
        # If any is unchecked, select all; otherwise deselect all
        selected_count = sum(1 for e in self.entries if e.get('selected', True))
        select_all = selected_count < len(self.entries)

        for idx, entry in enumerate(self.entries):
            entry['selected'] = select_all
            if idx < len(self._check_buttons):
                self._check_buttons[idx].set_active(select_all)

        self._update_selection_ui()

    def _update_selection_ui(self):
        selected_count = sum(1 for e in self.entries if e.get('selected', True))
        total_count = len(self.entries)

        self.selection_count_label.set_text(f"{selected_count} of {total_count} videos selected")
        self.select_all_btn.set_label("Deselect All" if selected_count == total_count and total_count > 0 else "Select All")
        self.download_btn.set_label(f"Download Selected ({selected_count})")
        self.download_btn.set_sensitive(selected_count > 0)

    def _on_download_clicked(self, *args):
        selected_entries = [e for e in self.entries if e.get('selected', True)]
        if not selected_entries:
            return

        mode = self.format_selector.get_selected_mode()
        quality = self.format_selector.get_selected_quality()
        audio_format = self.format_selector.get_audio_format()

        self.emit('download-playlist-requested', selected_entries, mode, quality, audio_format)


PlaylistCard = PlaylistPreviewCard
GlypdlPlaylistPreviewCard = PlaylistPreviewCard
