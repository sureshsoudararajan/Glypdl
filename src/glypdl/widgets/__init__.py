"""UI widgets for Glypdl."""

from glypdl.widgets.url_input import UrlInput, GlypdlUrlInput
from glypdl.widgets.format_selector import FormatSelector, GlypdlFormatSelector
from glypdl.widgets.download_card import DownloadCard, GlypdlDownloadCard
from glypdl.widgets.progress_card import MetadataPreviewCard, ProgressCard, GlypdlProgressCard
from glypdl.widgets.playlist_card import PlaylistPreviewCard, PlaylistCard, GlypdlPlaylistPreviewCard

__all__ = [
    'UrlInput', 'GlypdlUrlInput',
    'FormatSelector', 'GlypdlFormatSelector',
    'DownloadCard', 'GlypdlDownloadCard',
    'MetadataPreviewCard', 'ProgressCard', 'GlypdlProgressCard',
    'PlaylistPreviewCard', 'PlaylistCard', 'GlypdlPlaylistPreviewCard'
]
