"""Service for persistent SQLite download history."""

import sqlite3
import os
from pathlib import Path
from gi.repository import GLib

from glypdl.utils.paths import get_database_path


class HistoryService:
    """Manages SQLite storage for completed, failed, and past downloads."""

    def __init__(self, db_path=None):
        if db_path is None:
            self.db_path = str(get_database_path())
        else:
            self.db_path = str(db_path)

        os.makedirs(os.path.dirname(self.db_path), exist_ok=True)
        self._init_db()

    def _init_db(self):
        conn = sqlite3.connect(self.db_path)
        try:
            cursor = conn.cursor()
            cursor.execute('''
                CREATE TABLE IF NOT EXISTS history (
                    id TEXT PRIMARY KEY,
                    url TEXT,
                    title TEXT,
                    uploader TEXT,
                    thumbnail_url TEXT,
                    thumbnail_path TEXT,
                    download_path TEXT,
                    format TEXT,
                    file_size INTEGER,
                    status TEXT,
                    timestamp TEXT,
                    duration INTEGER,
                    mode TEXT,
                    quality TEXT
                )
            ''')
            # Check if thumbnail_url column exists for existing dbs
            cursor.execute("PRAGMA table_info(history)")
            cols = [row[1] for row in cursor.fetchall()]
            if 'thumbnail_url' not in cols:
                try:
                    cursor.execute("ALTER TABLE history ADD COLUMN thumbnail_url TEXT")
                except sqlite3.OperationalError:
                    pass
            conn.commit()
        finally:
            conn.close()

    def add_entry(self, download_item):
        """Insert or update a history record from a DownloadItem."""
        entry_dict = download_item.to_dict() if hasattr(download_item, 'to_dict') else download_item
        out_path = entry_dict.get('download_path') or entry_dict.get('output_path')
        
        # Determine actual file size on disk if available
        file_size = entry_dict.get('file_size') or entry_dict.get('total_bytes') or entry_dict.get('downloaded_bytes') or 0
        if out_path and os.path.isfile(out_path):
            try:
                disk_size = os.path.getsize(out_path)
                if disk_size > 0:
                    file_size = disk_size
            except Exception:
                pass

        conn = sqlite3.connect(self.db_path)
        try:
            cursor = conn.cursor()
            cursor.execute('''
                INSERT OR REPLACE INTO history 
                (id, url, title, uploader, thumbnail_url, thumbnail_path, download_path, format, file_size, status, timestamp, duration, mode, quality)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ''', (
                entry_dict.get('id'),
                entry_dict.get('url'),
                entry_dict.get('title'),
                entry_dict.get('uploader'),
                entry_dict.get('thumbnail_url'),
                entry_dict.get('thumbnail_path'),
                out_path,
                entry_dict.get('format') or entry_dict.get('format_id'),
                file_size,
                entry_dict.get('status') or (entry_dict.get('state') if isinstance(entry_dict.get('state'), str) else getattr(entry_dict.get('state'), 'name', 'COMPLETED')),
                entry_dict.get('timestamp') or entry_dict.get('completed_at') or entry_dict.get('created_at'),
                entry_dict.get('duration'),
                str(entry_dict.get('mode') or ''),
                entry_dict.get('quality')
            ))
            conn.commit()
        finally:
            conn.close()

    def get_all(self) -> list:
        conn = sqlite3.connect(self.db_path)
        try:
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            cursor.execute('SELECT * FROM history ORDER BY timestamp DESC')
            return [dict(row) for row in cursor.fetchall()]
        finally:
            conn.close()

    def get_by_status(self, status: str) -> list:
        conn = sqlite3.connect(self.db_path)
        try:
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            cursor.execute('SELECT * FROM history WHERE status = ? ORDER BY timestamp DESC', (status,))
            return [dict(row) for row in cursor.fetchall()]
        finally:
            conn.close()

    def remove_entry(self, entry_id: str):
        conn = sqlite3.connect(self.db_path)
        try:
            cursor = conn.cursor()
            cursor.execute('DELETE FROM history WHERE id = ?', (entry_id,))
            conn.commit()
        finally:
            conn.close()

    def clear_all(self):
        conn = sqlite3.connect(self.db_path)
        try:
            cursor = conn.cursor()
            cursor.execute('DELETE FROM history')
            conn.commit()
        finally:
            conn.close()

    def search(self, query: str) -> list:
        conn = sqlite3.connect(self.db_path)
        try:
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            search_term = f'%{query}%'
            cursor.execute('''
                SELECT * FROM history 
                WHERE title LIKE ? OR url LIKE ? OR uploader LIKE ?
                ORDER BY timestamp DESC
            ''', (search_term, search_term, search_term))
            return [dict(row) for row in cursor.fetchall()]
        finally:
            conn.close()

    def update_thumbnail_path(self, entry_id: str, path: str):
        conn = sqlite3.connect(self.db_path)
        try:
            cursor = conn.cursor()
            cursor.execute('UPDATE history SET thumbnail_path = ? WHERE id = ?', (path, entry_id))
            conn.commit()
        finally:
            conn.close()

    def update_download_path(self, entry_id: str, path: str):
        conn = sqlite3.connect(self.db_path)
        try:
            cursor = conn.cursor()
            cursor.execute('UPDATE history SET download_path = ? WHERE id = ?', (path, entry_id))
            conn.commit()
        finally:
            conn.close()

    def update_file_size(self, entry_id: str, size: int):
        conn = sqlite3.connect(self.db_path)
        try:
            cursor = conn.cursor()
            cursor.execute('UPDATE history SET file_size = ? WHERE id = ?', (size, entry_id))
            conn.commit()
        finally:
            conn.close()

    def update_status(self, entry_id: str, status: str):
        conn = sqlite3.connect(self.db_path)
        try:
            cursor = conn.cursor()
            cursor.execute('UPDATE history SET status = ? WHERE id = ?', (status, entry_id))
            conn.commit()
        finally:
            conn.close()
