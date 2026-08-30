"""URL input widget for entering media links."""

import gi

gi.require_version('Gtk', '4.0')
gi.require_version('Adw', '1')
from gi.repository import Gtk, Adw, GObject


class UrlInput(Gtk.Box):
    """Clean URL entry bar with add button and keyboard shortcut support."""
    __gtype_name__ = 'GlypdlUrlInput'

    __gsignals__ = {
        'url-submitted': (GObject.SignalFlags.RUN_LAST, None, (str,))
    }

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.set_orientation(Gtk.Orientation.VERTICAL)
        self.set_spacing(12)

        clamp = Adw.Clamp(maximum_size=700)
        self.append(clamp)

        hbox = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        clamp.set_child(hbox)

        self.entry = Gtk.Entry()
        self.entry.set_placeholder_text('Paste video or playlist URL...')
        self.entry.set_hexpand(True)
        self.entry.connect('activate', self._on_submit)
        hbox.append(self.entry)

        self.add_button = Gtk.Button(icon_name='list-add-symbolic')
        self.add_button.set_tooltip_text('Fetch Media Information')
        self.add_button.add_css_class('suggested-action')
        self.add_button.connect('clicked', self._on_submit)
        hbox.append(self.add_button)

    def _on_submit(self, *args):
        url = self.entry.get_text().strip()
        if url and (url.startswith('http://') or url.startswith('https://')):
            self.emit('url-submitted', url)
            self.entry.set_text('')

    def grab_focus(self):
        return self.entry.grab_focus()

    def get_text(self) -> str:
        return self.entry.get_text()

    def set_text(self, text: str):
        self.entry.set_text(text)


GlypdlUrlInput = UrlInput
