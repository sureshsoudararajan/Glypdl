using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;

namespace Glypdl.Windows.Models;

public partial class PlaylistItem : ObservableObject
{
    public int Index { get; set; }
    public string Id { get; set; } = string.Empty;
    public string Url { get; set; } = string.Empty;
    public string Title { get; set; } = string.Empty;
    public string Uploader { get; set; } = string.Empty;
    public int Duration { get; set; }
    public string ThumbnailUrl { get; set; } = string.Empty;

    [ObservableProperty]
    private bool _isSelected = true;

    public string FormattedDuration => Duration > 0
        ? (Duration >= 3600 ? TimeSpan.FromSeconds(Duration).ToString(@"h\:mm\:ss") : TimeSpan.FromSeconds(Duration).ToString(@"m\:ss"))
        : "";

    public string DisplayIndex => $"#{Index}";
}

public partial class MediaMetadata : ObservableObject
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
    public int? PlaylistIndex { get; set; }
    public string UsedCookieFile { get; set; } = string.Empty;
    public List<MediaFormat> Formats { get; set; } = new();
    public ObservableCollection<PlaylistItem> PlaylistEntries { get; set; } = new();

    public string FormattedDuration => Duration > 0 
        ? (Duration >= 3600 ? TimeSpan.FromSeconds(Duration).ToString(@"h\:mm\:ss") : TimeSpan.FromSeconds(Duration).ToString(@"m\:ss"))
        : "";

    public List<string> AvailableQualities
    {
        get
        {
            var detected = Formats
                .Where(f => f.HasVideo && !string.IsNullOrWhiteSpace(f.Resolution))
                .Select(f => f.Resolution)
                .Distinct()
                .ToList();

            if (detected.Count > 0)
            {
                return detected;
            }

            return new List<string> { "2160p (4K)", "1440p (2K)", "1080p (Full HD)", "720p (HD)", "480p (SD)", "360p" };
        }
    }
}
