namespace Glypdl.Windows.Utilities;

public static class PathUtils
{
    private static readonly string AppDataFolder = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "Glypdl"
    );

    public static string GetAppDataDir()
    {
        Directory.CreateDirectory(AppDataFolder);
        return AppDataFolder;
    }

    public static string GetCacheDir()
    {
        string path = Path.Combine(GetAppDataDir(), "Cache");
        Directory.CreateDirectory(path);
        return path;
    }

    public static string GetThumbnailsDir()
    {
        string path = Path.Combine(GetAppDataDir(), "thumbnails");
        Directory.CreateDirectory(path);
        return path;
    }

    public static string GetBinDir()
    {
        string path = Path.Combine(GetAppDataDir(), "bin");
        Directory.CreateDirectory(path);
        return path;
    }

    public static string GetDatabasePath()
    {
        return Path.Combine(GetAppDataDir(), "history.db");
    }

    public static string GetConfigFilePath()
    {
        return Path.Combine(GetAppDataDir(), "config.json");
    }

    public static string GetProfilesFilePath()
    {
        return Path.Combine(GetAppDataDir(), "profiles.json");
    }

    public static string GetDefaultDownloadDirectory()
    {
        return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads");
    }
}
