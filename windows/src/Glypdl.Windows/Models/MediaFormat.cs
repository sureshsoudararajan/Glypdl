namespace Glypdl.Windows.Models;

public class MediaFormat
{
    public string FormatId { get; set; } = string.Empty;
    public string Extension { get; set; } = string.Empty;
    public string Resolution { get; set; } = string.Empty;
    public int? Fps { get; set; }
    public string VideoCodec { get; set; } = string.Empty;
    public string AudioCodec { get; set; } = string.Empty;
    public long? FileSize { get; set; }
    public double? TotalBitrate { get; set; }
    public double? AudioBitrate { get; set; }
    public string FormatNote { get; set; } = string.Empty;
    public bool HasVideo { get; set; }
    public bool HasAudio { get; set; }
    public string DisplayName => $"{Resolution} ({Extension}) {FormatNote}".Trim();
}
