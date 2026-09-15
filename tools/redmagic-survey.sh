#!/data/data/com.termux/files/usr/bin/bash
# Fixed, read-only REDMAGIC survey for Game Star Box.
# Requires Shizuku rish configured for Termux.

set -u

MODE="${1:-snapshot}"
DURATION="${2:-60}"

case "$MODE" in
  snapshot|trace) ;;
  *) echo "usage: $0 [snapshot|trace] [seconds]" >&2; exit 2 ;;
esac

if ! [[ "$DURATION" =~ ^[0-9]+$ ]] || [ "$DURATION" -lt 10 ] || [ "$DURATION" -gt 180 ]; then
  echo "duration must be 10..180 seconds" >&2
  exit 2
fi

RISH="${GSB_RISH:-}"
if [ -z "$RISH" ]; then
  if command -v rish >/dev/null 2>&1; then
    RISH="$(command -v rish)"
  elif [ -x ./rish ]; then
    RISH=./rish
  else
    echo "rish not found; configure it from Shizuku > Use Shizuku in terminal apps" >&2
    exit 3
  fi
fi

run() { "$RISH" -c "$1"; }

IDENTITY="$(run 'id' 2>/dev/null || true)"
case "$IDENTITY" in
  *"uid=2000(shell)"*|*"uid=0(root)"*) ;;
  *) echo "Shizuku/rish is not running with shell/root identity: $IDENTITY" >&2; exit 4 ;;
esac

STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="${GSB_SURVEY_OUT:-$PWD/redmagic-survey-$STAMP}"
mkdir -p "$OUT"/{device,packages,settings,services,trace}

capture() {
  local file="$1"
  local command="$2"
  run "$command" >"$OUT/$file" 2>&1 || true
}

snapshot_settings() {
  local file="$1"
  capture "$file" "for k in gcs_need_kill_game_launcher nubia_game_scene nubia_game_mode cc_game_mis_operate virtual_game_key; do printf '%s=' \"\$k\"; settings get global \"\$k\" 2>/dev/null; done"
}

echo "[GSB-SURVEY] output: $OUT"

cat >"$OUT/README.txt" <<EOF
Game Star Box REDMAGIC survey
mode=$MODE
started=$(date -Iseconds 2>/dev/null || date)
identity=$IDENTITY
policy=read-only fixed probes only
EOF

capture "device/build.txt" "printf 'manufacturer='; getprop ro.product.manufacturer; printf 'brand='; getprop ro.product.brand; printf 'model='; getprop ro.product.model; printf 'device='; getprop ro.product.device; printf 'android='; getprop ro.build.version.release; printf 'sdk='; getprop ro.build.version.sdk; printf 'build='; getprop ro.build.id"
capture "packages/nubia.txt" "pm list packages -f | grep -Ei 'nubia|redmagic|gamelauncher|gameassist|gamespace'"
capture "packages/gamelauncher.txt" "dumpsys package cn.nubia.gamelauncher 2>/dev/null"
capture "packages/gameassist.txt" "dumpsys package cn.nubia.gameassist 2>/dev/null"
capture "packages/systemmanager.txt" "dumpsys package cn.nubia.systemmanager 2>/dev/null"
capture "services/binder.txt" "service list | grep -Ei 'nubia|redmagic|game|assist|perf|power|thermal|fan|trigger|shoulder|network|qos'"
capture "services/dumpsys.txt" "dumpsys -l | grep -Ei 'nubia|redmagic|game|assist|perf|power|thermal|fan|trigger|shoulder|network|qos'"
capture "services/processes.txt" "ps -A 2>/dev/null | grep -Ei 'nubia|redmagic|gamelauncher|gameassist|systemmgr'"
snapshot_settings "settings/known.txt"

if [ "$MODE" = trace ]; then
  snapshot_settings "trace/before.txt"
  START="$(run 'date +%s' 2>/dev/null | tr -dc '0-9')"
  [ -n "$START" ] || START="$(date +%s)"

  echo "[GSB-SURVEY] next $DURATION seconds: launch one game already registered in REDMAGIC Game Space, wait for it to open, then return to launcher."

  SAMPLES=$((DURATION * 2))
  TRACE_CMD="i=0; while [ \"\$i\" -lt $SAMPLES ]; do ts=\$(date +%s); a=\$(settings get global gcs_need_kill_game_launcher 2>/dev/null); b=\$(settings get global nubia_game_scene 2>/dev/null); c=\$(settings get global nubia_game_mode 2>/dev/null); d=\$(settings get global cc_game_mis_operate 2>/dev/null); e=\$(settings get global virtual_game_key 2>/dev/null); printf '%04d %s gcs=%s scene=%s mode=%s mis=%s virtual=%s\\n' \"\$i\" \"\$ts\" \"\$a\" \"\$b\" \"\$c\" \"\$d\" \"\$e\"; i=\$((i+1)); sleep 0.5; done"
  run "$TRACE_CMD" >"$OUT/trace/settings-timeline.txt" 2>&1 || true

  snapshot_settings "trace/after.txt"
  diff -u "$OUT/trace/before.txt" "$OUT/trace/after.txt" >"$OUT/trace/settings.diff" 2>&1 || true

  run "logcat -b main -b system -d -v epoch 2>/dev/null" 2>/dev/null \
    | awk -v start="$START" '($1 + 0) >= start' \
    | grep -Ei 'ActivityTaskManager|ActivityManager|WindowManager|nubia|redmagic|gamelauncher|gameassist|SystemMgr|notifyActivityResumed|nubia_game_|gcs_need_kill_game_launcher|virtual_game_key|cc_game_mis_operate|shoulder|trigger|fan|thermal|boost|performance|qos|latency' \
    >"$OUT/trace/logcat.txt" || true
fi

ARCHIVE="$OUT.tar.gz"
if command -v tar >/dev/null 2>&1; then
  tar -C "$(dirname "$OUT")" -czf "$ARCHIVE" "$(basename "$OUT")" 2>/dev/null || true
fi

echo "[GSB-SURVEY] complete"
echo "[GSB-SURVEY] folder: $OUT"
[ -f "$ARCHIVE" ] && echo "[GSB-SURVEY] archive: $ARCHIVE"
