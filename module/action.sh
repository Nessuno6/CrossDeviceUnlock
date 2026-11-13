#!/system/bin/sh
# KernelSU "Action" button: set the flags now and show the result.
MODDIR=${0%/*}
. "$MODDIR/common.sh"
if has_flags; then echo "- flags already set"; else apply; fi
echo "- waiting for Play services..."
sleep 30
is_unlocked && echo "- Cross-Device Services: enabled" || echo "! Cross-Device Services: not enabled yet, check again in a minute"
echo "- Settings > Google > All services > Cross-device services"
