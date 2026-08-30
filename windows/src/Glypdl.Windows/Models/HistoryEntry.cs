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
}
