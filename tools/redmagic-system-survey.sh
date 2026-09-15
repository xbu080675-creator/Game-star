#!/data/data/com.termux/files/usr/bin/bash
# Game Star Box / REDMAGIC system survey
# Read-only device reconnaissance for Game Session integration research.
# This script intentionally does not write Android Settings, sysfs, app data, or package state.

set -uo pipefail

SCRIPT_VERSION="2026-09-16.1"
MODE="${1:-snapshot}"
DURATION="${2:-60}"

case "$MODE" in
  snapshot|trace) ;;
  *)
    echo "Usage: $0 [snapshot|trace] [seconds]" >&2
    exit 2
    ;;
esac

if ! [[ "$DURATION" =~ ^[0-9]+$ ]] || [ "$DURATION" -lt 5 ] || [ "$DURATION" -gt 300 ]; then
  echo "[GSB-SURVEY] trace duration must be an integer between 5 and 300 seconds" >&2
  exit 2
fi

find_rish() {
  if [ -n "${GSB_RISH:-}" ] && [ -x "${GSB_RISH}" ]; then
    printf '%s\n' "$GSB_RISH"
    return 0
  fi
  if command -v rish >/dev/null 2>&1; then
    command -v rish
    return 0
  fi
  if [ -x "./rish" ]; then
    printf '%s\n' "./rish"
    return 0
  fi
  if [ -x "$HOME/rish" ]; then
    printf '%s\n' "$HOME/rish"
    return 0
  fi
  return 1
}

RISH_BIN="$(find_rish || true)"
if [ -z "$RISH_BIN" ]; then
  cat >&2 <<'EOF'
[GSB-SURVEY] rish not found.
Open Shizuku -> "Use Shizuku in terminal apps", finish rish setup, then either:
  1) put rish in PATH, or
  2) place ./rish beside this script, or
  3) run: GSB_RISH=/absolute/path/to/rish bash tools/redmagic-system-survey.sh trace 60
EOF
  exit 3
fi

remote() {
  "$RISH_BIN" -c "$1"
}

REMOTE_ID="$(remote 'id' 2>/dev/null || true)"
case "$REMOTE_ID" in
  *"uid=2000(shell)"*|*"uid=0(root)"*) ;;
  *)
    echo "[GSB-SURVEY] rish is not running as adb shell/root: $REMOTE_ID" >&2
    echo "[GSB-SURVEY] Start Shizuku first, then retry." >&2
    exit 4
    ;;
esac

STAMP="$(date '+%Y%m%d-%H%M%S')"
OUTDIR="${GSB_SURVEY_OUT:-$PWD/redmagic-survey-$STAMP}"
mkdir -p "$OUTDIR"/{meta,packages,services,settings,hardware,input,trace}

log() {
  printf '[GSB-SURVEY] %s\n' "$*"
}

capture() {
  local rel="$1"
  local cmd="$2"
  mkdir -p "$(dirname "$OUTDIR/$rel")"
  remote "$cmd" >"$OUTDIR/$rel" 2>&1 || true
}

capture_local() {
  local rel="$1"
  shift
  mkdir -p "$(dirname "$OUTDIR/$rel")"
  "$@" >"$OUTDIR/$rel" 2>&1 || true
}

KNOWN_KEYS=(
  gcs_need_kill_game_launcher
  nubia_game_scene
  nubia_game_mode
  cc_game_mis_operate
  virtual_game_key
)

settings_command() {
  local cmd=""
  local key
  for key in "${KNOWN_KEYS[@]}"; do
    cmd+="printf '%s=' '$key'; settings get global '$key' 2>/dev/null || true; "
  done
  printf '%s' "$cmd"
}

log "survey=$SCRIPT_VERSION mode=$MODE output=$OUTDIR"

cat >"$OUTDIR/meta/survey.txt" <<EOF
script_version=$SCRIPT_VERSION
mode=$MODE
duration_seconds=$DURATION
local_started_at=$(date -Iseconds 2>/dev/null || date)
rish=$RISH_BIN
remote_identity=$REMOTE_ID
safety=read-only; no settings writes; no sysfs writes; no package mutations
EOF

log "collecting device and vendor inventory"
capture "meta/device.txt" "printf 'manufacturer='; getprop ro.product.manufacturer; printf 'brand='; getprop ro.product.brand; printf 'model='; getprop ro.product.model; printf 'device='; getprop ro.product.device; printf 'android='; getprop ro.build.version.release; printf 'sdk='; getprop ro.build.version.sdk; printf 'build_id='; getprop ro.build.id; printf 'fingerprint='; getprop ro.build.fingerprint; printf 'security_patch='; getprop ro.build.version.security_patch; uname -a"
capture "meta/relevant-properties.txt" "getprop | grep -Ei 'nubia|redmagic|game|perf|thermal|fan|charge|shoulder|trigger|refresh|touch' | head -n 500"
capture "packages/vendor-packages.txt" "pm list packages -f | grep -Ei 'nubia|redmagic|gamelauncher|gameassist|gamespace'"
capture "packages/vendor-system-packages.txt" "pm list packages -s -f | grep -Ei 'nubia|redmagic|gamelauncher|gameassist|gamespace'"
capture "packages/relevant-features.txt" "pm list features | grep -Ei 'nubia|redmagic|game|fan|shoulder|trigger|performance'"
capture "settings/global-relevant.txt" "settings list global | grep -Ei 'nubia|redmagic|game|gcs_|fan|shoulder|trigger|boost|perf|thermal|charge|bypass|refresh|touch|qos|latency' | head -n 800"
capture "settings/known-keys.txt" "$(settings_command)"
capture "services/service-list.txt" "service list | grep -Ei 'nubia|redmagic|game|assist|perf|boost|power|thermal|fan|charge|trigger|shoulder|qos|network'"
capture "services/dumpsys-list.txt" "dumpsys -l | grep -Ei 'nubia|redmagic|game|assist|perf|boost|power|thermal|fan|charge|trigger|shoulder|qos|network'"
capture "services/processes.txt" "ps -A -o USER,PID,PPID,NAME,ARGS 2>/dev/null | grep -Ei 'nubia|redmagic|gamelauncher|gameassist|systemmgr'"
capture "services/overlays.txt" "cmd overlay list 2>/dev/null | grep -Ei 'nubia|redmagic|game'"
capture "services/hal.txt" "lshal 2>/dev/null | grep -Ei 'nubia|redmagic|game|perf|thermal|fan|power|touch' | head -n 500"
capture "input/getevent-devices.txt" "getevent -pl 2>/dev/null"
capture "hardware/fan.txt" "for p in /sys/kernel/fan/fan_enable /sys/kernel/fan/fan_speed_level; do echo \"--- \\$p\"; ls -lZ \"\\$p\" 2>/dev/null; cat \"\\$p\" 2>/dev/null; done"
capture "hardware/relevant-sysfs-paths.txt" "find /sys/kernel /sys/class -type f 2>/dev/null | grep -Ei 'nubia|redmagic|game|fan|shoulder|trigger|charge|bypass|perf|thermal|touch' | head -n 800"

# Build a dynamic candidate list without assuming REDMAGIC's private stack is stable across ROM versions.
VENDOR_PACKAGES="$(remote "pm list packages | sed 's/^package://' | grep -Ei '^(cn\\.nubia\\.|com\\.nubia\\.|.*redmagic.*)'" 2>/dev/null || true)"
{
  printf '%s\n' \
    cn.nubia.gamelauncher \
    cn.nubia.gameassist \
    cn.nubia.systemmanager \
    cn.nubia.security2
  printf '%s\n' "$VENDOR_PACKAGES" | grep -Ei 'game|assist|system|security|power|perf|thermal|fan|charge|trigger|shoulder|network|boost|service'
} | sed '/^$/d' | sort -u >"$OUTDIR/packages/candidates.txt"

log "dumping candidate package metadata, components, permissions and app-ops"
while IFS= read -r pkg; do
  [ -n "$pkg" ] || continue
  if ! remote "pm path '$pkg'" >/dev/null 2>&1; then
    continue
  fi
  safe="${pkg//[^A-Za-z0-9._-]/_}"
  capture "packages/$safe.path.txt" "pm path '$pkg'"
  capture "packages/$safe.dumpsys.txt" "dumpsys package '$pkg'"
  capture "packages/$safe.appops.txt" "appops get '$pkg' 2>/dev/null"
  capture "services/$safe.running-services.txt" "dumpsys activity services '$pkg' 2>/dev/null"
done <"$OUTDIR/packages/candidates.txt"

capture "packages/relevant-permissions.txt" "pm list permissions -f 2>/dev/null | grep -Ei 'nubia|redmagic|game|assist|boost|perf|thermal|fan|shoulder|trigger|charge|network|qos' | head -n 1000"

if [ "$MODE" = "trace" ]; then
  log "trace mode: preparing read-only launch timeline"
  capture "trace/settings-before.txt" "$(settings_command)"
  capture "trace/activity-before.txt" "dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|topResumedActivity|mFocusedApp|mCurrentFocus' | head -n 80"
  START_EPOCH="$(remote 'date +%s' 2>/dev/null | tr -dc '0-9' | head -c 16)"
  [ -n "$START_EPOCH" ] || START_EPOCH="$(date +%s)"
  printf '%s\n' "$START_EPOCH" >"$OUTDIR/trace/start-epoch.txt"

  echo
  echo "[GSB-SURVEY] ================================================"
  echo "[GSB-SURVEY] For the next $DURATION seconds:"
  echo "[GSB-SURVEY] 1. Leave Termux and launch ONE game already added to REDMAGIC Game Space."
  echo "[GSB-SURVEY] 2. Wait until the game's first interactive screen."
  echo "[GSB-SURVEY] 3. Return to the launcher before the timer ends."
  echo "[GSB-SURVEY] Do not toggle unrelated system settings during this window."
  echo "[GSB-SURVEY] ================================================"
  echo

  SAMPLES=$((DURATION * 2))
  SAMPLE_CMD="i=0; while [ \\$i -lt $SAMPLES ]; do printf '%04d ' \\$i; date +%s 2>/dev/null; $(settings_command) i=\\$((i+1)); sleep 0.5; done"
  remote "$SAMPLE_CMD" >"$OUTDIR/trace/settings-timeline.txt" 2>&1 || true

  capture "trace/settings-after.txt" "$(settings_command)"
  capture "trace/activity-after.txt" "dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|topResumedActivity|mFocusedApp|mCurrentFocus' | head -n 80"
  capture "trace/processes-after.txt" "ps -A -o USER,PID,PPID,NAME,ARGS 2>/dev/null | grep -Ei 'nubia|redmagic|gamelauncher|gameassist|systemmgr|game' | head -n 500"
  capture "trace/running-services-after.txt" "dumpsys activity services 2>/dev/null | grep -Ei -C 2 'nubia|redmagic|gamelauncher|gameassist|systemmgr' | head -n 1200"

  diff -u "$OUTDIR/trace/settings-before.txt" "$OUTDIR/trace/settings-after.txt" >"$OUTDIR/trace/settings-before-after.diff" 2>&1 || true

  awk '
    NR==1 { prev=$0; prevState=$0; sub(/^[^ ]+ [^ ]+ /,"",prevState); print; next }
    {
      state=$0; sub(/^[^ ]+ [^ ]+ /,"",state);
      if (state != prevState) print;
      prevState=state;
    }
  ' "$OUTDIR/trace/settings-timeline.txt" >"$OUTDIR/trace/settings-transitions.txt" 2>/dev/null || true

  # Stream logcat through local filters so the archive never stores an unrelated full log buffer.
  remote "logcat -b main -b system -d -v epoch 2>/dev/null" 2>/dev/null \
    | awk -v start="$START_EPOCH" '($1 + 0) >= start' \
    | grep -Ei 'ActivityTaskManager|ActivityManager|WindowManager|nubia|redmagic|GameSpace|gameassist|gamelauncher|SystemMgr|notifyActivityResumed|nubia_game_|gcs_need_kill_game_launcher|virtual_game_key|cc_game_mis_operate|shoulder|trigger|fan|thermal|boost|performance|qos|latency' \
    >"$OUTDIR/trace/logcat-filtered.txt" || true
fi

log "building evidence summary"
{
  echo "# Game Star Box REDMAGIC survey summary"
  echo
  echo "- survey_version: $SCRIPT_VERSION"
  echo "- mode: $MODE"
  echo "- remote_identity: $REMOTE_ID"
  echo "- generated_at: $(date -Iseconds 2>/dev/null || date)"
  echo "- mutation_policy: read-only"
  echo
  echo "## Known package presence"
  for pkg in cn.nubia.gamelauncher cn.nubia.gameassist cn.nubia.systemmanager cn.nubia.security2; do
    if remote "pm path '$pkg'" >/dev/null 2>&1; then
      echo "- $pkg: present"
    else
      echo "- $pkg: not-observed"
    fi
  done
  echo
  echo "## Known REDMAGIC settings"
  sed 's/^/- /' "$OUTDIR/settings/known-keys.txt" 2>/dev/null || true
  echo
  echo "## Hardware evidence"
  if grep -q '/sys/kernel/fan/fan_enable' "$OUTDIR/hardware/fan.txt" 2>/dev/null; then
    echo "- fan node probe captured; inspect hardware/fan.txt"
  else
    echo "- fan node evidence not observed by this probe"
  fi
  if grep -qi 'nubia_tgk_aw_sar' "$OUTDIR/input/getevent-devices.txt" 2>/dev/null; then
    echo "- shoulder SAR input evidence: nubia_tgk_aw_sar observed"
  else
    echo "- shoulder SAR input evidence: not observed under that device name"
  fi
  if [ "$MODE" = "trace" ]; then
    echo
    echo "## Trace evidence"
    TRANSITIONS="$(wc -l <"$OUTDIR/trace/settings-transitions.txt" 2>/dev/null || echo 0)"
    LOG_LINES="$(wc -l <"$OUTDIR/trace/logcat-filtered.txt" 2>/dev/null || echo 0)"
    echo "- settings transition rows: $TRANSITIONS"
    echo "- filtered framework/vendor log rows: $LOG_LINES"
    echo "- inspect trace/settings-transitions.txt first"
    echo "- inspect trace/logcat-filtered.txt for ActivityTaskManager/SystemMgr ordering"
  fi
  echo
  echo "## Decision rule"
  echo "Do not choose REUSE_REDMAGIC_CHAIN vs OWN_SESSION_CHAIN from package presence alone."
  echo "Require a repeatable launch-edge timeline plus an identified callable/observable system boundary."
} >"$OUTDIR/SUMMARY.md"

ARCHIVE="${OUTDIR}.tar.gz"
if command -v tar >/dev/null 2>&1; then
  tar -C "$(dirname "$OUTDIR")" -czf "$ARCHIVE" "$(basename "$OUTDIR")" 2>/dev/null || true
fi

log "done"
log "summary: $OUTDIR/SUMMARY.md"
if [ -f "$ARCHIVE" ]; then
  log "archive: $ARCHIVE"
fi
