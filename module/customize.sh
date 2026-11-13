ui_print " "
ui_print "*********************************"
ui_print "  Cross-Device Services unlock"
ui_print "*********************************"
pm path com.google.android.gms >/dev/null 2>&1 || abort "! Google Play services isn't installed"
# Upgrading from the old private versions (v1-v3): remove their DND sync flags once.
OLD=/data/adb/modules/crossdevice_unlock/module.prop
if [ -f "$OLD" ] && [ "$(grep -m1 '^versionCode=' "$OLD" | cut -d= -f2)" -lt 100 ] 2>/dev/null; then
  touch "$MODPATH/.dnd_cleanup"
fi
set_perm_recursive "$MODPATH" 0 0 0755 0644
for s in service.sh action.sh uninstall.sh; do set_perm "$MODPATH/$s" 0 0 0755; done
ui_print "- Applies after the next reboot, or press Action in KernelSU now"
ui_print "- Then: Settings > Google > All services > Cross-device services"
