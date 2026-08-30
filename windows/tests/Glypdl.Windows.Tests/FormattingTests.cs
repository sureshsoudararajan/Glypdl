using Glypdl.Windows.Utilities;
using Xunit;

namespace Glypdl.Windows.Tests;

public class FormattingTests
{
    [Theory]
    [InlineData(0, "0 B")]
    [InlineData(1024, "1 KB")]
    [InlineData(1048576, "1 MB")]
    [InlineData(1073741824, "1 GB")]
    public void FormatSize_ShouldReturnCorrectUnits(long bytes, string expected)
    {
        string actual = FormattingUtils.FormatSize(bytes);
        Assert.Equal(expected, actual);
    }

    [Theory]
    [InlineData(45, "0:45")]
    [InlineData(75, "1:15")]
    [InlineData(3665, "1:01:05")]
    public void FormatDuration_ShouldFormatCorrectly(int seconds, string expected)
    {
        string actual = FormattingUtils.FormatDuration(seconds);
        Assert.Equal(expected, actual);
    }

    [Fact]
    public void ParseProgressLine_ShouldExtractAccurateMetrics()
    {
        string line = "[download]  45.5% of 100.00MiB at  5.50MiB/s ETA 00:10";
        var info = FormattingUtils.ParseProgressLine(line);

        Assert.NotNull(info);
        Assert.Equal(45.5, info!.Percent);
        Assert.Equal(10, info.EtaSeconds);
        Assert.Equal("Downloading", info.Status);
    }
}
