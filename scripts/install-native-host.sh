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

# 2. Native package paths (Debian / Arch / RPM / /usr/local / Dev tree)
for bin in /usr/bin/glypdl-host /usr/local/bin/glypdl-host "$HOME/.local/share/glypdl/bin/glypdl-host" "$HOME/Glypdl/bin/glypdl-host"; do
    if [ -x "$bin" ]; then
        exec "$bin" "$@"
    fi
done

# If native binary not found on standard paths, try PATH
if command -v glypdl-host >/dev/null 2>&1; then
    exec glypdl-host "$@"
fi

# Fallback: send 32-bit length-prefixed JSON error to satisfy browser native messaging protocol
python3 -c '
import sys, struct, json
msg = json.dumps({"protocolVersion": 1, "success": False, "error": "Glypdl application executable not found"}).encode("utf-8")
sys.stdout.buffer.write(struct.pack("@I", len(msg)) + msg)
sys.stdout.buffer.flush()
'
exit 1
INNER_EOF

chmod +x "$HOST_WRAPPER"
echo "✓ Host bridge script installed at $HOST_WRAPPER"

# 2. Target directories for all major browsers
BROWSER_DIRS=(
    "$HOME/.mozilla/native-messaging-hosts"
    "$HOME/snap/firefox/common/.mozilla/native-messaging-hosts"
    "$HOME/snap/firefox/current/.mozilla/native-messaging-hosts"
    "$HOME/.librewolf/native-messaging-hosts"
    "$HOME/.config/librewolf/native-messaging-hosts"
    "$HOME/.var/app/org.mozilla.firefox/.mozilla/native-messaging-hosts"
    "$HOME/.var/app/io.gitlab.librewolf-community/.librewolf/native-messaging-hosts"
    "$HOME/.config/google-chrome/NativeMessagingHosts"
    "$HOME/.config/chromium/NativeMessagingHosts"
    "$HOME/.config/BraveSoftware/Brave-Browser/NativeMessagingHosts"
    "$HOME/.config/microsoft-edge/NativeMessagingHosts"
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

# 3. Grant xdg-desktop-portal WebExtensions permission for Snap/Flatpak browsers
# Sandboxed browsers route native messaging through xdg-desktop-portal. If the user
# previously denied the portal permission dialog (or it was never shown), the
# PermissionStore entry is "no" and all native messaging calls fail with
# "An unexpected error occurred". Setting it to "yes" fixes this silently.
if command -v busctl >/dev/null 2>&1; then
    for app in "snap.firefox" "org.mozilla.firefox" "snap.chromium"; do
        busctl --user call \
            org.freedesktop.impl.portal.PermissionStore \
            /org/freedesktop/impl/portal/PermissionStore \
            org.freedesktop.impl.portal.PermissionStore \
            SetPermission sbssas \
            "webextensions" true \
            "$HOST_NAME" \
            "$app" \
            1 "yes" 2>/dev/null || true
    done
    echo "✓ Granted xdg-desktop-portal WebExtensions permission for Snap/Flatpak browsers."
fi

echo ""
echo "🎉 Setup complete! Restart your browser to activate the connection."
echo "======================================================"
