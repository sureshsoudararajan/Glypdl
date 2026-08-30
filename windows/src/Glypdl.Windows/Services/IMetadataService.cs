using Glypdl.Windows.Models;

namespace Glypdl.Windows.Services;

public interface IMetadataService
{
    Task<MediaMetadata?> FetchMetadataAsync(string url, string? cookieFile = null, CancellationToken cancellationToken = default);
    string GetFormatSpec(MediaMetadata metadata, string quality, DownloadMode mode);
}
