using System;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Data;
using Glypdl.Windows.Models;

namespace Glypdl.Windows.Converters;

public class ActiveStateToVisibilityConverter : IValueConverter
{
    public object Convert(object? value, Type targetType, object? parameter, string? language)
    {
        if (value is DownloadState state)
        {
            bool isActive = state == DownloadState.Downloading || state == DownloadState.Queued || 
                            state == DownloadState.FetchingInfo || state == DownloadState.Processing || 
                            state == DownloadState.Merging || state == DownloadState.Converting;
            return isActive ? Visibility.Visible : Visibility.Collapsed;
        }
        return Visibility.Collapsed;
    }

    public object ConvertBack(object? value, Type targetType, object? parameter, string? language) => throw new NotImplementedException();
}

public class CompletedStateToVisibilityConverter : IValueConverter
{
    public object Convert(object? value, Type targetType, object? parameter, string? language)
    {
        if (value is DownloadState state)
        {
            return state == DownloadState.Completed ? Visibility.Visible : Visibility.Collapsed;
        }
        return Visibility.Collapsed;
    }

    public object ConvertBack(object? value, Type targetType, object? parameter, string? language) => throw new NotImplementedException();
}

public class BoolToVisibilityConverter : IValueConverter
{
    public object Convert(object? value, Type targetType, object? parameter, string? language)
    {
        return value is true ? Visibility.Visible : Visibility.Collapsed;
    }

    public object ConvertBack(object? value, Type targetType, object? parameter, string? language) => throw new NotImplementedException();
}