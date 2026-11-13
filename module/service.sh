#!/system/bin/sh
# After boot: set the flags, then check every 15 minutes and set them again if a Play
# services update or flag sync removed them.
MODDIR=${0%/*}
. "$MODDIR/common.sh"
: > "$LOG"

until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 5; done
sleep 90   # let Play services finish starting and syncing

# Upgrading from the old private versions: drop the DND sync flags they set (once).
if [ -f "$MODDIR/.dnd_cleanup" ]; then
  gms_kill; edit_file "$MD_FILE" "-45748174 -45822670"; gms_kill
  rm -f "$MODDIR/.dnd_cleanup"
fi

while true; do
  if ! has_flags; then
    apply
    sleep 60
    is_unlocked && log "- Cross-Device Services enabled" || log "! not enabled yet; it can take a few minutes"
  fi
  sleep 900
done
