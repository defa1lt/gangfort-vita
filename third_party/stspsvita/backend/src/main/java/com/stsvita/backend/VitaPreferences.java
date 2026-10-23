package com.stsvita.backend;

import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.GdxRuntimeException;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;

/** Typed snapshots; one committed generation remains intact during every write. */
public class VitaPreferences implements Preferences {

    private final String name;
    private final Map<String, Object> values = new HashMap<>();
    private final File[] slots;
    private final File pending;
    private int active = -1;
    private long generation;
    private static final int MAX_BYTES = 16 * 1024 * 1024;

    public VitaPreferences(String name) {
        this(name, new File(VitaFiles.DATA, "preferences"));
    }

    public VitaPreferences(String name, File directory) {
        if (name == null) throw new IllegalArgumentException("Preference name is null");
        this.name = name;
        StringBuilder encoded = new StringBuilder("prefs-");
        for (int i = 0; i < name.length(); i++) {
            String hex = Integer.toHexString(name.charAt(i));
            for (int pad = hex.length(); pad < 4; pad++) encoded.append('0');
            encoded.append(hex);
        }
        slots = new File[] {new File(directory, encoded + ".0"), new File(directory, encoded + ".1")};
        pending = new File(directory, encoded + ".pending");
        IOException failure = null;
        for (int i = 0; i < slots.length; i++) {
            if (!slots[i].exists()) continue;
            try {
                Snapshot snapshot = readSnapshot(slots[i]);
                if (active < 0 || snapshot.generation > generation) {
                    active = i;
                    generation = snapshot.generation;
                    values.clear();
                    values.putAll(snapshot.values);
                }
            } catch (IOException error) {
                failure = error;
                System.err.println("[preferences] cannot read " + slots[i] + ": " + error);
            }
        }
        if (active < 0 && failure != null) {
            throw new GdxRuntimeException("No valid preference snapshot for " + name, failure);
        }
    }

    @Override
    public Preferences putBoolean(String key, boolean val) {
        values.put(key, val);
        return this;
    }

    @Override
    public Preferences putInteger(String key, int val) {
        values.put(key, val);
        return this;
    }

    @Override
    public Preferences putLong(String key, long val) {
        values.put(key, val);
        return this;
    }

    @Override
    public Preferences putFloat(String key, float val) {
        values.put(key, val);
        return this;
    }

    @Override
    public Preferences putString(String key, String val) {
        values.put(key, val);
        return this;
    }

    @Override
    public Preferences put(Map<String, ?> vals) {
        for (Map.Entry<String, ?> entry : vals.entrySet()) validate(entry.getKey(), entry.getValue());
        values.putAll(vals);
        return this;
    }

    @Override
    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    @Override
    public int getInteger(String key) {
        return getInteger(key, 0);
    }

    @Override
    public long getLong(String key) {
        return getLong(key, 0L);
    }

    @Override
    public float getFloat(String key) {
        return getFloat(key, 0f);
    }

    @Override
    public String getString(String key) {
        return getString(key, "");
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        Object v = values.get(key);
        return v == null ? defValue : (Boolean) v;
    }

    @Override
    public int getInteger(String key, int defValue) {
        Object v = values.get(key);
        return v == null ? defValue : (Integer) v;
    }

    @Override
    public long getLong(String key, long defValue) {
        Object v = values.get(key);
        return v == null ? defValue : (Long) v;
    }

    @Override
    public float getFloat(String key, float defValue) {
        Object v = values.get(key);
        return v == null ? defValue : (Float) v;
    }

    @Override
    public String getString(String key, String defValue) {
        Object v = values.get(key);
        return v == null ? defValue : (String) v;
    }

    @Override
    public Map<String, ?> get() {
        return new HashMap<>(values);
    }

    @Override
    public boolean contains(String key) {
        return values.containsKey(key);
    }

    @Override
    public void clear() {
        values.clear();
    }

    @Override
    public void remove(String key) {
        values.remove(key);
    }

    @Override
    public void flush() {
        try {
            File directory = pending.getParentFile();
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create " + directory);
            if (generation == Long.MAX_VALUE) throw new IOException("Preference generation overflow");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(buffer);
            data.writeInt(0x47465031);
            data.writeLong(generation + 1);
            data.writeInt(values.size());
            for (Map.Entry<String, Object> entry : new TreeMap<>(values).entrySet()) {
                Object value = entry.getValue();
                validate(entry.getKey(), value);
                writeString(data, entry.getKey());
                if (value instanceof Boolean) { data.writeByte(1); data.writeBoolean((Boolean) value); }
                else if (value instanceof Integer) { data.writeByte(2); data.writeInt((Integer) value); }
                else if (value instanceof Long) { data.writeByte(3); data.writeLong((Long) value); }
                else if (value instanceof Float) { data.writeByte(4); data.writeFloat((Float) value); }
                else { data.writeByte(5); writeString(data, (String) value); }
            }
            data.flush();
            byte[] payload = buffer.toByteArray();
            if (payload.length > MAX_BYTES - 8) throw new IOException("Preferences exceed 16 MiB");
            CRC32 crc = new CRC32();
            crc.update(payload);
            try (DataOutputStream out = new DataOutputStream(new FileOutputStream(pending))) {
                out.write(payload);
                out.writeLong(crc.getValue());
                out.flush();
            }
            Snapshot verified = readSnapshot(pending);
            if (verified.generation != generation + 1 || !verified.values.equals(values)) {
                throw new IOException("Preference write verification failed");
            }
            int next = active == 0 ? 1 : 0;
            // Vita rename need not replace an existing destination. Only retire the older slot.
            if (slots[next].exists() && !slots[next].delete()) throw new IOException("Cannot retire " + slots[next]);
            if (!pending.renameTo(slots[next])) throw new IOException("Cannot commit " + slots[next]);
            active = next;
            generation++;
        } catch (IOException error) {
            throw new GdxRuntimeException("Cannot save preferences " + name, error);
        }
    }

    private static void validate(String key, Object value) {
        if (key == null || !(value instanceof Boolean || value instanceof Integer
                || value instanceof Long || value instanceof Float || value instanceof String)) {
            throw new IllegalArgumentException("Invalid preference value for " + key);
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes("UTF-8");
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > in.available()) throw new IOException("Invalid preference string length");
        byte[] bytes = new byte[count];
        in.readFully(bytes);
        return new String(bytes, "UTF-8");
    }

    private static Snapshot readSnapshot(File file) throws IOException {
        long size = file.length();
        if (size < 24 || size > MAX_BYTES) throw new IOException("Invalid snapshot size: " + size);
        byte[] payload = new byte[(int) size - 8];
        try (DataInputStream input = new DataInputStream(new FileInputStream(file))) {
            input.readFully(payload);
            CRC32 crc = new CRC32();
            crc.update(payload);
            if (input.readLong() != crc.getValue() || input.read() != -1) throw new IOException("Snapshot checksum mismatch");
        }
        DataInputStream data = new DataInputStream(new ByteArrayInputStream(payload));
        if (data.readInt() != 0x47465031) throw new IOException("Unknown preferences format");
        Snapshot snapshot = new Snapshot();
        snapshot.generation = data.readLong();
        int count = data.readInt();
        if (snapshot.generation < 1 || count < 0 || count > payload.length / 5) throw new IOException("Invalid preferences header");
        for (int i = 0; i < count; i++) {
            String key = readString(data);
            Object value;
            switch (data.readUnsignedByte()) {
                case 1: value = data.readBoolean(); break;
                case 2: value = data.readInt(); break;
                case 3: value = data.readLong(); break;
                case 4: value = data.readFloat(); break;
                case 5: value = readString(data); break;
                default: throw new IOException("Unknown preference type");
            }
            if (snapshot.values.put(key, value) != null) throw new IOException("Duplicate preference key");
        }
        if (data.read() != -1) throw new IOException("Trailing preference data");
        return snapshot;
    }

    private static final class Snapshot {
        long generation;
        final Map<String, Object> values = new HashMap<>();
    }
}
