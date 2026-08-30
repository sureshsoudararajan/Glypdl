using System.Globalization;
using System.Text.RegularExpressions;

namespace Glypdl.Windows.Utilities;

public class ProgressInfo
{
    public double? Percent { get; set; }
    public long? DownloadedBytes { get; set; }
    public long? TotalBytes { get; set; }
    public double? SpeedBytesPerSec { get; set; }
    public int? EtaSeconds { get; set; }
    public string? Status { get; set; }
}

public static class FormattingUtils
{
    private static readonly Regex ProgressRegex = new(
        @"\[download\]\s+(\d+(?:\.\d+)?)%\s+of\s+~?\s*(\d+(?:\.\d+)?\s*[KMGT]?i?B)(?:\s+at\s+(\d+(?:\.\d+)?\s*[KMGT]?i?B/s))?(?:\s+ETA\s+(\d+:\d+|\d+s))?",
        RegexOptions.Compiled | RegexOptions.IgnoreCase
    );

    public static string FormatSize(long bytes)
    {
        if (bytes <= 0) return "0 B";
        string[] units = { "B", "KB", "MB", "GB", "TB" };
        int order = 0;
        double len = bytes;
        while (len >= 1024 && order < units.Length - 1)
        {
            order++;
            len /= 1024;
        }
        return $"{len:0.##} {units[order]}";
    }

    public static string FormatSpeed(double bytesPerSec)
    {
        if (bytesPerSec <= 0) return "-- MB/s";
        return $"{FormatSize((long)bytesPerSec)}/s";
    }

    public static string FormatDuration(int totalSeconds)
    {
        if (totalSeconds <= 0) return "0:00";
        var span = TimeSpan.FromSeconds(totalSeconds);
        return span.Hours > 0
            ? $"{span.Hours}:{span.Minutes:D2}:{span.Seconds:D2}"
            : $"{span.Minutes}:{span.Seconds:D2}";
    }

    public static string FormatEta(int seconds)
    {
        if (seconds <= 0) return "--";
        if (seconds < 60) return $"{seconds}s";
        if (seconds < 3600) return $"{seconds / 60}m {seconds % 60}s";
        return $"{seconds / 3600}h {(seconds % 3600) / 60}m";
    }

    public static long ParseSizeToBytes(string sizeStr)
    {
        if (string.IsNullOrWhiteSpace(sizeStr)) return 0;
        sizeStr = sizeStr.Trim().ToUpperInvariant();
        double multiplier = 1;
        if (sizeStr.EndsWith("KIB") || sizeStr.EndsWith("KB") || sizeStr.EndsWith("K")) multiplier = 1024;
        else if (sizeStr.EndsWith("MIB") || sizeStr.EndsWith("MB") || sizeStr.EndsWith("M")) multiplier = 1024 * 1024;
        else if (sizeStr.EndsWith("GIB") || sizeStr.EndsWith("GB") || sizeStr.EndsWith("G")) multiplier = 1024 * 1024 * 1024;
        else if (sizeStr.EndsWith("TIB") || sizeStr.EndsWith("TB") || sizeStr.EndsWith("T")) multiplier = 1024L * 1024 * 1024 * 1024;

        string numPart = Regex.Replace(sizeStr, @"[^\d.]", "");
        if (double.TryParse(numPart, NumberStyles.Any, CultureInfo.InvariantCulture, out double val))
        {
            return (long)(val * multiplier);
        }
        return 0;
    }

    public static int ParseEtaToSeconds(string etaStr)
    {
        if (string.IsNullOrWhiteSpace(etaStr)) return 0;
        etaStr = etaStr.Trim();
        if (etaStr.EndsWith("s", StringComparison.OrdinalIgnoreCase) && int.TryParse(etaStr[..^1], out int s))
        {
            return s;
        }
        var parts = etaStr.Split(':');
        if (parts.Length == 2 && int.TryParse(parts[0], out int min) && int.TryParse(parts[1], out int sec))
        {
            return min * 60 + sec;
        }
        if (parts.Length == 3 && int.TryParse(parts[0], out int hr) && int.TryParse(parts[1], out int m) && int.TryParse(parts[2], out int sc))
        {
            return hr * 3600 + m * 60 + sc;
        }
        return 0;
    }

    public static ProgressInfo? ParseProgressLine(string line)
    {
        if (string.IsNullOrWhiteSpace(line)) return null;

        var match = ProgressRegex.Match(line);
        if (match.Success)
        {
            var info = new ProgressInfo();
            if (double.TryParse(match.Groups[1].Value, NumberStyles.Any, CultureInfo.InvariantCulture, out double pct))
            {
                info.Percent = pct;
            }
            if (match.Groups[2].Success)
            {
                info.TotalBytes = ParseSizeToBytes(match.Groups[2].Value);
                if (info.Percent.HasValue && info.TotalBytes.HasValue)
                {
                    info.DownloadedBytes = (long)(info.TotalBytes.Value * (info.Percent.Value / 100.0));
                }
            }
            if (match.Groups[3].Success)
            {
                info.SpeedBytesPerSec = ParseSizeToBytes(match.Groups[3].Value.Replace("/s", "", StringComparison.OrdinalIgnoreCase));
            }
            if (match.Groups[4].Success)
            {
                info.EtaSeconds = ParseEtaToSeconds(match.Groups[4].Value);
            }
            info.Status = "Downloading";
            return info;
        }

        if (line.Contains("[Merger]")) return new ProgressInfo { Status = "Merging" };
        if (line.Contains("[ExtractAudio]")) return new ProgressInfo { Status = "Extracting Audio" };
        if (line.Contains("[Fixup]")) return new ProgressInfo { Status = "Processing" };

        return null;
    }
}
