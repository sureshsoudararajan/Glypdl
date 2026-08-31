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
        if (binary == null)
        {
            try
            {
                string localBin = Path.Combine(PathUtils.GetBinDir(), "yt-dlp.exe");
                using var client = new HttpClient();
                var bytes = await client.GetByteArrayAsync("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe", cancellationToken);
                await File.WriteAllBytesAsync(localBin, bytes, cancellationToken);
                binary = localBin;
            }
            catch
            {
                throw new InvalidOperationException("yt-dlp executable was not found and could not be downloaded automatically.");
            }
        }

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

            bool isPlaylist = GetStringSafe(root, "_type") == "playlist";

            string thumb = GetStringSafe(root, "thumbnail");
            if (string.IsNullOrWhiteSpace(thumb) && root.TryGetProperty("thumbnails", out var thumbsEl) && thumbsEl.ValueKind == JsonValueKind.Array)
            {
                foreach (var t in thumbsEl.EnumerateArray())
                {
                    var u = GetStringSafe(t, "url");
                    if (!string.IsNullOrWhiteSpace(u))
                    {
                        thumb = u;
                    }
                }
            }

            var meta = new MediaMetadata
            {
                Url = url,
                Id = GetStringSafe(root, "id"),
                Title = GetStringSafe(root, "title", "Untitled"),
                Uploader = GetStringSafe(root, "uploader", GetStringSafe(root, "channel")),
                Duration = GetIntSafe(root, "duration") ?? 0,
                ThumbnailUrl = thumb,
                Extractor = GetStringSafe(root, "extractor"),
                Description = GetStringSafe(root, "description"),
                IsPlaylist = isPlaylist
            };

            if (isPlaylist && root.TryGetProperty("entries", out var entriesEl) && entriesEl.ValueKind == JsonValueKind.Array)
            {
                meta.PlaylistCount = entriesEl.GetArrayLength();
                int idx = 1;
                foreach (var entry in entriesEl.EnumerateArray())
                {
                    string entryThumb = GetStringSafe(entry, "thumbnail");
                    if (string.IsNullOrWhiteSpace(entryThumb) && entry.TryGetProperty("thumbnails", out var entryThumbs) && entryThumbs.ValueKind == JsonValueKind.Array)
                    {
                        foreach (var et in entryThumbs.EnumerateArray())
                        {
                            var u = GetStringSafe(et, "url");
                            if (!string.IsNullOrWhiteSpace(u))
                            {
                                entryThumb = u;
                            }
                        }
                    }

                    string entryId = GetStringSafe(entry, "id");
                    if (string.IsNullOrWhiteSpace(entryThumb) && !string.IsNullOrWhiteSpace(entryId) && entryId.Length == 11)
                    {
                        entryThumb = $"https://i.ytimg.com/vi/{entryId}/mqdefault.jpg";
                    }

                    string entryUrl = GetStringSafe(entry, "url", GetStringSafe(entry, "webpage_url"));
                    if (!string.IsNullOrWhiteSpace(entryId) && (string.IsNullOrWhiteSpace(entryUrl) || !entryUrl.StartsWith("http", StringComparison.OrdinalIgnoreCase)))
                    {
                        entryUrl = $"https://www.youtube.com/watch?v={entryId}";
                    }

                    meta.PlaylistEntries.Add(new PlaylistItem
                    {
                        Index = idx++,
                        Url = entryUrl,
                        Id = entryId,
                        Title = GetStringSafe(entry, "title", $"Track {idx - 1}"),
                        Uploader = GetStringSafe(entry, "uploader", meta.Uploader),
                        Duration = GetIntSafe(entry, "duration") ?? 0,
                        ThumbnailUrl = entryThumb,
                        IsSelected = true
                    });
                }
            }
            else if (root.TryGetProperty("formats", out var formatsEl) && formatsEl.ValueKind == JsonValueKind.Array)
            {
                foreach (var f in formatsEl.EnumerateArray())
                {
                    string vcodec = GetStringSafe(f, "vcodec", "none");
                    string acodec = GetStringSafe(f, "acodec", "none");
                    bool hasVideo = vcodec != "none" && !string.IsNullOrWhiteSpace(vcodec);
                    bool hasAudio = acodec != "none" && !string.IsNullOrWhiteSpace(acodec);

                    int? height = GetIntSafe(f, "height");
                    string res = height.HasValue ? $"{height.Value}p" : GetStringSafe(f, "resolution");

                    meta.Formats.Add(new MediaFormat
                    {
                        FormatId = GetStringSafe(f, "format_id"),
                        Extension = GetStringSafe(f, "ext"),
                        Resolution = res,
                        Fps = GetIntSafe(f, "fps"),
                        VideoCodec = vcodec,
                        AudioCodec = acodec,
                        FileSize = GetLongSafe(f, "filesize") ?? GetLongSafe(f, "filesize_approx"),
                        TotalBitrate = GetDoubleSafe(f, "tbr"),
                        AudioBitrate = GetDoubleSafe(f, "abr"),
                        FormatNote = GetStringSafe(f, "format_note"),
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

    private static int? GetIntSafe(JsonElement el, string prop)
    {
        if (!el.TryGetProperty(prop, out var val)) return null;
        if (val.ValueKind == JsonValueKind.Number)
        {
            if (val.TryGetInt32(out int i)) return i;
            if (val.TryGetDouble(out double d)) return (int)Math.Round(d);
        }
        if (val.ValueKind == JsonValueKind.String && int.TryParse(val.GetString(), out int parsed))
        {
            return parsed;
        }
        return null;
    }

    private static long? GetLongSafe(JsonElement el, string prop)
    {
        if (!el.TryGetProperty(prop, out var val)) return null;
        if (val.ValueKind == JsonValueKind.Number)
        {
            if (val.TryGetInt64(out long l)) return l;
            if (val.TryGetDouble(out double d)) return (long)Math.Round(d);
        }
        if (val.ValueKind == JsonValueKind.String && long.TryParse(val.GetString(), out long parsed))
        {
            return parsed;
        }
        return null;
    }

    private static double? GetDoubleSafe(JsonElement el, string prop)
    {
        if (!el.TryGetProperty(prop, out var val)) return null;
        if (val.ValueKind == JsonValueKind.Number && val.TryGetDouble(out double d))
        {
            return d;
        }
        if (val.ValueKind == JsonValueKind.String && double.TryParse(val.GetString(), System.Globalization.NumberStyles.Any, System.Globalization.CultureInfo.InvariantCulture, out double parsed))
        {
            return parsed;
        }
        return null;
    }

    private static string GetStringSafe(JsonElement el, string prop, string fallback = "")
    {
        if (!el.TryGetProperty(prop, out var val)) return fallback;
        if (val.ValueKind == JsonValueKind.String) return val.GetString() ?? fallback;
        if (val.ValueKind == JsonValueKind.Number) return val.GetRawText();
        return fallback;
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
