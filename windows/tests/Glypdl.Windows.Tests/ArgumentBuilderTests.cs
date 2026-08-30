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
}
