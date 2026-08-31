using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Glypdl.Windows.ViewModels;

namespace Glypdl.Windows.Views;

public partial class SettingsPage : Page
{
    public SettingsViewModel ViewModel { get; }

    public SettingsPage()
    {
        InitializeComponent();
        ViewModel = (SettingsViewModel)App.Services.GetService(typeof(SettingsViewModel))!;
        DataContext = ViewModel;
        Loaded += (s, e) => ApplyCurrentTheme();
    }

    private void ApplyCurrentTheme()
    {
        var settingsService = (Services.ISettingsService?)App.Services.GetService(typeof(Services.ISettingsService));
        if (settingsService != null)
        {
            var theme = settingsService.GetSettings().Theme;
            RequestedTheme = theme switch
            {
                Models.AppTheme.Light => ElementTheme.Light,
                Models.AppTheme.Dark => ElementTheme.Dark,
                _ => ElementTheme.Default
            };
        }
    }

    private async void ChangeFolder_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            var picker = new global::Windows.Storage.Pickers.FolderPicker();
            picker.SuggestedStartLocation = global::Windows.Storage.Pickers.PickerLocationId.Downloads;
            picker.FileTypeFilter.Add("*");

            var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(App.MainWindow);
            WinRT.Interop.InitializeWithWindow.Initialize(picker, hwnd);

            var folder = await picker.PickSingleFolderAsync();
            if (folder != null)
            {
                ViewModel.DownloadDirectory = folder.Path;
            }
        }
        catch { }
    }
}
