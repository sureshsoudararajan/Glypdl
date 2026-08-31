namespace Glypdl.Windows.Models;

public class HistoryEntry
{
    public string Id { get; set; } = Guid.NewGuid().ToString();
    public string Url { get; set; } = string.Empty;
    public string Title { get; set; } = string.Empty;
    public string Uploader { get; set; } = string.Empty;
    public string ThumbnailPath { get; set; } = string.Empty;
    public string DownloadPath { get; set; } = string.Empty;
    public string Format { get; set; } = string.Empty;
    public long FileSize { get; set; }
    public string Status { get; set; } = "Completed";
    public DateTime Timestamp { get; set; } = DateTime.UtcNow;
    public int Duration { get; set; }
    public string Mode { get; set; } = "video+audio";
    public string Quality { get; set; } = "best";

    public string FormattedSize => Utilities.FormattingUtils.FormatSize(FileSize);
    public string FormattedDuration => Utilities.FormattingUtils.FormatDuration(Duration);
    public string FormattedDate => Timestamp.ToLocalTime().ToString("MMM dd, yyyy HH:mm");

    public string? GetResolvedFilePath()
    {
        if (string.IsNullOrWhiteSpace(DownloadPath)) return null;

        if (System.IO.File.Exists(DownloadPath))
        {
            return DownloadPath;
        }

        if (!System.IO.Directory.Exists(DownloadPath))
        {
            return null;
        }

        try
        {
            var files = System.IO.Directory.GetFiles(DownloadPath);
            if (files.Length == 0) return null;

            var cleanTitle = string.Concat(Title.Where(c => !System.IO.Path.GetInvalidFileNameChars().Contains(c))).Trim();
            if (!string.IsNullOrWhiteSpace(cleanTitle))
            {
                var matched = files.FirstOrDefault(f => 
                    System.IO.Path.GetFileNameWithoutExtension(f).Contains(cleanTitle, StringComparison.OrdinalIgnoreCase) ||
                    cleanTitle.Contains(System.IO.Path.GetFileNameWithoutExtension(f), StringComparison.OrdinalIgnoreCase));
                if (matched != null) return matched;
            }

            var mediaExtensions = new[] { ".mp4", ".mkv", ".webm", ".mp3", ".m4a", ".opus", ".flac", ".wav", ".aac" };
            var candidate = files.Where(f => mediaExtensions.Contains(System.IO.Path.GetExtension(f).ToLowerInvariant()))
                                 .OrderByDescending(f => System.IO.File.GetLastWriteTimeUtc(f))
                                 .FirstOrDefault();
            return candidate;
        }
        catch
        {
            return null;
        }
    }

    public bool IsFileAvailable
    {
        get
        {
            var p = GetResolvedFilePath();
            return !string.IsNullOrWhiteSpace(p) && System.IO.File.Exists(p);
        }
    }
}
