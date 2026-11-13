package dev.deang.flagtool;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Sets or removes flags in a Play services shared flag file
 * (files/phenotype/shared/&lt;group&gt;.pb), keeping its order: Play services looks flags up
 * by position, so entries tacked on at the end are ignored.
 *
 * <pre>
 * CLASSPATH=flagtool.dex app_process /system/bin dev.deang.flagtool.Main FILE ARG...
 *   NAME          set boolean NAME to true (NAME is a flag name or a numeric flag ID)
 *   NAME=true     same
 *   NAME=false    set boolean NAME to false
 *   NAME=123      set long NAME to 123
 *   -NAME         remove NAME
 * </pre>
 * Prints "unchanged" or "updated"; exits 1 on an unreadable file (and leaves it untouched).
 *
 * File layout (protobuf): repeated field 5 = flag { 1: name, 3: bool | 2: long | ... }.
 * Named flags come first in name order, then numeric IDs in numeric order.
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: Main FILE NAME[=true|false|N]... [-NAME...]");
            System.exit(2);
        }
        File f = new File(args[0]);
        byte[] d = Files.readAllBytes(f.toPath());

        List<byte[]> fields = new ArrayList<>();
        List<Integer> numbers = new ArrayList<>();
        try {
            int i = 0;
            while (i < d.length) {
                int start = i;
                long[] r = varint(d, i);
                int field = (int) (r[0] >>> 3), type = (int) (r[0] & 7);
                i = skip(d, (int) r[1], type);
                if (i > d.length) throw new IllegalStateException("truncated");
                fields.add(slice(d, start, i));
                numbers.add(field);
            }
        } catch (RuntimeException e) {
            System.err.println("unreadable flag file: " + e);
            System.exit(1);
        }

        // Wanted values: Boolean or Long, by flag name. Names to remove.
        Map<String, Object> want = new LinkedHashMap<>();
        Set<String> drop = new HashSet<>();
        for (int a = 1; a < args.length; a++) {
            String arg = args[a];
            if (arg.startsWith("-")) { drop.add(arg.substring(1)); continue; }
            int eq = arg.indexOf('=');
            if (eq < 0) { want.put(arg, Boolean.TRUE); continue; }
            String name = arg.substring(0, eq), v = arg.substring(eq + 1);
            if (v.equals("true") || v.equals("false")) want.put(name, Boolean.valueOf(v));
            else want.put(name, Long.parseLong(v));
        }

        boolean already = true;
        for (Map.Entry<String, Object> w : want.entrySet()) {
            already &= w.getValue().equals(currentValue(fields, numbers, w.getKey()));
        }
        for (int k = 0; k < fields.size(); k++) {
            if (numbers.get(k) == 5 && drop.contains(flagName(fields.get(k)))) already = false;
        }
        if (already) {
            System.out.println("unchanged");
            return;
        }

        // Pending inserts, in file order: named flags by name, numeric ones by number.
        TreeMap<String, Object> pendingNamed = new TreeMap<>();
        TreeMap<Long, String> pendingIds = new TreeMap<>();
        for (String name : want.keySet()) {
            if (isNumber(name)) pendingIds.put(Long.parseLong(name), name);
            else pendingNamed.put(name, want.get(name));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int k = 0; k < fields.size(); k++) {
            byte[] raw = fields.get(k);
            int field = numbers.get(k);
            if (field == 5) {
                String name = flagName(raw);
                if (want.containsKey(name) || drop.contains(name)) continue; // rewritten / removed
                if (isNumber(name)) {
                    flushNamed(out, pendingNamed, null, want);
                    long id = Long.parseLong(name);
                    while (!pendingIds.isEmpty() && pendingIds.firstKey() < id) {
                        String n = pendingIds.pollFirstEntry().getValue();
                        out.write(flag(n, want.get(n)));
                    }
                } else {
                    flushNamed(out, pendingNamed, name, want);
                }
            } else if (field > 5) {
                flushNamed(out, pendingNamed, null, want);
                while (!pendingIds.isEmpty()) {
                    String n = pendingIds.pollFirstEntry().getValue();
                    out.write(flag(n, want.get(n)));
                }
            }
            out.write(raw);
        }
        flushNamed(out, pendingNamed, null, want);
        while (!pendingIds.isEmpty()) {
            String n = pendingIds.pollFirstEntry().getValue();
            out.write(flag(n, want.get(n)));
        }

        File tmp = new File(f.getPath() + ".flagtool");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            o.write(out.toByteArray());
            o.getFD().sync();
        }
        if (!tmp.renameTo(f)) throw new IllegalStateException("rename failed");
        System.out.println("updated");
    }

    /** Writes pending named flags that sort before {@code before} (all of them if null). */
    private static void flushNamed(ByteArrayOutputStream out, TreeMap<String, Object> pending, String before,
                                   Map<String, Object> want) throws Exception {
        while (!pending.isEmpty() && (before == null || pending.firstKey().compareTo(before) < 0)) {
            String n = pending.pollFirstEntry().getKey();
            out.write(flag(n, want.get(n)));
        }
    }

    /** The flag's value as Boolean or Long, or null if absent (or another type). */
    private static Object currentValue(List<byte[]> fields, List<Integer> numbers, String name) {
        for (int k = 0; k < fields.size(); k++) {
            if (numbers.get(k) != 5 || !name.equals(flagName(fields.get(k)))) continue;
            byte[] body = body(fields.get(k));
            int j = 0;
            while (j < body.length) {
                long[] r = varint(body, j);
                int field = (int) (r[0] >>> 3), type = (int) (r[0] & 7);
                j = (int) r[1];
                if (type == 0) {
                    long[] v = varint(body, j);
                    if (field == 3) return v[0] != 0;
                    if (field == 2) return v[0];
                    j = (int) v[1];
                } else {
                    j = skip(body, j, type);
                }
            }
            return null;
        }
        return null;
    }

    /** Flag entry: { 1: name, 3: bool } or { 1: name, 2: long }. */
    private static byte[] flag(String name, Object value) {
        byte[] n = name.getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(0x0a);
        writeVarint(b, n.length);
        b.write(n, 0, n.length);
        if (value instanceof Boolean) {
            b.write(0x18);
            b.write((Boolean) value ? 1 : 0);
        } else {
            b.write(0x10);
            writeVarint(b, (Long) value);
        }
        byte[] body = b.toByteArray();
        ByteArrayOutputStream e = new ByteArrayOutputStream();
        e.write(0x2a);
        writeVarint(e, body.length);
        e.write(body, 0, body.length);
        return e.toByteArray();
    }

    private static int skip(byte[] b, int i, int type) {
        switch (type) {
            case 0: return (int) varint(b, i)[1];
            case 1: return i + 8;
            case 2: { long[] l = varint(b, i); return (int) l[1] + (int) l[0]; }
            case 5: return i + 4;
            default: throw new IllegalStateException("wire type " + type);
        }
    }

    private static byte[] body(byte[] raw) {
        long[] k = varint(raw, 0);
        long[] l = varint(raw, (int) k[1]);
        return slice(raw, (int) l[1], (int) l[1] + (int) l[0]);
    }

    private static String flagName(byte[] raw) {
        byte[] b = body(raw);
        long[] k = varint(b, 0);
        if ((k[0] >>> 3) != 1 || (k[0] & 7) != 2) return "";
        long[] l = varint(b, (int) k[1]);
        return new String(b, (int) l[1], (int) l[0], StandardCharsets.US_ASCII);
    }

    private static boolean isNumber(String s) {
        if (s.isEmpty() || s.length() > 18) return false;
        for (int c = 0; c < s.length(); c++) if (!Character.isDigit(s.charAt(c))) return false;
        return true;
    }

    /** Returns {value, next index}. */
    private static long[] varint(byte[] b, int i) {
        long r = 0;
        int s = 0;
        while (true) {
            if (i >= b.length || s > 63) throw new IllegalStateException("bad varint");
            int x = b[i++] & 0xff;
            r |= (long) (x & 0x7f) << s;
            s += 7;
            if (x < 0x80) return new long[]{r, i};
        }
    }

    private static void writeVarint(ByteArrayOutputStream o, long n) {
        while (true) {
            int x = (int) (n & 0x7f);
            n >>>= 7;
            if (n != 0) {
                o.write(x | 0x80);
            } else {
                o.write(x);
                return;
            }
        }
    }

    private static byte[] slice(byte[] b, int from, int to) {
        byte[] o = new byte[to - from];
        System.arraycopy(b, from, o, 0, o.length);
        return o;
    }
}
