using CommunityToolkit.Mvvm.ComponentModel;

namespace Glypdl.Windows.ViewModels;

public partial class MainViewModel : ObservableObject
{
    [ObservableProperty]
    private string _title = "Glypdl";

    public HomeViewModel Home { get; }
    public DownloadsViewModel Downloads { get; }
    public HistoryViewModel History { get; }
    public SettingsViewModel Settings { get; }
    public AboutViewModel About { get; }

    public MainViewModel(
        HomeViewModel home,
        DownloadsViewModel downloads,
        HistoryViewModel history,
        SettingsViewModel settings,
        AboutViewModel about)
    {
        Home = home;
        Downloads = downloads;
        History = history;
        Settings = settings;
        About = about;
    }
}
