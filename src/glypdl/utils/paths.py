import pathlib
import gi
gi.require_version('GLib', '2.0')
from gi.repository import GLib

APP_NAME = 'glypdl'

def get_data_dir() -> pathlib.Path:
    return pathlib.Path(GLib.get_user_data_dir()) / APP_NAME

def get_bin_dir() -> pathlib.Path:
    return get_data_dir() / "bin"

def get_cache_dir() -> pathlib.Path:
    return pathlib.Path(GLib.get_user_cache_dir()) / APP_NAME

def get_config_dir() -> pathlib.Path:
    return pathlib.Path(GLib.get_user_config_dir()) / APP_NAME

def get_thumbnail_cache_dir() -> pathlib.Path:
    return get_cache_dir() / "thumbnails"

def get_temp_dir() -> pathlib.Path:
    return get_cache_dir() / "tmp"

def get_default_download_dir() -> pathlib.Path:
    downloads = GLib.get_user_special_dir(GLib.UserDirectory.DIRECTORY_DOWNLOAD)
    if downloads:
        return pathlib.Path(downloads)
    return pathlib.Path(GLib.get_home_dir()) / "Downloads"

def get_database_path() -> pathlib.Path:
    return get_data_dir() / "history.db"

def get_runtime_dir() -> pathlib.Path:
    runtime = GLib.get_user_runtime_dir()
    if runtime:
        return pathlib.Path(runtime) / APP_NAME
    return get_config_dir()

def get_ipc_socket_path() -> pathlib.Path:
    return get_runtime_dir() / "ipc.sock"

def ensure_dirs() -> None:
    dirs = [
        get_data_dir(),
        get_bin_dir(),
        get_cache_dir(),
        get_config_dir(),
        get_thumbnail_cache_dir(),
        get_temp_dir(),
        get_runtime_dir()
    ]
    for d in dirs:
        d.mkdir(parents=True, exist_ok=True)

