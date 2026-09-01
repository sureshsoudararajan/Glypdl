using System;
using System.IO;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Glypdl.Windows.ViewModels;
using Glypdl.Windows.Views;

namespace Glypdl.Windows;

public partial class MainWindow : Window
{
    public MainWindow()
    {
        try
        {
            InitializeComponent();
            ExtendsContentIntoTitleBar = true;
            SetTitleBar(AppTitleBar);
            Title = "Glypdl";

            try
            {
                var iconPath = Path.Combine(AppContext.BaseDirectory, "Assets", "icon.ico");
                if (File.Exists(iconPath))
                {
                    AppWindow.SetIcon(iconPath);
                }
                AppWindow.Resize(new global::Windows.Graphics.SizeInt32(1100, 750));
            }
            catch { }

            if (Content is FrameworkElement root)
            {
                root.ActualThemeChanged += (s, e) =>
                {
                    App.UpdateTitleBar();
                };
            }

            NavView.Loaded += (s, e) =>
            {
                try
                {
                    if (NavView.MenuItems.Count > 0)
                    {
                        NavView.SelectedItem = NavView.MenuItems[0];
                    }
                    ContentFrame.Navigate(typeof(HomePage));
                }
                catch (Exception ex)
                {
                    var dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Glypdl");
                    Directory.CreateDirectory(dir);
                    File.WriteAllText(Path.Combine(dir, "nav_crash.log"), ex.ToString());
                }
            };
        }
        catch (Exception ex)
        {
            var dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Glypdl");
            Directory.CreateDirectory(dir);
            File.WriteAllText(Path.Combine(dir, "mainwindow_crash.log"), ex.ToString());
            throw;
        }
    }

    private void NavView_SelectionChanged(NavigationView sender, NavigationViewSelectionChangedEventArgs args)
    {
        if (args.IsSettingsSelected)
        {
            ContentFrame.Navigate(typeof(SettingsPage));
            return;
        }

        var item = args.SelectedItemContainer as NavigationViewItem ?? args.SelectedItem as NavigationViewItem;
        if (item != null)
        {
            string? tag = item.Tag?.ToString();
            switch (tag)
            {
                case "Home":
                    ContentFrame.Navigate(typeof(HomePage));
                    break;
                case "Downloads":
                    ContentFrame.Navigate(typeof(DownloadsPage));
                    break;
                case "History":
                    ContentFrame.Navigate(typeof(HistoryPage));
                    break;
                case "About":
                    ContentFrame.Navigate(typeof(AboutPage));
                    break;
            }
        }
    }

    public void NavigateToHome(string? url = null)
    {
        DispatcherQueue.TryEnqueue(() =>
        {
            foreach (var item in NavView.MenuItems)
            {
                if (item is NavigationViewItem navItem && navItem.Tag?.ToString() == "Home")
                {
                    NavView.SelectedItem = navItem;
                    break;
                }
            }
            ContentFrame.Navigate(typeof(HomePage));

            if (!string.IsNullOrWhiteSpace(url))
            {
                var homeVm = (HomeViewModel)App.Services.GetService(typeof(HomeViewModel))!;
                homeVm.UrlInput = url;
                _ = homeVm.FetchMetadataAsync();
            }
        });
    }

    public void NavigateToDownloads()
    {
        DispatcherQueue.TryEnqueue(() =>
        {
            foreach (var item in NavView.MenuItems)
            {
                if (item is NavigationViewItem navItem && navItem.Tag?.ToString() == "Downloads")
                {
                    NavView.SelectedItem = navItem;
                    break;
                }
            }
            ContentFrame.Navigate(typeof(DownloadsPage));
        });
    }

    public void NavigateToSettings()
    {
        DispatcherQueue.TryEnqueue(() =>
        {
            NavView.SelectedItem = NavView.SettingsItem;
            ContentFrame.Navigate(typeof(SettingsPage));
        });
    }
}
