"""Metadata preview card showing video information and download configuration."""

import gi

gi.require_version('Gtk', '4.0')
gi.require_version('Adw', '1')
from gi.repository import Gtk, Adw, Gio, GObject, Pango

from glypdl.widgets.format_selector import FormatSelector
from glypdl.models.download import DownloadMode
from glypdl.utils.formatting import format_duration
from glypdl.utils.thumbnails import load_thumbnail_async, load_scaled_texture


class MetadataPreviewCard(Gtk.Box):
    """Card displaying fetched media metadata with thumbnail and format options prior to download."""
    __gtype_name__ = 'GlypdlMetadataPreviewCard'

    __gsignals__ = {
        'download-requested': (GObject.SignalFlags.RUN_LAST, None, (str, object, str, str)),
        'cancel-preview': (GObject.SignalFlags.RUN_LAST, None, ())
    }

    def __init__(self, metadata: dict = None, **kwargs):
        super().__init__(**kwargs)
        self.set_orientation(Gtk.Orientation.VERTICAL)
        self.add_css_class('card')
        self.set_spacing(12)
        self.set_margin_top(8)
        self.set_margin_bottom(8)
        self.set_margin_start(8)
        self.set_margin_end(8)

        self.url = ""
        self.metadata = {}

        # Top section: Thumbnail + Details
        top_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=14)
        top_box.set_margin_top(12)
        top_box.set_margin_start(12)
        top_box.set_margin_end(12)
        self.append(top_box)

        # Thumbnail wrapped in fixed frame
        thumb_frame = Gtk.Box(halign=Gtk.Align.CENTER, valign=Gtk.Align.CENTER)
        thumb_frame.set_size_request(160, 90)
        thumb_frame.set_hexpand(False)
        thumb_frame.set_vexpand(False)
        top_box.append(thumb_frame)

        self.thumbnail = Gtk.Picture()
        self.thumbnail.set_size_request(160, 90)
        self.thumbnail.set_can_shrink(True)
        self.thumbnail.set_content_fit(Gtk.ContentFit.COVER)
        self.thumbnail.set_hexpand(False)
        self.thumbnail.set_vexpand(False)
        self.thumbnail.add_css_class('card')
        thumb_frame.append(self.thumbnail)

        # Info vbox
        info_vbox = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)
        info_vbox.set_hexpand(True)
        info_vbox.set_valign(Gtk.Align.CENTER)
        top_box.append(info_vbox)

        self.title_label = Gtk.Label(halign=Gtk.Align.START, xalign=0.0)
        self.title_label.add_css_class('title-3')
        self.title_label.set_wrap(True)
        self.title_label.set_wrap_mode(Pango.WrapMode.WORD_CHAR)
        self.title_label.set_max_width_chars(50)
        info_vbox.append(self.title_label)

        self.uploader_label = Gtk.Label(halign=Gtk.Align.START, xalign=0.0)
        self.uploader_label.add_css_class('dim-label')
        info_vbox.append(self.uploader_label)

        self.meta_badge_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        info_vbox.append(self.meta_badge_box)

        self.duration_label = Gtk.Label(halign=Gtk.Align.START, xalign=0.0)
        self.duration_label.add_css_class('dim-label')
        self.meta_badge_box.append(self.duration_label)

        self.site_label = Gtk.Label(halign=Gtk.Align.START, xalign=0.0)
        self.site_label.add_css_class('dim-label')
        self.meta_badge_box.append(self.site_label)

        # Format Selector widget
        self.format_selector = FormatSelector()
        self.format_selector.set_margin_start(12)
        self.format_selector.set_margin_end(12)
        self.append(self.format_selector)

        # Action Buttons Box
        action_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=12)
        action_box.set_halign(Gtk.Align.END)
        action_box.set_margin_bottom(12)
        action_box.set_margin_end(12)
        self.append(action_box)

        self.cancel_btn = Gtk.Button(label='Dismiss')
        self.cancel_btn.connect('clicked', lambda _: self.emit('cancel-preview'))
        action_box.append(self.cancel_btn)

        self.download_btn = Gtk.Button(label='Download')
        self.download_btn.add_css_class('suggested-action')
        self.download_btn.connect('clicked', self._on_download_clicked)
        action_box.append(self.download_btn)

        if metadata:
            self.set_metadata(metadata)

    def _on_download_clicked(self, *args):
        mode = self.format_selector.get_selected_mode()
        quality = self.format_selector.get_selected_quality()
        audio_format = self.format_selector.get_audio_format()
        self.emit('download-requested', self.url, mode, quality, audio_format)

    def set_metadata(self, metadata: dict):
        self.metadata = metadata or {}
        self.url = metadata.get('original_url') or metadata.get('webpage_url') or ''
        
        title = metadata.get('title', 'Unknown Title')
        self.title_label.set_text(title)
        
        uploader = metadata.get('uploader') or metadata.get('channel') or 'Unknown Uploader'
        self.uploader_label.set_text(uploader)

        duration = metadata.get('duration', 0)
        self.duration_label.set_text(f"Duration: {format_duration(duration)}" if duration else "")

        extractor = metadata.get('extractor_key') or metadata.get('extractor') or ''
        self.site_label.set_text(f"• {extractor}" if extractor else "")

        # Handle thumbnail loading
        thumb_path = metadata.get('thumbnail_path')
        if thumb_path:
            self.set_thumbnail_file(thumb_path)
        else:
            thumb_url = metadata.get('thumbnail')
            if thumb_url:
                load_thumbnail_async(thumb_url, self._on_thumbnail_loaded)

        self.format_selector.set_formats(metadata)

    def _on_thumbnail_loaded(self, local_path):
        if local_path:
            self.set_thumbnail_file(local_path)

    def set_thumbnail_file(self, file_path: str):
        if not file_path:
            return
        texture = load_scaled_texture(file_path, 160, 90)
        if texture:
            self.thumbnail.set_paintable(texture)
        else:
            try:
                gfile = Gio.File.new_for_path(file_path)
                self.thumbnail.set_file(gfile)
            except Exception:
                pass

    def clear(self):
        self.url = ""
        self.metadata = {}
        self.title_label.set_text("")
        self.uploader_label.set_text("")
        self.duration_label.set_text("")
        self.site_label.set_text("")
        self.thumbnail.set_paintable(None)
        self.format_selector.set_formats({})


ProgressCard = MetadataPreviewCard
GlypdlProgressCard = MetadataPreviewCard
