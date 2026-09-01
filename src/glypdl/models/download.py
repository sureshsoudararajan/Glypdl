import gi
import uuid
import datetime
import enum
from typing import Dict, Any, Optional

gi.require_version('GObject', '2.0')
from gi.repository import GObject

class DownloadState(enum.Enum):
    FETCHING_INFO = "fetching_info"
    QUEUED = "queued"
    DOWNLOADING = "downloading"
    PAUSED = "paused"
    PROCESSING = "processing"
    MERGING = "merging"
    CONVERTING = "converting"
    COMPLETED = "completed"
    FAILED = "failed"
    CANCELLED = "cancelled"

class DownloadMode(enum.Enum):
    VIDEO = "video"
    AUDIO = "audio"
    VIDEO_AUDIO = "video_audio"

class DownloadItem(GObject.Object):
    __gtype_name__ = 'GlypdlDownloadItem'

    # Using object type for enum compatibility in PyGObject properties
    id = GObject.Property(type=str)
    url = GObject.Property(type=str)
    title = GObject.Property(type=str)
    uploader = GObject.Property(type=str)
    duration = GObject.Property(type=int, default=0)
    thumbnail_url = GObject.Property(type=str)
    thumbnail_path = GObject.Property(type=str)
    
    state = GObject.Property(type=object)
    mode = GObject.Property(type=object)
    
    quality = GObject.Property(type=str)
    audio_format = GObject.Property(type=str)
    format_id = GObject.Property(type=str)
    output_path = GObject.Property(type=str)
    download_dir = GObject.Property(type=str)
    filename_template = GObject.Property(type=str)
    cookie_file = GObject.Property(type=str)
    cookies_from_browser = GObject.Property(type=str)
    
    progress = GObject.Property(type=float, default=0.0)
    downloaded_bytes = GObject.Property(type=int, default=0)
    total_bytes = GObject.Property(type=int, default=0)
    speed = GObject.Property(type=float, default=0.0)
    average_speed = GObject.Property(type=float, default=0.0)
    eta = GObject.Property(type=int, default=0)
    elapsed = GObject.Property(type=float, default=0.0)
    fragment_index = GObject.Property(type=int, default=0)
    fragment_count = GObject.Property(type=int, default=0)
    error_message = GObject.Property(type=str)
    created_at = GObject.Property(type=str)
    completed_at = GObject.Property(type=str)
    
    extra_args = GObject.Property(type=object)

    def __init__(self, **kwargs):
        super().__init__()
        # Set defaults
        self.id = str(uuid.uuid4())
        self.state = DownloadState.FETCHING_INFO
        self.mode = DownloadMode.VIDEO
        self.created_at = datetime.datetime.now(datetime.timezone.utc).isoformat()
        self.extra_args = []
        self.process = None # Not a GObject property
        
        # Apply kwargs
        for key, value in kwargs.items():
            if hasattr(self, key):
                setattr(self, key, value)

    def update_progress(self, percent: float, downloaded: int, total: int, speed: float, eta: int):
        """Update progress-related properties."""
        self.progress = percent
        self.downloaded_bytes = downloaded
        self.total_bytes = total
        self.speed = speed
        self.eta = eta

    def mark_completed(self, output_path: str):
        """Mark download as completed, setting output_path BEFORE firing state transition."""
        self.output_path = output_path
        self.progress = 100.0
        self.completed_at = datetime.datetime.now(datetime.timezone.utc).isoformat()
        self.state = DownloadState.COMPLETED

    def mark_failed(self, error_message: str):
        """Mark download as failed."""
        self.error_message = error_message
        self.completed_at = datetime.datetime.now(datetime.timezone.utc).isoformat()
        self.state = DownloadState.FAILED

    def mark_cancelled(self):
        """Mark download as cancelled."""
        self.completed_at = datetime.datetime.now(datetime.timezone.utc).isoformat()
        self.state = DownloadState.CANCELLED

    def to_dict(self) -> dict:
        """Serialize properties to a dictionary, omitting transient state like process."""
        return {
            'id': self.id,
            'url': self.url,
            'title': self.title,
            'uploader': self.uploader,
            'duration': self.duration,
            'thumbnail_url': self.thumbnail_url,
            'thumbnail_path': self.thumbnail_path,
            'state': self.state.name if self.state else None,
            'mode': self.mode.name if self.mode else None,
            'quality': self.quality,
            'audio_format': self.audio_format,
            'format_id': self.format_id,
            'output_path': self.output_path,
            'download_path': self.output_path,
            'download_dir': self.download_dir,
            'filename_template': self.filename_template,
            'progress': self.progress,
            'downloaded_bytes': self.downloaded_bytes,
            'total_bytes': self.total_bytes,
            'speed': self.speed,
            'average_speed': self.average_speed,
            'eta': self.eta,
            'elapsed': self.elapsed,
            'fragment_index': self.fragment_index,
            'fragment_count': self.fragment_count,
            'error_message': self.error_message,
            'created_at': self.created_at,
            'completed_at': self.completed_at,
            'extra_args': self.extra_args
        }
