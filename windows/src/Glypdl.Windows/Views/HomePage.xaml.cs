using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Glypdl.Windows.Models;
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

        ViewModel.RequestAuthRecovery += HandleAuthRecoveryAsync;
    }

    private bool _isAuthDialogOpen;

    private async Task HandleAuthRecoveryAsync(string errorMsg, string url)
    {
        if (_isAuthDialogOpen) return;
        var root = this.XamlRoot ?? App.MainWindow?.Content?.XamlRoot;
        if (root == null) return;

        _isAuthDialogOpen = true;

        try
        {
            var cookieService = App.Services.GetService(typeof(Services.ICookieService)) as Services.ICookieService;
            var settingsService = App.Services.GetService(typeof(Services.ISettingsService)) as Services.ISettingsService;
            if (cookieService == null || settingsService == null) return;

            var settings = settingsService.GetSettings();
            var profiles = cookieService.GetProfiles();

            var combo = new ComboBox
            {
                HorizontalAlignment = HorizontalAlignment.Stretch,
                Margin = new Thickness(0, 8, 0, 0)
            };

            var items = new List<CookieOptionItem>();
            foreach (var p in profiles)
            {
                if (p.Exists)
                {
                    items.Add(new CookieOptionItem
                    {
                        DisplayName = $"{p.Name} ({System.IO.Path.GetFileName(p.FilePath)})",
                        FilePath = p.FilePath
                    });
                }
            }

            var browseOption = new CookieOptionItem
            {
                DisplayName = "📂 Browse for other cookie file…",
                FilePath = "__browse__"
            };
            items.Add(browseOption);

            combo.ItemsSource = items;
            combo.SelectedIndex = 0;

            var stack = new StackPanel { Spacing = 10 };

            if (!settings.UseCookies)
            {
                var warningBorder = new Border
                {
                    Background = new Microsoft.UI.Xaml.Media.SolidColorBrush(global::Windows.UI.Color.FromArgb(30, 255, 185, 0)),
                    BorderBrush = new Microsoft.UI.Xaml.Media.SolidColorBrush(global::Windows.UI.Color.FromArgb(120, 255, 185, 0)),
                    BorderThickness = new Thickness(1),
                    CornerRadius = new CornerRadius(6),
                    Padding = new Thickness(12, 10, 12, 10)
                };
                var warningText = new TextBlock
                {
                    Text = "⚠️ Cookies are currently turned OFF in Settings.\nSelecting or importing a cookie file below will automatically enable cookies and retry the download.",
                    TextWrapping = TextWrapping.Wrap,
                    FontSize = 12
                };
                warningBorder.Child = warningText;
                stack.Children.Add(warningBorder);
            }

            stack.Children.Add(new TextBlock
            {
                Text = "This video or site requires login credentials or authentication cookies:\n\n" + 
                       (errorMsg.Length > 220 ? errorMsg.Substring(0, 220) + "..." : errorMsg),
                TextWrapping = TextWrapping.Wrap,
                Opacity = 0.8,
                FontSize = 13
            });

            stack.Children.Add(new TextBlock
            {
                Text = "Select a Cookie Profile or browse for a Netscape cookies.txt file to retry:",
                FontWeight = Microsoft.UI.Text.FontWeights.SemiBold,
                FontSize = 13
            });

            stack.Children.Add(combo);

            var dialog = new ContentDialog
            {
                Title = "Authentication / Cookies Required",
                Content = stack,
                PrimaryButtonText = "Enable & Retry",
                SecondaryButtonText = "Go to Settings",
                CloseButtonText = "Cancel",
                DefaultButton = ContentDialogButton.Primary,
                XamlRoot = root
            };

            var res = await dialog.ShowAsync();
            if (res == ContentDialogResult.Secondary)
            {
                App.NavigateToSettings();
            }
            else if (res == ContentDialogResult.Primary)
            {
                var selected = combo.SelectedItem as CookieOptionItem;
                if (selected != null)
                {
                    string? cookiePathToUse = selected.FilePath;
                    if (cookiePathToUse == "__browse__")
                    {
                        var picker = new global::Windows.Storage.Pickers.FileOpenPicker();
                        picker.SuggestedStartLocation = global::Windows.Storage.Pickers.PickerLocationId.DocumentsLibrary;
                        picker.FileTypeFilter.Add(".txt");
                        picker.FileTypeFilter.Add(".cookies");
                        picker.FileTypeFilter.Add("*");

                        var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(App.MainWindow);
                        WinRT.Interop.InitializeWithWindow.Initialize(picker, hwnd);

                        var file = await picker.PickSingleFileAsync();
                        cookiePathToUse = file?.Path;

                        if (!string.IsNullOrWhiteSpace(cookiePathToUse))
                        {
                            string autoName = file?.Name != null ? System.IO.Path.GetFileNameWithoutExtension(file.Name) : "Cookies";
                            if (!string.IsNullOrWhiteSpace(autoName) && autoName.Length > 1)
                            {
                                autoName = char.ToUpper(autoName[0]) + autoName[1..];
                            }
                            cookieService.AddProfile(autoName, cookiePathToUse);
                        }
                    }

                    if (!string.IsNullOrWhiteSpace(cookiePathToUse))
                    {
                        settings.UseCookies = true;
                        settings.ActiveCookieFile = cookiePathToUse;
                        settingsService.SaveSettings(settings);

                        _ = ViewModel.FetchMetadataWithCookieAsync(url, cookiePathToUse);
                    }
                }
            }
        }
        catch { }
        finally
        {
            _isAuthDialogOpen = false;
        }
    }

    private async void ImportCookieAndRetry_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            var picker = new global::Windows.Storage.Pickers.FileOpenPicker();
            picker.SuggestedStartLocation = global::Windows.Storage.Pickers.PickerLocationId.DocumentsLibrary;
            picker.FileTypeFilter.Add(".txt");
            picker.FileTypeFilter.Add(".cookies");
            picker.FileTypeFilter.Add("*");

            var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(App.MainWindow);
            WinRT.Interop.InitializeWithWindow.Initialize(picker, hwnd);

            var file = await picker.PickSingleFileAsync();
            if (file != null)
            {
                var cookieService = App.Services.GetService(typeof(Services.ICookieService)) as Services.ICookieService;
                var settingsService = App.Services.GetService(typeof(Services.ISettingsService)) as Services.ISettingsService;
                if (cookieService == null || settingsService == null) return;

                var settings = settingsService.GetSettings();

                string autoName = file.Name != null ? System.IO.Path.GetFileNameWithoutExtension(file.Name) : "Cookies";
                if (!string.IsNullOrWhiteSpace(autoName) && autoName.Length > 1)
                {
                    autoName = char.ToUpper(autoName[0]) + autoName[1..];
                }
                cookieService.AddProfile(autoName, file.Path);

                settings.UseCookies = true;
                settings.ActiveCookieFile = file.Path;
                settingsService.SaveSettings(settings);

                string urlToRetry = !string.IsNullOrWhiteSpace(ViewModel.UrlInput) ? ViewModel.UrlInput.Trim() : string.Empty;
                if (!string.IsNullOrWhiteSpace(urlToRetry))
                {
                    _ = ViewModel.FetchMetadataWithCookieAsync(urlToRetry, file.Path);
                }
            }
        }
        catch { }
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
