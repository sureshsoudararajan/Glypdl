using System.Diagnostics;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Glypdl.Windows.Services;

namespace Glypdl.Windows.ViewModels;

public partial class AboutViewModel : ObservableObject
{
    private readonly IUpdateService _updateService;

    public string AppName => "Glypdl for Windows 11";
    public string Version => "1.0.0";
    public string Author => "Suresh Soundararajan";
    public string License => "GPL-3.0-or-later";
    public string GitHubUrl => "https://github.com/sureshsoudararajan/Glypdl";
    public string IssuesUrl => "https://github.com/sureshsoudararajan/Glypdl/issues";

    [ObservableProperty]
    private string _updateStatus = "Check for Updates";

    [ObservableProperty]
    private bool _isCheckingUpdate;

    [ObservableProperty]
    private UpdateCheckResult? _updateResult;

    public AboutViewModel(IUpdateService updateService)
    {
        _updateService = updateService;
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
