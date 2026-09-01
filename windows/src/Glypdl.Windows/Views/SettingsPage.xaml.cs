using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Glypdl.Windows.Models;
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

        Loaded += (s, e) =>
        {
            ViewModel.LoadSettings();
        };
    }

    public Visibility StringToVisibility(string? str) => !string.IsNullOrWhiteSpace(str) ? Visibility.Visible : Visibility.Collapsed;
    public bool StringToBool(string? str) => !string.IsNullOrWhiteSpace(str);

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

    private async void ChooseCookieFile_Click(object sender, RoutedEventArgs e)
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
                ViewModel.SetActiveCookieFile(file.Path);
            }
        }
        catch { }
    }

    private void ClearCookieFile_Click(object sender, RoutedEventArgs e)
    {
        ViewModel.ClearActiveCookie();
    }

    private void UseProfile_Click(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement fe && fe.Tag is CookieProfile profile)
        {
            ViewModel.UseProfile(profile);
        }
    }

    private void DeleteProfile_Click(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement fe && fe.Tag is CookieProfile profile)
        {
            ViewModel.RemoveCookieProfile(profile);
        }
    }

    private async void AddProfile_Click(object sender, RoutedEventArgs e)
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
            if (file == null) return;

            string defaultName = System.IO.Path.GetFileNameWithoutExtension(file.Name);
            if (!string.IsNullOrWhiteSpace(defaultName) && defaultName.Length > 1)
            {
                defaultName = char.ToUpper(defaultName[0]) + defaultName[1..];
            }

            var inputTextBox = new TextBox
            {
                Text = defaultName,
                PlaceholderText = "Profile Name (e.g. YouTube, Vimeo)",
                Margin = new Thickness(0, 12, 0, 0)
            };

            var dialog = new ContentDialog
            {
                Title = "Name Cookie Profile",
                Content = new StackPanel
                {
                    Spacing = 8,
                    Children =
                    {
                        new TextBlock { Text = $"Enter a profile name for:\n{file.Path}", TextWrapping = TextWrapping.Wrap, Opacity = 0.8 },
                        inputTextBox
                    }
                },
                PrimaryButtonText = "Add Profile",
                CloseButtonText = "Cancel",
                DefaultButton = ContentDialogButton.Primary,
                XamlRoot = this.XamlRoot ?? App.MainWindow?.Content?.XamlRoot
            };

            var result = await dialog.ShowAsync();
            if (result == ContentDialogResult.Primary)
            {
                string pName = !string.IsNullOrWhiteSpace(inputTextBox.Text) ? inputTextBox.Text.Trim() : defaultName;
                ViewModel.AddCookieProfile(pName, file.Path);
            }
        }
        catch { }
    }
}
