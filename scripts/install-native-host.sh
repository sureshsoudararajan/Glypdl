#!/usr/bin/env bash
# ==============================================================================
# Glypdl Native Messaging Host Installer
# Registers the browser companion bridge for Firefox, LibreWolf, and Chromium.
# Works for Flatpak, Arch Linux, Debian/Ubuntu, Fedora, AppImage, and Tarball.
# ==============================================================================

set -e

APP_ID="io.github.sureshsoudararajan.Glypdl"
HOST_NAME="io.github.sureshsoudararajan.glypdl"
LOCAL_BIN="$HOME/.local/bin"
HOST_WRAPPER="$LOCAL_BIN/glypdl-host"

echo "======================================================"
echo "  Installing Glypdl Browser Companion Native Host     "
echo "======================================================"

mkdir -p "$LOCAL_BIN"

# 1. Create ~/.local/bin/glypdl-host bridge script
cat << 'INNER_EOF' > "$HOST_WRAPPER"
#!/usr/bin/env bash
# Glypdl Native Messaging Host Bridge
# Auto-detects whether Glypdl is running via Flatpak or native package.

APP_ID="io.github.sureshsoudararajan.Glypdl"

# 1. If Flatpak is installed and app is present, run inside container
if command -v flatpak >/dev/null 2>&1; then
    if flatpak info "$APP_ID" >/dev/null 2>&1; then
        exec flatpak run --command=/app/bin/glypdl-host "$APP_ID" "$@"
    fi
fi

# 2. Native package paths (Debian / Arch / RPM / /usr/local)
for bin in /usr/bin/glypdl-host /usr/local/bin/glypdl-host "$HOME/.local/share/glypdl/bin/glypdl-host"; do
    if [ -x "$bin" ]; then
        exec "$bin" "$@"
    fi
done

echo '{"protocolVersion": 1, "success": false, "error": "Glypdl application executable not found"}'
exit 1
INNER_EOF

chmod +x "$HOST_WRAPPER"
echo "✓ Host bridge script installed at $HOST_WRAPPER"

# 2. Target directories for all major browsers
BROWSER_DIRS=(
    "$HOME/.mozilla/native-messaging-hosts"
    "$HOME/.librewolf/native-messaging-hosts"
    "$HOME/.config/librewolf/native-messaging-hosts"
    "$HOME/.var/app/org.mozilla.firefox/.mozilla/native-messaging-hosts"
    "$HOME/.var/app/io.gitlab.librewolf-community/.librewolf/native-messaging-hosts"
    "$HOME/.config/google-chrome/NativeMessagingHosts"
    "$HOME/.config/chromium/NativeMessagingHosts"
    "$HOME/.config/BraveSoftware/Brave-Browser/NativeMessagingHosts"
)

MANIFEST_CONTENT=$(cat << INNER_EOF
{
  "name": "$HOST_NAME",
  "description": "Glypdl Native Messaging Host for Firefox, LibreWolf and Chromium",
  "path": "$HOST_WRAPPER",
  "type": "stdio",
  "allowed_extensions": [
    "glypdl@suresh.io"
  ]
}
INNER_EOF
)

count=0
for dir in "${BROWSER_DIRS[@]}"; do
    mkdir -p "$dir"
    echo "$MANIFEST_CONTENT" > "$dir/$HOST_NAME.json"
    count=$((count + 1))
done

echo "✓ Registered manifest in $count browser target directories."
echo ""
echo "🎉 Setup complete! Restart your browser to activate the connection."
echo "======================================================"
