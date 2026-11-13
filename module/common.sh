# Shared by service.sh, action.sh and uninstall.sh. Source with MODDIR set.
#
# Google's Cross-Device Services (notification sync, App Cast, Quick Access, task handoff)
# only run on device types Google allows. Play services reads that from its shared flag
# files (files/phenotype/shared/<group>.pb); these are the flags that allow tablets and
# Samsung DeX and show the feature toggles. flagtool.dex sets them in place (Play services
# looks flags up in sorted order, so appended entries would be ignored).
LOG="$MODDIR/crossdevice.log"
GMS=com.google.android.gms
AMB=com.google.ambient.streaming
SH=/data/data/$GMS/files/phenotype/shared

MD_FILE="$SH/com.google.android.gms.multidevice#com.google.android.gms.pb"
MD_FLAGS="45742359 45742360 45765962 45768040 45771177 45784133 45784134 45824848 45852685 45853337
 45865111 45865112 45865113=false
 MultideviceClientApis__enable_multidevice_sync_setup_flow
 MultideviceSettingsConfig__deduplicate_permission_state_updates_after_cdm_association
 MultideviceSettingsConfig__enable_multidevice_sync_half_sheet_activity
 MultideviceSettingsConfig__enable_multidevice_sync_handoff_subpage
 MultideviceSettingsConfig__is_samsung_desktop_allowed_for_multidevice_sync
 MultideviceSettingsConfig__is_tablet_allowed_for_multidevice_sync
 MultideviceSettingsConfig__should_display_app_cast_toggle
 MultideviceSettingsConfig__should_display_notification_sync_toggle
 MultideviceSettingsConfig__should_display_quick_access_toggle
 MultideviceSettingsConfig__should_display_task_handoff_toggle
 MultideviceSettingsConfig__use_build_characteristics_to_gate_allowed_form_factors=false"
DTDI_FILE="$SH/com.google.android.gms.dtdi#com.google.android.gms.pb"
DTDI_FLAGS="45849072 45869682 45876275"
PROX_FILE="$SH/com.google.android.gms.auth.proximity.pb"
PROX_FLAGS="ProximityAuth__is_better_together_host_supported ProximityAuth__is_sms_sync_feature_allowed"

log() { echo "$(date '+%m-%d %H:%M:%S') $*" >> "$LOG"; echo "$*"; }

flagtool() { CLASSPATH="$MODDIR/flagtool.dex" app_process /system/bin dev.deang.flagtool.Main "$@"; }

is_unlocked() {
  dumpsys activity service $GMS/.dtdi.lifecycle.LifecycleService 2>/dev/null | grep -q 'MultideviceSyncState(ENABLED)'
}

# True when FILE already has FLAGS (checked on a copy: no writes).
file_ok() {
  [ -f "$1" ] || return 1
  T=/data/local/tmp/cdu_check.pb
  cp "$1" $T || return 1
  r=$(flagtool $T $2 2>/dev/null)
  rm -f $T
  [ "$r" = "unchanged" ]
}

has_flags() {
  file_ok "$MD_FILE" "$MD_FLAGS" && file_ok "$DTDI_FILE" "$DTDI_FLAGS" && file_ok "$PROX_FILE" "$PROX_FLAGS"
}

gms_kill() { for p in $(pidof $GMS $GMS.persistent $GMS.unstable $GMS.ui $AMB); do kill -9 $p; done; }

# Runs flagtool on FILE with ARGS and gives the file back to Play services.
edit_file() {
  [ -f "$1" ] || { log "! $(basename "$1") doesn't exist yet (open Settings > Google > All services once)"; return 1; }
  GU=$(stat -c %u /data/data/$GMS)
  r=$(flagtool "$1" $2 2>&1)
  chown $GU:$GU "$1"; chmod 600 "$1"; restorecon "$1" 2>/dev/null
  log "- $(basename "$1" .pb | sed 's/#.*//'): $r"
}

apply() {
  log "- setting the Cross-Device Services flags (stopping Play services briefly)"
  gms_kill
  edit_file "$MD_FILE" "$MD_FLAGS"
  edit_file "$DTDI_FILE" "$DTDI_FLAGS"
  edit_file "$PROX_FILE" "$PROX_FLAGS"
  rm -rf /data/data/$GMS/app_phenotype_shared/* /data/user_de/0/$GMS/app_phenotype_shared/*
  # The Cross-Device Services app's screens and the permissions it asks for in setup.
  for c in multidevicesync.appcast.receiver.launcher.RemoteAppsLauncherActivity \
           multidevicesync.appcast.receiver.RemoteAppListActivity \
           multidevicesync.quickaccess.QuickAccessDocumentsProvider; do
    pm enable $AMB/$AMB.$c >/dev/null 2>&1
  done
  for p in ACCESS_LOCAL_NETWORK BLUETOOTH_ADVERTISE BLUETOOTH_CONNECT BLUETOOTH_SCAN \
           GET_ACCOUNTS NEARBY_WIFI_DEVICES POST_NOTIFICATIONS READ_CONTACTS \
           READ_PHONE_STATE UWB_RANGING; do
    pm grant $AMB android.permission.$p >/dev/null 2>&1
  done
  gms_kill
}

# Removes every flag this module sets (Play services' own values come back on its next sync).
unset_flags() {
  gms_kill
  for pair in "$MD_FILE|$MD_FLAGS" "$DTDI_FILE|$DTDI_FLAGS" "$PROX_FILE|$PROX_FLAGS"; do
    f=${pair%%|*}; args=""
    for a in ${pair#*|}; do args="$args -${a%%=*}"; done
    [ -f "$f" ] && edit_file "$f" "$args"
  done
  rm -rf /data/data/$GMS/app_phenotype_shared/* /data/user_de/0/$GMS/app_phenotype_shared/*
  gms_kill
}
