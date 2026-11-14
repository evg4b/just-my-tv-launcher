#!/usr/bin/env bash
# Roll the TV back to a record made by tools/record.sh (docs/OPERATIONS.md, section 4). CHANGES THE TV.
# Usage: TV_HOST=<tv ip> tools/restore.sh [record.json]    (default: the first record, ~/.just-my-tv-launcher/<tv ip>.json)
# Restores HOME, the accessibility services and the enabled state of the recorded packages.
# Never enables launcherx and never uninstalls anything (our launcher is removed by hand in Settings -> Apps).
set -euo pipefail
: "${TV_HOST:?Set TV_HOST to the TV IP address}"
SERIAL="${TV_HOST%:5555}:5555"
REC="${1:-$HOME/.just-my-tv-launcher/${SERIAL%:5555}.json}"

s() { adb -s "$SERIAL" shell "$@" </dev/null | tr -d '\r'; }

adb connect "$SERIAL" >/dev/null
[ "$(adb -s "$SERIAL" get-state 2>/dev/null)" = device ] || { echo "ERROR: $SERIAL is not connected" >&2; exit 1; }

if [ ! -f "$REC" ]; then
  echo "No record at $REC. Current state below; the rollback is manual (Home -> pick the launcher -> Always)."
  s cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1
  s settings get secure enabled_accessibility_services
  exit 1
fi

# Lines: "home <component>", "access <value>", "pkg <name> <enabled>".
while read -r kind a b; do
  case "$kind" in
    home)
      echo "HOME -> $a"
      s cmd package set-home-activity "$a"
      ;;
    access)
      [ "$(s settings get secure enabled_accessibility_services)" = "$a" ] && continue
      echo "accessibility -> $a"
      if [ "$a" = null ]; then s settings delete secure enabled_accessibility_services
      else s settings put secure enabled_accessibility_services "$a"; fi
      ;;
    pkg)
      [ "$b" = absent ] && continue
      now="$(s dumpsys package "$a" | grep -m1 'User 0: ceDataInode' | grep -o 'enabled=[0-9]*' | cut -d= -f2 || true)"
      [ -z "$now" ] || [ "$now" = "$b" ] && continue
      case "$b" in
        0|1)
          [ "$a" = com.google.android.apps.tv.launcherx ] && { echo "skip: launcherx is never enabled"; continue; }
          echo "enable $a"; s pm enable --user 0 "$a" ;;
        2|3) echo "disable $a"; s pm disable-user --user 0 "$a" ;;
      esac
      ;;
  esac
done < <(python3 -I - "$REC" <<'EOF'
import json, sys
r = json.load(open(sys.argv[1]))
print("home", r["home"])
print("access", r["accessibility"])
for p, e in r["packages"].items():
    print("pkg", p, e)
EOF
)
echo "Done. HOME is now:"
s cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1
