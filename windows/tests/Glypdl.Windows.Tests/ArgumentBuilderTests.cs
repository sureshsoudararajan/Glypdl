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
        Assert.Contains("-f", args);
        Assert.Contains("1080p", args);
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
}
