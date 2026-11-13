# Cross-Device Services unlock

A KernelSU / Magisk module that turns on Google's **Cross-device services** (notification
sync, App Cast, Quick Access, task handoff) on Android devices Google doesn't enable them for,
such as Samsung Galaxy tablets. Only Play services' app data is changed; nothing on
`/system` is written.

## How it works

Play services decides which device types may use Cross-device services from its own flags,
kept in shared flag files (`files/phenotype/shared/<group>.pb`). The module sets the flags
that allow tablets and Samsung DeX, use those allowlists instead of build characteristics,
and show the feature toggles:

- `com.google.android.gms.multidevice`: `MultideviceSettingsConfig__is_tablet_allowed_for_multidevice_sync`,
  `…is_samsung_desktop_allowed_for_multidevice_sync`, the `…should_display_*_toggle` flags,
  `…use_build_characteristics_to_gate_allowed_form_factors=false`, and related numeric flags
- `com.google.android.gms.dtdi`: three numeric flags
- `com.google.android.gms.auth.proximity`: `ProximityAuth__is_better_together_host_supported`,
  `ProximityAuth__is_sms_sync_feature_allowed`

The full list is in [`module/common.sh`](module/common.sh). It also enables the Cross-Device
Services app's App Cast and Quick Access components and grants the permissions it asks for
during setup.

Play services rewrites these files when it syncs its configuration, so the module checks
every 15 minutes and sets the flags again when they're gone (briefly restarting Play
services). Uninstalling removes them.

## Install

Needs root (KernelSU or Magisk) and Google Play services.

1. Download `CrossDeviceUnlock_vX.Y.zip` from the
   [latest release](https://github.com/Nessuno6/CrossDeviceUnlock/releases/latest), install it
   in KernelSU or Magisk, and reboot (or press the module's **Action** button in KernelSU to
   apply it straight away).
2. Open Settings > Google > All services > **Cross-device services** and set it up with the
   same Google account on your devices.

The flag IDs belong to the Play services version the module was made with; a future Play
services update may rename or stop using them.

## Updates

`module.prop` points KernelSU / Magisk to [`update.json`](update.json), so the manager offers
new versions in its module list.

## Build

```bash
bash release.sh            # flagtool.dex + dist/CrossDeviceUnlock_vX.Y.zip + update.json
bash release.sh --publish  # also creates the GitHub release; then commit update.json
```

The version comes from the first `## vX.Y` heading in [CHANGELOG.md](CHANGELOG.md).
`flagtool` needs a JDK and the Android SDK's build-tools 36.0.0 (`ANDROID_HOME` /
`JAVA_HOME`, or Android Studio's default locations).

## flagtool

`flagtool/` is a small Java program run on the device with `app_process`. It sets or removes
flags in a Play services flag file, inserting them in sorted position, because Play services
ignores entries appended at the end.

```text
CLASSPATH=flagtool.dex app_process /system/bin dev.deang.flagtool.Main FILE ARG...
  NAME | NAME=true | NAME=false | NAME=123 | -NAME
```

## License

[MIT](LICENSE).
