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

def get_ipc_socket_candidates() -> list[pathlib.Path]:
    """Return all candidate socket paths in priority order across host and Flatpak sandboxes."""
    candidates = []
    
    # 1. Standard runtime dir (host and shared Flatpak with xdg-run/glypdl:create)
    candidates.append(get_ipc_socket_path())
    
    # 2. Host user runtime directory check for Flatpak sandbox runtime mounts
    runtime = GLib.get_user_runtime_dir()
    if runtime:
        rt_path = pathlib.Path(runtime)
        known_flatpak_ids = [
            "io.github.sureshsoudararajan.Glypdl",
            "io.github.suresh.Glypdl",
        ]
        for app_id in known_flatpak_ids:
            sock = rt_path / "app" / app_id / APP_NAME / "ipc.sock"
            if sock not in candidates:
                candidates.append(sock)
                
        # Also check any app subdirectory matching glypdl
        app_dir = rt_path / "app"
        if app_dir.is_dir():
            for p in app_dir.glob("*[Gg]lypdl*/glypdl/ipc.sock"):
                if p not in candidates:
                    candidates.append(p)

    # 3. Flatpak user home var directories
    home = pathlib.Path.home()
    for app_id in ["io.github.sureshsoudararajan.Glypdl", "io.github.suresh.Glypdl"]:
        var_sock = home / ".var" / "app" / app_id / "config" / APP_NAME / "ipc.sock"
        if var_sock not in candidates:
            candidates.append(var_sock)

    # 4. Fallback config dir
    config_sock = get_config_dir() / "ipc.sock"
    if config_sock not in candidates:
        candidates.append(config_sock)

    return candidates

def get_active_ipc_socket_path() -> pathlib.Path:
    """Return the first existing socket path from candidates, or the default path if none exists."""
    for sock in get_ipc_socket_candidates():
        if sock.exists():
            return sock
    return get_ipc_socket_path()

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

