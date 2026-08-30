using Glypdl.Windows.Models;

namespace Glypdl.Windows.Services;

public interface IHistoryService
{
    Task<List<HistoryEntry>> GetAllAsync();
    Task<List<HistoryEntry>> SearchAsync(string query);
    Task AddEntryAsync(HistoryEntry entry);
    Task DeleteEntryAsync(string id);
    Task ClearAllAsync();
}
