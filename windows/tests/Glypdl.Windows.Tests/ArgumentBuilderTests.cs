using Glypdl.Windows.Models;
using Glypdl.Windows.Services;
using Xunit;

namespace Glypdl.Windows.Tests;

public class MockSettingsService : ISettingsService
{
    public AppSettings GetSettings() => new();
    public void SaveSettings(AppSettings settings) { }
}

public class ArgumentBuilderTests
{
    [Fact]
    public void BuildDownloadArguments_ShouldIncludeEssentialFlags()
    {
        var settingsService = new MockSettingsService();
        var ytdlpService = new YtDlpService(settingsService);

        var args = ytdlpService.BuildDownloadArguments(
            url: "https://example.com/video",
            formatSpec: "1080p",
            outputTemplate: "%(title)s.%(ext)s",
            downloadDir: @"C:\Downloads"
        );

        Assert.Contains("--newline", args);
        Assert.Contains("--progress", args);
        Assert.DoesNotContain("--no-overwrites", args);
        Assert.Contains("-f", args);
        Assert.Contains("1080p", args);
        Assert.Contains("-o", args);
        Assert.Contains("%(title)s [%(id)s].%(ext)s", args);
        Assert.Contains("-P", args);
        Assert.Contains(@"C:\Downloads", args);
        Assert.Contains("https://example.com/video", args);
    }

    [Fact]
    public void BuildAudioDownloadArguments_ShouldIncludeExtractFlag()
    {
        var settingsService = new MockSettingsService();
        var ytdlpService = new YtDlpService(settingsService);

        var args = ytdlpService.BuildDownloadArguments(
            url: "https://example.com/audio",
            extractAudio: true,
            audioFormat: "mp3"
        );

        Assert.Contains("-x", args);
        Assert.Contains("--audio-format", args);
        Assert.Contains("mp3", args);
        Assert.Contains("--audio-quality", args);
    }

    [Fact]
    public void BuildMetadataArguments_WithCookieFile_ShouldIncludeCookieFlag()
    {
        var settingsService = new MockSettingsService();
        var ytdlpService = new YtDlpService(settingsService);

        string tempCookie = Path.GetTempFileName();
        try
        {
            var args = ytdlpService.BuildMetadataArguments("https://example.com/video", tempCookie);
            Assert.Contains("--cookies", args);
            Assert.Contains(tempCookie, args);
        }
        finally
        {
            if (File.Exists(tempCookie)) File.Delete(tempCookie);
        }
    }

    [Fact]
    public void CookieService_ValidateCookieFile_ShouldValidateExistence()
    {
        var settingsService = new MockSettingsService();
        var cookieService = new CookieService(settingsService);

        Assert.False(cookieService.ValidateCookieFile(null));
        Assert.False(cookieService.ValidateCookieFile(""));
        Assert.False(cookieService.ValidateCookieFile(@"C:\non_existent_cookies_12345.txt"));

        string tempFile = Path.GetTempFileName();
        try
        {
            Assert.True(cookieService.ValidateCookieFile(tempFile));
        }
        finally
        {
            if (File.Exists(tempFile)) File.Delete(tempFile);
        }
    }

    [Fact]
    public void CookieService_BuildBrowserSpec_ShouldFormatCorrectly()
    {
        var settingsService = new MockSettingsService();
        var cookieService = new CookieService(settingsService);

        Assert.Equal("edge", cookieService.BuildBrowserSpec("edge"));
        Assert.Equal("chrome", cookieService.BuildBrowserSpec("chrome", "Default"));
        Assert.Equal("chrome:Profile 1", cookieService.BuildBrowserSpec("chrome", "Profile 1"));
        Assert.Equal("firefox:default-release", cookieService.BuildBrowserSpec("firefox", "default-release"));
        Assert.Equal("edge+basictext", cookieService.BuildBrowserSpec("edge", "Default", "basictext"));
        Assert.Equal(string.Empty, cookieService.BuildBrowserSpec("none"));
    }

    [Fact]
    public void BuildDownloadArguments_WithBrowserCookies_ShouldIncludeCookiesFromBrowserFlag()
    {
        var settingsService = new MockSettingsService();
        var ytdlpService = new YtDlpService(settingsService);

        var args = ytdlpService.BuildDownloadArguments(
            url: "https://example.com/video",
            cookieFile: "browser:edge:Default"
        );

        Assert.Contains("--cookies-from-browser", args);
        Assert.Contains("edge:Default", args);
    }

    [Fact]
    public void BuildMetadataArguments_WithBrowserCookies_ShouldIncludeCookiesFromBrowserFlag()
    {
        var settingsService = new MockSettingsService();
        var ytdlpService = new YtDlpService(settingsService);

        var args = ytdlpService.BuildMetadataArguments("https://example.com/video", "browser:chrome");

        Assert.Contains("--cookies-from-browser", args);
        Assert.Contains("chrome", args);
    }

    [Fact]
    public void CookieService_DiscoverInstalledBrowsers_ShouldReturnSupportedBrowsers()
    {
        var settingsService = new MockSettingsService();
        var cookieService = new CookieService(settingsService);

        var browsers = cookieService.DiscoverInstalledBrowsers();
        Assert.NotEmpty(browsers);
        Assert.Contains(browsers, b => b.Id == "edge");
        Assert.Contains(browsers, b => b.Id == "chrome");
    }

    [Theory]
    [InlineData("https://www.instagram.com/stories/nithya.___.04/3977692917933874797/", "3977692917933874797")]
    [InlineData("https://www.instagram.com/stories/nithya.___.04/3977682576785494088/", "3977682576785494088")]
    [InlineData("https://instagram.com/stories/user.name/1234567890?igsh=xyz", "1234567890")]
    [InlineData("https://www.instagram.com/stories/highlights/17900000000000000/9876543210/", "9876543210")]
    [InlineData("https://www.instagram.com/stories/nithya.___.04/", null)]
    [InlineData("https://www.instagram.com/p/C-abc123/", null)]
    public void ExtractInstagramStoryId_ShouldExtractCorrectStoryId(string url, string? expectedId)
    {
        string? result = YtDlpService.ExtractInstagramStoryId(url);
        Assert.Equal(expectedId, result);
    }

    [Fact]
    public void BuildDownloadArguments_WithPlaylistIndex_ShouldIncludePlaylistItemsFlag()
    {
        var settingsService = new MockSettingsService();
        var ytdlpService = new YtDlpService(settingsService);

        var args = ytdlpService.BuildDownloadArguments(
            url: "https://www.instagram.com/stories/nithya.___.04/3977692917933874797/",
            playlistIndex: 3
        );

        Assert.Contains("--playlist-items", args);
        int idx = args.IndexOf("--playlist-items");
        Assert.True(idx >= 0 && idx < args.Count - 1);
        Assert.Equal("3", args[idx + 1]);
    }

    [Fact]
    public void BuildMetadataArguments_ShouldIncludeNoPlaylistFlag()
    {
        var settingsService = new MockSettingsService();
        var ytdlpService = new YtDlpService(settingsService);

        var args = ytdlpService.BuildMetadataArguments("https://www.instagram.com/stories/nithya.___.04/3977682576785494088/");

        Assert.Contains("-J", args);
        Assert.Contains("--no-playlist", args);
    }
}
