"""Download card widget displaying active, queued, completed or failed download items."""

import gi

gi.require_version('Gtk', '4.0')
gi.require_version('Adw', '1')
from gi.repository import Gtk, Adw, Gio, GObject, Pango

from glypdl.models.download import DownloadItem, DownloadState, DownloadMode
from glypdl.utils.formatting import format_size, format_speed, format_eta
from glypdl.utils.thumbnails import load_thumbnail_async, load_scaled_texture


class DownloadCard(Gtk.Box):
    """Card widget representing an individual download with progress, statistics and controls."""
    __gtype_name__ = 'GlypdlDownloadCard'

    __gsignals__ = {
        'cancel-requested': (GObject.SignalFlags.RUN_LAST, None, (str,)),
        'pause-requested': (GObject.SignalFlags.RUN_LAST, None, (str,)),
        'resume-requested': (GObject.SignalFlags.RUN_LAST, None, (str,)),
        'retry-requested': (GObject.SignalFlags.RUN_LAST, None, (str,)),
        'open-file-requested': (GObject.SignalFlags.RUN_LAST, None, (str,)),
        'open-folder-requested': (GObject.SignalFlags.RUN_LAST, None, (str,)),
        'remove-requested': (GObject.SignalFlags.RUN_LAST, None, (str,)),
        'copy-url-requested': (GObject.SignalFlags.RUN_LAST, None, (str,))
    }

    def __init__(self, download_item: DownloadItem = None, **kwargs):
        super().__init__(**kwargs)
        self.set_orientation(Gtk.Orientation.VERTICAL)
        self.add_css_class('card')
        self.set_margin_top(4)
        self.set_margin_bottom(4)
        self.set_margin_start(4)
        self.set_margin_end(4)

        self.download_id = ""
        self._bound_item = None
        self._notify_handlers = []

        # Main horizontal container
        hbox = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=14)
        hbox.set_margin_top(10)
        hbox.set_margin_bottom(10)
        hbox.set_margin_start(10)
        hbox.set_margin_end(10)
        self.append(hbox)

        # Thumbnail wrapped in fixed-size box so it never expands or stretches the window
        thumb_frame = Gtk.Box(halign=Gtk.Align.CENTER, valign=Gtk.Align.CENTER)
        thumb_frame.set_size_request(120, 68)
        thumb_frame.set_hexpand(False)
        thumb_frame.set_vexpand(False)
        hbox.append(thumb_frame)

        self.thumbnail = Gtk.Picture()
        self.thumbnail.set_size_request(120, 68)
        self.thumbnail.set_can_shrink(True)
        self.thumbnail.set_content_fit(Gtk.ContentFit.COVER)
        self.thumbnail.set_hexpand(False)
        self.thumbnail.set_vexpand(False)
        self.thumbnail.add_css_class('card')
        thumb_frame.append(self.thumbnail)

        # Central details vbox with hexpand to lock outer dimensions
        vbox = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)
        vbox.set_hexpand(True)
        vbox.set_valign(Gtk.Align.CENTER)
        hbox.append(vbox)

        self.title_label = Gtk.Label(halign=Gtk.Align.START, xalign=0.0)
        self.title_label.add_css_class('heading')
        self.title_label.set_ellipsize(Pango.EllipsizeMode.END)
        self.title_label.set_hexpand(True)
        vbox.append(self.title_label)

        self.info_sublabel = Gtk.Label(halign=Gtk.Align.START, xalign=0.0)
        self.info_sublabel.add_css_class('dim-label')
        self.info_sublabel.set_ellipsize(Pango.EllipsizeMode.END)
        self.info_sublabel.set_hexpand(True)
        vbox.append(self.info_sublabel)

        # Progress bar
        self.progress_bar = Gtk.ProgressBar()
        self.progress_bar.set_margin_top(2)
        self.progress_bar.set_margin_bottom(2)
        vbox.append(self.progress_bar)

        # Stats row with fixed sizing on labels to prevent container jitter
        self.stats_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        vbox.append(self.stats_box)

        self.progress_text = Gtk.Label(halign=Gtk.Align.START, xalign=0.0)
        self.progress_text.add_css_class('dim-label')
        self.progress_text.add_css_class('numeric')
        self.progress_text.set_size_request(210, -1)
        self.stats_box.append(self.progress_text)

        self.speed_eta_text = Gtk.Label(halign=Gtk.Align.END, xalign=1.0)
        self.speed_eta_text.add_css_class('dim-label')
        self.speed_eta_text.add_css_class('numeric')
        self.speed_eta_text.set_hexpand(True)
        self.stats_box.append(self.speed_eta_text)

        # Status label (for queued, merging, failed, etc.)
        self.status_label = Gtk.Label(halign=Gtk.Align.START, xalign=0.0)
        self.status_label.add_css_class('dim-label')
        self.status_label.set_ellipsize(Pango.EllipsizeMode.END)
        vbox.append(self.status_label)

        # Right Action Buttons
        self.btn_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        self.btn_box.set_valign(Gtk.Align.CENTER)
        hbox.append(self.btn_box)

        self.open_file_btn = Gtk.Button(label='Play')
        self.open_file_btn.set_tooltip_text('Play / Open Video')
        self.open_file_btn.connect('clicked', lambda _: self.emit('open-file-requested', self.download_id))
        self.btn_box.append(self.open_file_btn)

        self.open_folder_btn = Gtk.Button(icon_name='folder-open-symbolic')
        self.open_folder_btn.set_tooltip_text('Open Containing Folder')
        self.open_folder_btn.connect('clicked', lambda _: self.emit('open-folder-requested', self.download_id))
        self.btn_box.append(self.open_folder_btn)

        self.retry_btn = Gtk.Button(icon_name='view-refresh-symbolic')
        self.retry_btn.set_tooltip_text('Retry Download')
        self.retry_btn.connect('clicked', lambda _: self.emit('retry-requested', self.download_id))
        self.btn_box.append(self.retry_btn)

        self.cancel_btn = Gtk.Button(icon_name='process-stop-symbolic')
        self.cancel_btn.set_tooltip_text('Cancel Download')
        self.cancel_btn.connect('clicked', lambda _: self.emit('cancel-requested', self.download_id))
        self.btn_box.append(self.cancel_btn)

        self.dismiss_btn = Gtk.Button(icon_name='window-close-symbolic')
        self.dismiss_btn.set_tooltip_text('Dismiss')
        self.dismiss_btn.connect('clicked', lambda _: self.emit('remove-requested', self.download_id))
        self.btn_box.append(self.dismiss_btn)

        if download_item:
            self.bind_download_item(download_item)

    def bind_download_item(self, download_item: DownloadItem):
        """Bind to DownloadItem properties and handle updates reactively."""
        if self._bound_item:
            for handler_id in self._notify_handlers:
                self._bound_item.disconnect(handler_id)
            self._notify_handlers = []

        self._bound_item = download_item
        if not download_item:
            return

        self.download_id = download_item.id
        self.title_label.set_text(download_item.title or download_item.url)

        # Mode text
        mode_str = "Video + Audio"
        if download_item.mode == DownloadMode.VIDEO:
            mode_str = "Video"
        elif download_item.mode == DownloadMode.AUDIO:
            mode_str = f"Audio ({download_item.audio_format or 'Best'})"

        quality_str = f" • {download_item.quality}" if download_item.quality and download_item.mode != DownloadMode.AUDIO else ""
        uploader_str = f"{download_item.uploader} • " if download_item.uploader else ""
        self.info_sublabel.set_text(f"{uploader_str}{mode_str}{quality_str}")

        # Set thumbnail
        if download_item.thumbnail_path:
            self.set_thumbnail(download_item.thumbnail_path)
        elif download_item.thumbnail_url:
            load_thumbnail_async(download_item.thumbnail_url, self._on_thumb_loaded)

        self._update_state(download_item)
        self._update_progress(download_item)

        h1 = download_item.connect('notify::progress', lambda obj, pspec: self._update_progress(obj))
        h2 = download_item.connect('notify::state', lambda obj, pspec: self._update_state(obj))
        h3 = download_item.connect('notify::speed', lambda obj, pspec: self._update_progress(obj))
        h4 = download_item.connect('notify::eta', lambda obj, pspec: self._update_progress(obj))
        h5 = download_item.connect('notify::downloaded-bytes', lambda obj, pspec: self._update_progress(obj))
        h6 = download_item.connect('notify::total-bytes', lambda obj, pspec: self._update_progress(obj))
        h7 = download_item.connect('notify::title', lambda obj, pspec: self.title_label.set_text(obj.title or obj.url))

        self._notify_handlers.extend([h1, h2, h3, h4, h5, h6, h7])

    def _on_thumb_loaded(self, local_path):
        if local_path:
            self.set_thumbnail(local_path)
            if self._bound_item:
                self._bound_item.thumbnail_path = local_path

    def _update_progress(self, item: DownloadItem):
        fraction = max(0.0, min(1.0, item.progress / 100.0))
        self.progress_bar.set_fraction(fraction)

        # Downloaded vs Total size text e.g. "1.42 GB / 1.82 GB (78.4%)"
        if item.total_bytes > 0 and item.downloaded_bytes > 0:
            dl_text = f"{format_size(item.downloaded_bytes)} / {format_size(item.total_bytes)} ({item.progress:.1f}%)"
        elif item.total_bytes > 0:
            dl_text = f"{format_size(item.total_bytes)} ({item.progress:.1f}%)"
        elif item.downloaded_bytes > 0:
            dl_text = f"{format_size(item.downloaded_bytes)} ({item.progress:.1f}%)"
        else:
            dl_text = f"{item.progress:.1f}%"

        self.progress_text.set_text(dl_text)

        # Speed and ETA text
        speed_str = format_speed(item.speed) if item.speed > 0 else ""
        eta_str = f"ETA {format_eta(item.eta)}" if item.eta > 0 else ""
        if speed_str and eta_str:
            self.speed_eta_text.set_text(f"{speed_str} • {eta_str}")
        elif speed_str:
            self.speed_eta_text.set_text(speed_str)
        elif eta_str:
            self.speed_eta_text.set_text(eta_str)
        else:
            self.speed_eta_text.set_text("")

    def _update_state(self, item: DownloadItem):
        state = item.state

        is_downloading = state == DownloadState.DOWNLOADING
        is_processing = state in (DownloadState.MERGING, DownloadState.CONVERTING, DownloadState.PROCESSING)
        is_queued = state == DownloadState.QUEUED
        is_completed = state == DownloadState.COMPLETED
        is_failed = state == DownloadState.FAILED
        is_cancelled = state == DownloadState.CANCELLED

        self.progress_bar.set_visible(is_downloading)
        self.stats_box.set_visible(is_downloading)

        # Status label
        if is_queued:
            self.status_label.set_visible(True)
            self.status_label.set_text("Queued — waiting for available download slot")
        elif is_processing:
            self.status_label.set_visible(True)
            if state == DownloadState.MERGING:
                self.status_label.set_text("Merging video and audio streams…")
            elif state == DownloadState.CONVERTING:
                self.status_label.set_text("Converting media format with ffmpeg…")
            else:
                self.status_label.set_text("Processing completed download…")
        elif is_failed:
            self.status_label.set_visible(True)
            err = item.error_message or "Download failed"
            self.status_label.set_text(f"Failed: {err}")
        elif is_cancelled:
            self.status_label.set_visible(True)
            self.status_label.set_text("Cancelled")
        elif is_completed:
            self.status_label.set_visible(True)
            self.status_label.set_text("Download Completed")
        else:
            self.status_label.set_visible(not is_downloading)
            self.status_label.set_text(str(state.value).capitalize() if hasattr(state, 'value') else str(state))

        # Button visibility
        self.open_file_btn.set_visible(is_completed)
        self.open_folder_btn.set_visible(is_completed)
        self.retry_btn.set_visible(is_failed or is_cancelled)
        self.cancel_btn.set_visible(is_downloading or is_queued or is_processing)
        self.dismiss_btn.set_visible(is_completed or is_failed or is_cancelled)

    def set_thumbnail(self, file_path: str):
        if not file_path:
            return
        texture = load_scaled_texture(file_path, 120, 68)
        if texture:
            self.thumbnail.set_paintable(texture)
        else:
            try:
                gfile = Gio.File.new_for_path(file_path)
                self.thumbnail.set_file(gfile)
            except Exception:
                pass


GlypdlDownloadCard = DownloadCard
