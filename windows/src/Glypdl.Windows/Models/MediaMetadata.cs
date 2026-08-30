namespace Glypdl.Windows.Models;

public class MediaMetadata
{
    public string Id { get; set; } = string.Empty;
    public string Url { get; set; } = string.Empty;
    public string Title { get; set; } = string.Empty;
    public string Uploader { get; set; } = string.Empty;
    public int Duration { get; set; }
    public string ThumbnailUrl { get; set; } = string.Empty;
    public string Extractor { get; set; } = string.Empty;
    public string Description { get; set; } = string.Empty;
    public bool IsPlaylist { get; set; }
    public int PlaylistCount { get; set; }
    public List<MediaFormat> Formats { get; set; } = new();
    public List<MediaMetadata> PlaylistEntries { get; set; } = new();
    public bool IsSelectedInPlaylist { get; set; } = true;

    public List<string> AvailableQualities => Formats
        .Where(f => f.HasVideo && !string.IsNullOrWhiteSpace(f.Resolution))
        .Select(f => f.Resolution)
        .Distinct()
        .ToList();
}
