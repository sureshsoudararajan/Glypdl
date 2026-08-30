using System.Net.Http.Headers;
using System.Text.Json;

namespace Glypdl.Windows.Services;

public class UpdateService : IUpdateService
{
    private const string CurrentVersion = "1.0.0";
    private const string GitHubApiUrl = "https://api.github.com/repos/sureshsoudararajan/Glypdl/releases/latest";

    public async Task<UpdateCheckResult> CheckForUpdatesAsync()
    {
        try
        {
            using var client = new HttpClient();
            client.DefaultRequestHeaders.UserAgent.Add(new ProductInfoHeaderValue("Glypdl-Windows", CurrentVersion));

            var response = await client.GetAsync(GitHubApiUrl);
            if (!response.IsSuccessStatusCode)
            {
                return new UpdateCheckResult { CurrentVersion = CurrentVersion, HasUpdate = false };
            }

            string json = await response.Content.ReadAsStringAsync();
            using var doc = JsonDocument.Parse(json);
            var root = doc.RootElement;

            string tag = root.TryGetProperty("tag_name", out var tagEl) ? tagEl.GetString() ?? "" : "";
            string cleanTag = tag.TrimStart('v');

            string body = root.TryGetProperty("body", out var bodyEl) ? bodyEl.GetString() ?? "" : "";
            string htmlUrl = root.TryGetProperty("html_url", out var urlEl) ? urlEl.GetString() ?? "" : "";

            bool isNewer = IsVersionNewer(cleanTag, CurrentVersion);

            return new UpdateCheckResult
            {
                HasUpdate = isNewer,
                LatestVersion = tag,
                CurrentVersion = CurrentVersion,
                ReleaseNotes = body,
                DownloadUrl = htmlUrl
            };
        }
        catch
        {
            return new UpdateCheckResult { CurrentVersion = CurrentVersion, HasUpdate = false };
        }
    }

    public static bool IsVersionNewer(string latest, string current)
    {
        if (Version.TryParse(latest, out var vLatest) && Version.TryParse(current, out var vCurrent))
        {
            return vLatest > vCurrent;
        }
        return string.Compare(latest, current, StringComparison.OrdinalIgnoreCase) > 0;
    }
}
