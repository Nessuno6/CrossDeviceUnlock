# Changelog

## v4.0

- Sets the specific Play services flags instead of restoring a saved copy of Play services'
  data, so it works on any device and survives Play services updates.
- Re-applies the flags when a Play services sync removes them.
- flagtool supports named flags and `=false`.
- Updates through KernelSU / Magisk (`update.json`).
- First public release (earlier versions weren't published).
