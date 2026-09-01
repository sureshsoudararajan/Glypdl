using System;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Glypdl.Windows.ViewModels;

namespace Glypdl.Windows.Views;

public partial class AboutPage : Page
{
    public AboutViewModel ViewModel { get; }

    public AboutPage()
    {
        InitializeComponent();
        ViewModel = (AboutViewModel)App.Services.GetService(typeof(AboutViewModel))!;
        DataContext = ViewModel;
    }

    private async void YtDlpItem_Click(object sender, RoutedEventArgs e)
    {
        var stack = new StackPanel { Spacing = 12 };
        stack.Children.Add(new TextBlock
        {
            Text = "License: The Unlicense",
            FontWeight = Microsoft.UI.Text.FontWeights.SemiBold
        });
        stack.Children.Add(new TextBlock
        {
            Text = "Glypdl uses yt-dlp as its primary media download engine. yt-dlp is dedicated to the public domain under The Unlicense. The distributed executable also contains additional third-party dependencies with their respective open-source licenses.",
            TextWrapping = TextWrapping.Wrap,
            Foreground = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["TextFillColorSecondaryBrush"]
        });

        var btnSp = new StackPanel { Spacing = 6, Margin = new Thickness(0, 8, 0, 0) };
        btnSp.Children.Add(new HyperlinkButton
        {
            Content = "View Full License (GitHub)",
            NavigateUri = new Uri("https://github.com/yt-dlp/yt-dlp/blob/master/LICENSE"),
            Padding = new Thickness(0, 4, 0, 4)
        });
        btnSp.Children.Add(new HyperlinkButton
        {
            Content = "View Third-Party Licenses (AUTHORS)",
            NavigateUri = new Uri("https://github.com/yt-dlp/yt-dlp/blob/master/AUTHORS"),
            Padding = new Thickness(0, 4, 0, 4)
        });
        btnSp.Children.Add(new HyperlinkButton
        {
            Content = "yt-dlp Project Website",
            NavigateUri = new Uri("https://github.com/yt-dlp/yt-dlp"),
            Padding = new Thickness(0, 4, 0, 4)
        });
        stack.Children.Add(btnSp);

        var dialog = new ContentDialog
        {
            Title = "yt-dlp",
            Content = stack,
            CloseButtonText = "Close",
            XamlRoot = this.XamlRoot
        };

        await dialog.ShowAsync();
    }

    private async void FFmpegItem_Click(object sender, RoutedEventArgs e)
    {
        var stack = new StackPanel { Spacing = 12 };
        stack.Children.Add(new TextBlock
        {
            Text = "License: LGPL v2.1+ / GPL v3 (as applicable)",
            FontWeight = Microsoft.UI.Text.FontWeights.SemiBold
        });
        stack.Children.Add(new TextBlock
        {
            Text = "Glypdl uses FFmpeg for media processing, format conversion, and stream merging. FFmpeg is licensed under the GNU Lesser General Public License (LGPL) version 2.1 or later, with optional GPL components depending on the build configuration.",
            TextWrapping = TextWrapping.Wrap,
            Foreground = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["TextFillColorSecondaryBrush"]
        });

        var btnSp = new StackPanel { Spacing = 6, Margin = new Thickness(0, 8, 0, 0) };
        btnSp.Children.Add(new HyperlinkButton
        {
            Content = "FFmpeg Legal & Licensing Overview",
            NavigateUri = new Uri("https://www.ffmpeg.org/legal.html"),
            Padding = new Thickness(0, 4, 0, 4)
        });
        btnSp.Children.Add(new HyperlinkButton
        {
            Content = "FFmpeg Official Website",
            NavigateUri = new Uri("https://www.ffmpeg.org/"),
            Padding = new Thickness(0, 4, 0, 4)
        });
        btnSp.Children.Add(new HyperlinkButton
        {
            Content = "Gyan.dev FFmpeg Windows Builds",
            NavigateUri = new Uri("https://www.gyan.dev/ffmpeg/builds/"),
            Padding = new Thickness(0, 4, 0, 4)
        });
        stack.Children.Add(btnSp);

        var dialog = new ContentDialog
        {
            Title = "FFmpeg",
            Content = stack,
            CloseButtonText = "Close",
            XamlRoot = this.XamlRoot
        };

        await dialog.ShowAsync();
    }

    private async void FFprobeItem_Click(object sender, RoutedEventArgs e)
    {
        var stack = new StackPanel { Spacing = 12 };
        stack.Children.Add(new TextBlock
        {
            Text = "License: LGPL v2.1+ / FFmpeg Project",
            FontWeight = Microsoft.UI.Text.FontWeights.SemiBold
        });
        stack.Children.Add(new TextBlock
        {
            Text = "FFprobe is a multimedia stream analyzer and metadata probing utility from the FFmpeg project, used by yt-dlp to inspect container formats and audio/video codecs.",
            TextWrapping = TextWrapping.Wrap,
            Foreground = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["TextFillColorSecondaryBrush"]
        });

        var btnSp = new StackPanel { Spacing = 6, Margin = new Thickness(0, 8, 0, 0) };
        btnSp.Children.Add(new HyperlinkButton
        {
            Content = "FFprobe Documentation",
            NavigateUri = new Uri("https://ffmpeg.org/ffprobe.html"),
            Padding = new Thickness(0, 4, 0, 4)
        });
        btnSp.Children.Add(new HyperlinkButton
        {
            Content = "FFmpeg Project Legal Information",
            NavigateUri = new Uri("https://www.ffmpeg.org/legal.html"),
            Padding = new Thickness(0, 4, 0, 4)
        });
        stack.Children.Add(btnSp);

        var dialog = new ContentDialog
        {
            Title = "FFprobe",
            Content = stack,
            CloseButtonText = "Close",
            XamlRoot = this.XamlRoot
        };

        await dialog.ShowAsync();
    }

    private async void OtherDependenciesItem_Click(object sender, RoutedEventArgs e)
    {
        var stack = new StackPanel { Spacing = 12, Margin = new Thickness(0, 4, 0, 4) };
        stack.Children.Add(CreateDepItem("Microsoft Windows App SDK / WinUI 3", "MIT License", "https://github.com/microsoft/WindowsAppSDK"));
        stack.Children.Add(CreateDepItem("CommunityToolkit.Mvvm", "MIT License", "https://github.com/CommunityToolkit/dotnet"));
        stack.Children.Add(CreateDepItem("Microsoft.Data.Sqlite", "MIT License", "https://github.com/dotnet/efcore"));
        stack.Children.Add(CreateDepItem("Dapper", "Apache License 2.0", "https://github.com/DapperLib/Dapper"));
        stack.Children.Add(CreateDepItem("WinUIEdit", "MIT License", "https://github.com/microsoft/WinUIEdit"));
        stack.Children.Add(CreateDepItem("Microsoft.Web.WebView2", "Microsoft Software License", "https://developer.microsoft.com/en-us/microsoft-edge/webview2/"));

        var scroll = new ScrollViewer
        {
            MaxHeight = 320,
            HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
            VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
            Content = stack
        };

        var dialog = new ContentDialog
        {
            Title = "Open Source Dependencies",
            Content = scroll,
            CloseButtonText = "Close",
            XamlRoot = this.XamlRoot
        };

        await dialog.ShowAsync();
    }

    private UIElement CreateDepItem(string name, string license, string url)
    {
        var grid = new Grid { Margin = new Thickness(0, 2, 0, 2) };
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        var sp = new StackPanel { Spacing = 2, VerticalAlignment = VerticalAlignment.Center };
        sp.Children.Add(new TextBlock { Text = name, FontWeight = Microsoft.UI.Text.FontWeights.SemiBold, FontSize = 13 });
        sp.Children.Add(new TextBlock { Text = license, FontSize = 11, Foreground = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["TextFillColorSecondaryBrush"] });
        Grid.SetColumn(sp, 0);

        var link = new HyperlinkButton { Content = "Source", NavigateUri = new Uri(url), FontSize = 12, Padding = new Thickness(8, 2, 8, 2), VerticalAlignment = VerticalAlignment.Center };
        Grid.SetColumn(link, 1);

        grid.Children.Add(sp);
        grid.Children.Add(link);
        return grid;
    }
}
