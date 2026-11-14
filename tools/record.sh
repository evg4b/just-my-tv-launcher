#!/usr/bin/env bash
# Record the TV state before changing it (docs/OPERATIONS.md, section 3). Read-only on the TV.
# Usage: TV_HOST=<tv ip> tools/record.sh
# Writes ~/.just-my-tv-launcher/<tv ip>.json; an existing record is never overwritten, a later one gets a date suffix.
set -euo pipefail
: "${TV_HOST:?Set TV_HOST to the TV IP address}"
SERIAL="${TV_HOST%:5555}:5555"
DIR="$HOME/.just-my-tv-launcher"
# Public packages from OPERATIONS 3; every app with a HOME activity is added below, so private ones need no name here.
PKGS=(com.google.android.apps.tv.launcherx flar2.homebutton com.conreo.couchytv org.smarttube.stable)

s() { adb -s "$SERIAL" shell "$@" </dev/null | tr -d '\r'; }

adb connect "$SERIAL" >/dev/null
[ "$(adb -s "$SERIAL" get-state 2>/dev/null)" = device ] || { echo "ERROR: $SERIAL is not connected" >&2; exit 1; }

HOME_ACT="$(s cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1 | tr -d ' ')"
while read -r comp; do PKGS+=("${comp%%/*}"); done < <(
  s cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME | grep '/' | tr -d ' ')
ACCESS="$(s settings get secure enabled_accessibility_services)"
DISABLED="$(s pm list packages -d | sed 's/^package://')"

# "<pkg> <enabled>" per package: enabled is the User 0 value (0 default, 1 on, 2 off, 3 off by user), or "absent".
STATES=""
for p in $(printf '%s\n' "${PKGS[@]}" | sort -u); do
  e="$(s dumpsys package "$p" | grep -m1 'User 0: ceDataInode' | grep -o 'enabled=[0-9]*' | cut -d= -f2 || true)"
  STATES+="$p ${e:-absent}"$'\n'
done

mkdir -p "$DIR"
OUT="$DIR/${SERIAL%:5555}.json"
[ -e "$OUT" ] && OUT="$DIR/${SERIAL%:5555}-$(date +%Y%m%d-%H%M%S).json"

python3 -I - "$OUT" "${SERIAL%:5555}" "$HOME_ACT" "$ACCESS" "$DISABLED" "$STATES" <<'EOF'
import json, sys, datetime
out, tv, home, access, disabled, states = sys.argv[1:]
rec = {
    "tv": tv,
    "recorded": datetime.datetime.now().isoformat(timespec="seconds"),
    "home": home,
    "packages": dict(l.split(" ", 1) for l in states.splitlines() if l),
    "accessibility": access,
    "disabled": disabled.split(),
}
with open(out, "x") as f:  # "x": never overwrite a record
    json.dump(rec, f, indent=2)
print(out)
print(json.dumps({k: rec[k] for k in ("home", "packages", "accessibility")}, indent=2))
EOF
