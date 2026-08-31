using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Glypdl.Windows.ViewModels;

namespace Glypdl.Windows.Views;

public partial class HomePage : Page
{
    public HomeViewModel ViewModel { get; }

    public HomePage()
    {
        InitializeComponent();
        ViewModel = (HomeViewModel)App.Services.GetService(typeof(HomeViewModel))!;
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

    public Visibility ObjectToVisibility(object? obj) => obj != null ? Visibility.Visible : Visibility.Collapsed;
    public Visibility BoolToVisibility(bool value) => value ? Visibility.Visible : Visibility.Collapsed;
    public Visibility InvertBoolToVisibility(bool value) => !value ? Visibility.Visible : Visibility.Collapsed;
    public Visibility StringToVisibility(string? str) => !string.IsNullOrWhiteSpace(str) ? Visibility.Visible : Visibility.Collapsed;
    public bool StringToBool(string? str) => !string.IsNullOrWhiteSpace(str);

    public Microsoft.UI.Xaml.Media.ImageSource? StringToImageSource(string? path)
    {
        if (string.IsNullOrWhiteSpace(path)) return null;
        try
        {
            if (Uri.TryCreate(path, UriKind.Absolute, out var uri))
            {
                return new Microsoft.UI.Xaml.Media.Imaging.BitmapImage(uri);
            }
            if (System.IO.File.Exists(path))
            {
                return new Microsoft.UI.Xaml.Media.Imaging.BitmapImage(new Uri(path));
            }
        }
        catch { }
        return null;
    }

    private void UrlTextBox_KeyDown(object sender, KeyRoutedEventArgs e)
    {
        if (e.Key == global::Windows.System.VirtualKey.Enter)
        {
            _ = ViewModel.FetchMetadataAsync();
        }
    }

    private async void UrlTextBox_Paste(object sender, TextControlPasteEventArgs e)
    {
        await Task.Delay(80);
        TriggerAutoFetchIfValid();
    }

    private void TriggerAutoFetchIfValid()
    {
        if (!string.IsNullOrWhiteSpace(ViewModel.UrlInput))
        {
            var trimmed = ViewModel.UrlInput.Trim();
            if (trimmed.StartsWith("http://", StringComparison.OrdinalIgnoreCase) || 
                trimmed.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
            {
                _ = ViewModel.FetchMetadataAsync();
            }
        }
    }

    private async void Page_KeyDown(object sender, KeyRoutedEventArgs e)
    {
        var ctrlState = Microsoft.UI.Input.InputKeyboardSource.GetKeyStateForCurrentThread(global::Windows.System.VirtualKey.Control);
        if ((ctrlState & global::Windows.UI.Core.CoreVirtualKeyStates.Down) == global::Windows.UI.Core.CoreVirtualKeyStates.Down && 
            e.Key == global::Windows.System.VirtualKey.V)
        {
            try
            {
                var package = global::Windows.ApplicationModel.DataTransfer.Clipboard.GetContent();
                if (package.Contains(global::Windows.ApplicationModel.DataTransfer.StandardDataFormats.Text))
                {
                    var text = await package.GetTextAsync();
                    if (!string.IsNullOrWhiteSpace(text))
                    {
                        var trimmed = text.Trim();
                        if (trimmed.StartsWith("http://", StringComparison.OrdinalIgnoreCase) || 
                            trimmed.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
                        {
                            ViewModel.UrlInput = trimmed;
                            _ = ViewModel.FetchMetadataAsync();
                            e.Handled = true;
                        }
                    }
                }
            }
            catch { }
        }
    }
}
