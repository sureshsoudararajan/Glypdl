using System.Diagnostics;
using System.Text;

namespace Glypdl.Windows.Utilities;

public class ProcessResult
{
    public int ExitCode { get; set; }
    public string StandardOutput { get; set; } = string.Empty;
    public string StandardError { get; set; } = string.Empty;
    public bool Success => ExitCode == 0;
}

public static class ProcessRunner
{
    public static async Task<ProcessResult> RunAsync(
        string executable,
        IEnumerable<string> arguments,
        CancellationToken cancellationToken = default,
        string? workingDirectory = null)
    {
        var psi = new ProcessStartInfo
        {
            FileName = executable,
            UseShellExecute = false,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            CreateNoWindow = true,
            StandardOutputEncoding = Encoding.UTF8,
            StandardErrorEncoding = Encoding.UTF8
        };

        if (!string.IsNullOrWhiteSpace(workingDirectory))
        {
            psi.WorkingDirectory = workingDirectory;
        }

        foreach (var arg in arguments)
        {
            psi.ArgumentList.Add(arg);
        }

        using var process = new Process { StartInfo = psi };
        var stdout = new StringBuilder();
        var stderr = new StringBuilder();

        process.OutputDataReceived += (_, e) =>
        {
            if (e.Data != null) stdout.AppendLine(e.Data);
        };
        process.ErrorDataReceived += (_, e) =>
        {
            if (e.Data != null) stderr.AppendLine(e.Data);
        };

        process.Start();
        process.BeginOutputReadLine();
        process.BeginErrorReadLine();

        await process.WaitForExitAsync(cancellationToken);

        return new ProcessResult
        {
            ExitCode = process.ExitCode,
            StandardOutput = stdout.ToString().Trim(),
            StandardError = stderr.ToString().Trim()
        };
    }

    public static Process StartStreaming(
        string executable,
        IEnumerable<string> arguments,
        Action<string> onLine,
        Action<string>? onError = null,
        string? workingDirectory = null)
    {
        var psi = new ProcessStartInfo
        {
            FileName = executable,
            UseShellExecute = false,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            CreateNoWindow = true,
            StandardOutputEncoding = Encoding.UTF8,
            StandardErrorEncoding = Encoding.UTF8
        };

        if (!string.IsNullOrWhiteSpace(workingDirectory))
        {
            psi.WorkingDirectory = workingDirectory;
        }

        foreach (var arg in arguments)
        {
            psi.ArgumentList.Add(arg);
        }

        var process = new Process { StartInfo = psi };

        process.OutputDataReceived += (_, e) =>
        {
            if (e.Data != null) onLine(e.Data);
        };
        process.ErrorDataReceived += (_, e) =>
        {
            if (e.Data != null && onError != null) onError(e.Data);
        };

        process.Start();
        process.BeginOutputReadLine();
        process.BeginErrorReadLine();

        return process;
    }
}
