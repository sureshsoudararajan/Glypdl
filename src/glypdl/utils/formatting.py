"""Formatting and progress parsing utilities for Glypdl."""

import re
from typing import Dict, Any


def format_size(bytes_val: int) -> str:
    """Format bytes into human-readable string (e.g. 1.42 GB, 340 MB)."""
    if bytes_val <= 0:
        return "0 B"
    units = ["B", "KB", "MB", "GB", "TB", "PB"]
    unit_index = 0
    size = float(bytes_val)
    while size >= 1024.0 and unit_index < len(units) - 1:
        size /= 1024.0
        unit_index += 1
    
    if unit_index == 0:
        return f"{int(size)} {units[unit_index]}"
    return f"{size:.2f} {units[unit_index]}"


def format_speed(bytes_per_sec: float) -> str:
    """Format bytes per second into human-readable speed string (e.g. 9.70 MB/s)."""
    if bytes_per_sec <= 0:
        return "0 B/s"
    return f"{format_size(int(bytes_per_sec))}/s"


def format_duration(seconds: int) -> str:
    """Format seconds into HH:MM:SS or MM:SS."""
    if seconds <= 0:
        return "0:00"
    
    h = seconds // 3600
    m = (seconds % 3600) // 60
    s = seconds % 60
    
    if h > 0:
        return f"{h}:{m:02d}:{s:02d}"
    return f"{m}:{s:02d}"


def format_eta(seconds: int) -> str:
    """Format ETA in seconds into human-readable string (e.g. 41s, 2m 13s, 1h 5m)."""
    if seconds <= 0:
        return "0s"
    
    h = seconds // 3600
    m = (seconds % 3600) // 60
    s = seconds % 60
    
    if h > 0:
        return f"{h}h {m}m"
    if m > 0:
        return f"{m}m {s}s"
    return f"{s}s"


def parse_progress_line(line: str) -> Dict[str, Any]:
    """Parse yt-dlp stdout progress line and extract percent, downloaded bytes, total bytes, speed, eta."""
    result = {
        "percent": 0.0,
        "downloaded_bytes": 0,
        "total_bytes": 0,
        "speed": "",
        "eta": "",
        "fragment_index": 0,
        "fragment_count": 0,
        "status": ""
    }
    
    line = line.strip()
    if not line:
        return result
        
    if line.startswith("[download]"):
        result["status"] = "downloading"
        
        # Parse percentage
        pct_match = re.search(r'(\d+\.?\d*)%', line)
        if pct_match:
            try:
                result["percent"] = float(pct_match.group(1))
            except ValueError:
                pass
                
        # Parse ETA
        eta_match = re.search(r'ETA\s+(\d+:\d+(?::\d+)?)', line)
        if eta_match:
            result["eta"] = eta_match.group(1)
            
        # Parse speed
        speed_match = re.search(r'at\s+([^ ]+/s)', line)
        if speed_match:
            result["speed"] = speed_match.group(1).replace('iB', 'B')
            
        # Parse fragments
        frag_match = re.search(r'\(frag\s+(\d+)/(\d+)\)', line)
        if frag_match:
            try:
                result["fragment_index"] = int(frag_match.group(1))
                result["fragment_count"] = int(frag_match.group(2))
            except ValueError:
                pass

        # Parse downloaded and total sizes
        mults = {'B': 1, 'KB': 1024, 'MB': 1024**2, 'GB': 1024**3, 'TB': 1024**4}
        
        # Check "X of Y" e.g. "1.42MiB of 15.00MiB"
        dl_tot_m = re.search(r'([\d.]+)\s*([KMGT]?i?B)\s+of\s+~?([\d.]+)\s*([KMGT]?i?B)', line)
        if dl_tot_m:
            try:
                dl_val, dl_unit, tot_val, tot_unit = dl_tot_m.groups()
                result["downloaded_bytes"] = int(float(dl_val) * mults.get(dl_unit.upper().replace('I', ''), 1))
                result["total_bytes"] = int(float(tot_val) * mults.get(tot_unit.upper().replace('I', ''), 1))
            except (ValueError, TypeError):
                pass
        else:
            # Check "of ~1.82GiB"
            tot_m = re.search(r'of\s+~?([\d.]+)\s*([KMGT]?i?B)', line)
            if tot_m:
                try:
                    val = float(tot_m.group(1))
                    unit = tot_m.group(2).upper().replace('I', '')
                    total = int(val * mults.get(unit, 1))
                    result["total_bytes"] = total
                    if result["percent"] > 0:
                        result["downloaded_bytes"] = int(total * (result["percent"] / 100.0))
                except (ValueError, TypeError):
                    pass

    elif line.startswith("[Merger]"):
        result["status"] = "merging"
    elif line.startswith("[ExtractAudio]"):
        result["status"] = "extracting_audio"
    elif line.startswith("[ffmpeg]"):
        result["status"] = "converting"
        
    return result
