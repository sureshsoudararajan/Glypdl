using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Glypdl.Windows.Models;
using Glypdl.Windows.ViewModels;

namespace Glypdl.Windows.Views;

public partial class HistoryPage : Page
{
    public HistoryViewModel ViewModel { get; }

    public HistoryPage()
    {
        InitializeComponent();
        ViewModel = (HistoryViewModel)App.Services.GetService(typeof(HistoryViewModel))!;
        DataContext = ViewModel;
        Loaded += (_, _) =>
        {
            ApplyCurrentTheme();
            _ = ViewModel.LoadHistoryAsync();
        };
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

    public Visibility CountToEmptyVisibility(int count) => count == 0 ? Visibility.Visible : Visibility.Collapsed;
    public Visibility CountToListVisibility(int count) => count > 0 ? Visibility.Visible : Visibility.Collapsed;
    public Visibility BoolToVisibility(bool value) => value ? Visibility.Visible : Visibility.Collapsed;

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

    private void AutoSuggestBox_QuerySubmitted(AutoSuggestBox sender, AutoSuggestBoxQuerySubmittedEventArgs args)
    {
        _ = ViewModel.LoadHistoryAsync();
    }

    private void AutoSuggestBox_TextChanged(AutoSuggestBox sender, AutoSuggestBoxTextChangedEventArgs args)
    {
        if (args.Reason == AutoSuggestionBoxTextChangeReason.UserInput)
        {
            _ = ViewModel.LoadHistoryAsync();
        }
    }

    private void PlayButton_Click(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement { DataContext: HistoryEntry entry })
        {
            ViewModel.PlayMedia(entry);
        }
    }

    private void OpenFolderButton_Click(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement { DataContext: HistoryEntry entry })
        {
            ViewModel.OpenFolder(entry);
        }
    }

    private void DownloadAgainButton_Click(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement { DataContext: HistoryEntry entry })
        {
            App.NavigateToHomeWithUrl(entry.Url);
        }
    }
}
