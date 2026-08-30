using System.Text.Json;
using Glypdl.Windows.Models;
using Glypdl.Windows.Utilities;

namespace Glypdl.Windows.Services;

public class MetadataService : IMetadataService
{
    private readonly IYtDlpService _ytdlpService;

    public MetadataService(IYtDlpService ytdlpService)
    {
        _ytdlpService = ytdlpService;
    }

    public async Task<MediaMetadata?> FetchMetadataAsync(string url, string? cookieFile = null, CancellationToken cancellationToken = default)
    {
        string? binary = _ytdlpService.DetectYtDlp();
        if (binary == null) throw new InvalidOperationException("yt-dlp executable was not found.");

        var args = _ytdlpService.BuildMetadataArguments(url, cookieFile);
        var result = await ProcessRunner.RunAsync(binary, args, cancellationToken);

        if (!result.Success || string.IsNullOrWhiteSpace(result.StandardOutput))
        {
            // Try flat playlist if single video returned nothing
            var plArgs = _ytdlpService.BuildPlaylistArguments(url, cookieFile);
            result = await ProcessRunner.RunAsync(binary, plArgs, cancellationToken);
            if (!result.Success || string.IsNullOrWhiteSpace(result.StandardOutput))
            {
                throw new Exception(string.IsNullOrWhiteSpace(result.StandardError) ? "Failed to retrieve metadata." : result.StandardError);
            }
        }

        try
        {
            using var doc = JsonDocument.Parse(result.StandardOutput);
            var root = doc.RootElement;

            bool isPlaylist = root.TryGetProperty("_type", out var typeEl) && typeEl.GetString() == "playlist";

            var meta = new MediaMetadata
            {
                Url = url,
                Id = root.TryGetProperty("id", out var idEl) ? idEl.GetString() ?? "" : "",
                Title = root.TryGetProperty("title", out var titleEl) ? titleEl.GetString() ?? "" : "Untitled",
                Uploader = root.TryGetProperty("uploader", out var upEl) ? upEl.GetString() ?? "" : (root.TryGetProperty("channel", out var chEl) ? chEl.GetString() ?? "" : ""),
                Duration = root.TryGetProperty("duration", out var durEl) && durEl.TryGetInt32(out int d) ? d : 0,
                ThumbnailUrl = root.TryGetProperty("thumbnail", out var thumbEl) ? thumbEl.GetString() ?? "" : "",
                Extractor = root.TryGetProperty("extractor", out var extEl) ? extEl.GetString() ?? "" : "",
                Description = root.TryGetProperty("description", out var descEl) ? descEl.GetString() ?? "" : "",
                IsPlaylist = isPlaylist
            };

            if (isPlaylist && root.TryGetProperty("entries", out var entriesEl) && entriesEl.ValueKind == JsonValueKind.Array)
            {
                meta.PlaylistCount = entriesEl.GetArrayLength();
                foreach (var entry in entriesEl.EnumerateArray())
                {
                    meta.PlaylistEntries.Add(new MediaMetadata
                    {
                        Url = entry.TryGetProperty("url", out var u) ? u.GetString() ?? "" : (entry.TryGetProperty("webpage_url", out var w) ? w.GetString() ?? "" : ""),
                        Id = entry.TryGetProperty("id", out var i) ? i.GetString() ?? "" : "",
                        Title = entry.TryGetProperty("title", out var t) ? t.GetString() ?? "" : "Track",
                        Uploader = entry.TryGetProperty("uploader", out var eUp) ? eUp.GetString() ?? "" : meta.Uploader,
                        Duration = entry.TryGetProperty("duration", out var eDur) && eDur.TryGetInt32(out int ed) ? ed : 0,
                        ThumbnailUrl = entry.TryGetProperty("thumbnail", out var eTh) ? eTh.GetString() ?? "" : "",
                        IsSelectedInPlaylist = true
                    });
                }
            }
            else if (root.TryGetProperty("formats", out var formatsEl) && formatsEl.ValueKind == JsonValueKind.Array)
            {
                foreach (var f in formatsEl.EnumerateArray())
                {
                    string vcodec = f.TryGetProperty("vcodec", out var vc) ? vc.GetString() ?? "none" : "none";
                    string acodec = f.TryGetProperty("acodec", out var ac) ? ac.GetString() ?? "none" : "none";
                    bool hasVideo = vcodec != "none" && !string.IsNullOrWhiteSpace(vcodec);
                    bool hasAudio = acodec != "none" && !string.IsNullOrWhiteSpace(acodec);

                    int? height = f.TryGetProperty("height", out var h) && h.TryGetInt32(out int hval) ? hval : null;
                    string res = height.HasValue ? $"{height.Value}p" : (f.TryGetProperty("resolution", out var r) ? r.GetString() ?? "" : "");

                    meta.Formats.Add(new MediaFormat
                    {
                        FormatId = f.TryGetProperty("format_id", out var fid) ? fid.GetString() ?? "" : "",
                        Extension = f.TryGetProperty("ext", out var ext) ? ext.GetString() ?? "" : "",
                        Resolution = res,
                        Fps = f.TryGetProperty("fps", out var fps) && fps.TryGetInt32(out int fpsval) ? fpsval : null,
                        VideoCodec = vcodec,
                        AudioCodec = acodec,
                        FileSize = f.TryGetProperty("filesize", out var fs) && fs.TryGetInt64(out long fsval) ? fsval : (f.TryGetProperty("filesize_approx", out var fsa) && fsa.TryGetInt64(out long fsaval) ? fsaval : null),
                        TotalBitrate = f.TryGetProperty("tbr", out var tbr) && tbr.TryGetDouble(out double tbrval) ? tbrval : null,
                        AudioBitrate = f.TryGetProperty("abr", out var abr) && abr.TryGetDouble(out double abrval) ? abrval : null,
                        FormatNote = f.TryGetProperty("format_note", out var fn) ? fn.GetString() ?? "" : "",
                        HasVideo = hasVideo,
                        HasAudio = hasAudio
                    });
                }
            }

            return meta;
        }
        catch (Exception ex)
        {
            throw new Exception($"Failed to parse metadata JSON: {ex.Message}");
        }
    }

    public string GetFormatSpec(MediaMetadata metadata, string quality, DownloadMode mode)
    {
        if (mode == DownloadMode.AudioOnly)
        {
            return "bestaudio/best";
        }

        if (string.IsNullOrWhiteSpace(quality) || quality.Equals("best", StringComparison.OrdinalIgnoreCase))
        {
            return mode == DownloadMode.VideoOnly ? "bestvideo/best" : "bestvideo+bestaudio/best";
        }

        // Parse height from quality (e.g., "1080p" -> 1080)
        string digits = new string(quality.Where(char.IsDigit).ToArray());
        if (int.TryParse(digits, out int height))
        {
            return mode == DownloadMode.VideoOnly
                ? $"bestvideo[height<={height}]/best[height<={height}]"
                : $"bestvideo[height<={height}]+bestaudio/best[height<={height}]";
        }

        return "bestvideo+bestaudio/best";
    }
}
