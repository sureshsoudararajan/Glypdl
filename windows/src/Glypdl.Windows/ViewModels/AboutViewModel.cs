using System.Diagnostics;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Glypdl.Windows.Services;

namespace Glypdl.Windows.ViewModels;

public partial class AboutViewModel : ObservableObject
{
    private readonly IUpdateService _updateService;
    private readonly IYtDlpService _ytdlpService;

    public string AppName => "Glypdl for Windows 11";
    public string Version => "1.0.0";
    public string Author => "Suresh Soundararajan";
    public string License => "GPL-3.0-or-later";
    public string GitHubUrl => "https://github.com/sureshsoudararajan/Glypdl";
    public string IssuesUrl => "https://github.com/sureshsoudararajan/Glypdl/issues";

    private string _ytDlpVersion = "Detecting...";
    public string YtDlpVersion
    {
        get => _ytDlpVersion;
        set => SetProperty(ref _ytDlpVersion, value);
    }

    private string _ffmpegVersion = "Detecting...";
    public string FFmpegVersion
    {
        get => _ffmpegVersion;
        set => SetProperty(ref _ffmpegVersion, value);
    }

    public string FFprobeVersion => "FFmpeg Suite";

    [ObservableProperty]
    private string _updateStatus = "Check for Updates";

    [ObservableProperty]
    private bool _isCheckingUpdate;

    [ObservableProperty]
    private UpdateCheckResult? _updateResult;

    public AboutViewModel(IUpdateService updateService, IYtDlpService ytdlpService)
    {
        _updateService = updateService;
        _ytdlpService = ytdlpService;
        _ = LoadVersionsAsync();
    }

    public async Task LoadVersionsAsync()
    {
        try
        {
            var yVer = await _ytdlpService.GetVersionAsync();
            Utilities.DispatcherHelper.ExecuteOnUIThread(() =>
            {
                YtDlpVersion = !string.IsNullOrWhiteSpace(yVer) ? yVer : "Bundled (Latest)";
            });
        }
        catch
        {
            Utilities.DispatcherHelper.ExecuteOnUIThread(() =>
            {
                YtDlpVersion = "Bundled";
            });
        }

        try
        {
            var fVer = await _ytdlpService.GetFFmpegVersionAsync();
            Utilities.DispatcherHelper.ExecuteOnUIThread(() =>
            {
                FFmpegVersion = !string.IsNullOrWhiteSpace(fVer) ? fVer : "Bundled (Latest)";
            });
        }
        catch
        {
            Utilities.DispatcherHelper.ExecuteOnUIThread(() =>
            {
                FFmpegVersion = "Bundled";
            });
        }
    }

    [RelayCommand]
    public async Task CheckForUpdatesAsync()
    {
        IsCheckingUpdate = true;
        UpdateStatus = "Checking GitHub Releases...";
        try
        {
            var res = await _updateService.CheckForUpdatesAsync();
            UpdateResult = res;
            if (res.HasUpdate)
            {
                UpdateStatus = $"Update Available: {res.LatestVersion}";
            }
            else
            {
                UpdateStatus = "You are on the latest version (1.0.0)";
            }
        }
        catch (Exception ex)
        {
            UpdateStatus = $"Failed to check: {ex.Message}";
        }
        finally
        {
            IsCheckingUpdate = false;
        }
    }

    [RelayCommand]
    public void OpenUrl(string url)
    {
        if (string.IsNullOrWhiteSpace(url)) return;
        try
        {
            Process.Start(new ProcessStartInfo
            {
                FileName = url,
                UseShellExecute = true
            });
        }
        catch { }
    }
}
