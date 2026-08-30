using System.Globalization;
using Glypdl.Windows.Models;
using Glypdl.Windows.Utilities;
using Microsoft.Data.Sqlite;

namespace Glypdl.Windows.Services;

public class HistoryService : IHistoryService
{
    private readonly string _connectionString;

    public HistoryService()
    {
        string dbPath = PathUtils.GetDatabasePath();
        _connectionString = $"Data Source={dbPath}";
        InitializeDatabase();
    }

    private void InitializeDatabase()
    {
        using var connection = new SqliteConnection(_connectionString);
        connection.Open();

        string sql = @"
            CREATE TABLE IF NOT EXISTS history (
                id TEXT PRIMARY KEY,
                url TEXT NOT NULL,
                title TEXT,
                uploader TEXT,
                thumbnail_path TEXT,
                download_path TEXT,
                format TEXT,
                file_size INTEGER,
                status TEXT,
                timestamp TEXT,
                duration INTEGER,
                mode TEXT,
                quality TEXT
            );";

        using var command = new SqliteCommand(sql, connection);
        command.ExecuteNonQuery();
    }

    public async Task<List<HistoryEntry>> GetAllAsync()
    {
        var list = new List<HistoryEntry>();
        using var connection = new SqliteConnection(_connectionString);
        await connection.OpenAsync();

        string sql = "SELECT * FROM history ORDER BY timestamp DESC;";
        using var command = new SqliteCommand(sql, connection);
        using var reader = await command.ExecuteReaderAsync();

        while (await reader.ReadAsync())
        {
            list.Add(ReadEntry(reader));
        }

        return list;
    }

    public async Task<List<HistoryEntry>> SearchAsync(string query)
    {
        var list = new List<HistoryEntry>();
        using var connection = new SqliteConnection(_connectionString);
        await connection.OpenAsync();

        string sql = "SELECT * FROM history WHERE title LIKE @q OR url LIKE @q ORDER BY timestamp DESC;";
        using var command = new SqliteCommand(sql, connection);
        command.Parameters.AddWithValue("@q", $"%{query}%");
        using var reader = await command.ExecuteReaderAsync();

        while (await reader.ReadAsync())
        {
            list.Add(ReadEntry(reader));
        }

        return list;
    }

    public async Task AddEntryAsync(HistoryEntry entry)
    {
        using var connection = new SqliteConnection(_connectionString);
        await connection.OpenAsync();

        string sql = @"
            INSERT OR REPLACE INTO history 
            (id, url, title, uploader, thumbnail_path, download_path, format, file_size, status, timestamp, duration, mode, quality)
            VALUES (@id, @url, @title, @uploader, @thumbnail_path, @download_path, @format, @file_size, @status, @timestamp, @duration, @mode, @quality);";

        using var command = new SqliteCommand(sql, connection);
        command.Parameters.AddWithValue("@id", entry.Id);
        command.Parameters.AddWithValue("@url", entry.Url);
        command.Parameters.AddWithValue("@title", entry.Title);
        command.Parameters.AddWithValue("@uploader", entry.Uploader);
        command.Parameters.AddWithValue("@thumbnail_path", entry.ThumbnailPath);
        command.Parameters.AddWithValue("@download_path", entry.DownloadPath);
        command.Parameters.AddWithValue("@format", entry.Format);
        command.Parameters.AddWithValue("@file_size", entry.FileSize);
        command.Parameters.AddWithValue("@status", entry.Status);
        command.Parameters.AddWithValue("@timestamp", entry.Timestamp.ToString("o"));
        command.Parameters.AddWithValue("@duration", entry.Duration);
        command.Parameters.AddWithValue("@mode", entry.Mode);
        command.Parameters.AddWithValue("@quality", entry.Quality);

        await command.ExecuteNonQueryAsync();
    }

    public async Task DeleteEntryAsync(string id)
    {
        using var connection = new SqliteConnection(_connectionString);
        await connection.OpenAsync();

        string sql = "DELETE FROM history WHERE id = @id;";
        using var command = new SqliteCommand(sql, connection);
        command.Parameters.AddWithValue("@id", id);

        await command.ExecuteNonQueryAsync();
    }

    public async Task ClearAllAsync()
    {
        using var connection = new SqliteConnection(_connectionString);
        await connection.OpenAsync();

        string sql = "DELETE FROM history;";
        using var command = new SqliteCommand(sql, connection);

        await command.ExecuteNonQueryAsync();
    }

    private static HistoryEntry ReadEntry(SqliteDataReader reader)
    {
        return new HistoryEntry
        {
            Id = reader.GetString(reader.GetOrdinal("id")),
            Url = reader.IsDBNull(reader.GetOrdinal("url")) ? "" : reader.GetString(reader.GetOrdinal("url")),
            Title = reader.IsDBNull(reader.GetOrdinal("title")) ? "" : reader.GetString(reader.GetOrdinal("title")),
            Uploader = reader.IsDBNull(reader.GetOrdinal("uploader")) ? "" : reader.GetString(reader.GetOrdinal("uploader")),
            ThumbnailPath = reader.IsDBNull(reader.GetOrdinal("thumbnail_path")) ? "" : reader.GetString(reader.GetOrdinal("thumbnail_path")),
            DownloadPath = reader.IsDBNull(reader.GetOrdinal("download_path")) ? "" : reader.GetString(reader.GetOrdinal("download_path")),
            Format = reader.IsDBNull(reader.GetOrdinal("format")) ? "" : reader.GetString(reader.GetOrdinal("format")),
            FileSize = reader.IsDBNull(reader.GetOrdinal("file_size")) ? 0 : reader.GetInt64(reader.GetOrdinal("file_size")),
            Status = reader.IsDBNull(reader.GetOrdinal("status")) ? "" : reader.GetString(reader.GetOrdinal("status")),
            Timestamp = reader.IsDBNull(reader.GetOrdinal("timestamp")) ? DateTime.UtcNow : DateTime.Parse(reader.GetString(reader.GetOrdinal("timestamp")), null, DateTimeStyles.RoundtripKind),
            Duration = reader.IsDBNull(reader.GetOrdinal("duration")) ? 0 : reader.GetInt32(reader.GetOrdinal("duration")),
            Mode = reader.IsDBNull(reader.GetOrdinal("mode")) ? "" : reader.GetString(reader.GetOrdinal("mode")),
            Quality = reader.IsDBNull(reader.GetOrdinal("quality")) ? "" : reader.GetString(reader.GetOrdinal("quality"))
        };
    }
}
