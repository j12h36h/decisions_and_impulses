package io.github.j12h36h.dai.client.play;

import io.github.j12h36h.dai.logics.core.DAI_Core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Small level.dat editor used by DAI's out-of-world Modify screen.
 *
 * <p>This intentionally reads/writes standard NBT itself instead of booting a
 * second integrated server just to change metadata. Unknown tags are preserved
 * byte-for-byte by type/value, including nested lists/compounds and arrays.</p>
 */
final class DAI_LevelDatEditor {

    private static final int TAG_END = 0;
    private static final int TAG_BYTE = 1;
    private static final int TAG_SHORT = 2;
    private static final int TAG_INT = 3;
    private static final int TAG_LONG = 4;
    private static final int TAG_FLOAT = 5;
    private static final int TAG_DOUBLE = 6;
    private static final int TAG_BYTE_ARRAY = 7;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;
    private static final int TAG_INT_ARRAY = 11;
    private static final int TAG_LONG_ARRAY = 12;

    private static final int MAX_DEPTH = 128;
    private static final int MAX_COLLECTION_LENGTH = 16 * 1024 * 1024;

    private DAI_LevelDatEditor() {}

    static synchronized String levelName(Path saveRoot, String fallback) {
        Root root = read(saveRoot);
        Node data = child(root, "Data", TAG_COMPOUND);
        Node name = child(data, "LevelName", TAG_STRING);
        if (name != null && name.value instanceof String value && !value.isBlank()) return value;
        return fallback == null ? "" : fallback;
    }

    static synchronized Map<String, String> gameRules(Path saveRoot) {
        Root root = read(saveRoot);
        Node data = child(root, "Data", TAG_COMPOUND);
        Node rules = child(data, "GameRules", TAG_COMPOUND);
        if (rules == null) return Map.of();

        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Node> entry : compound(rules).entrySet()) {
            Node node = entry.getValue();
            if (node != null && node.type == TAG_STRING && node.value instanceof String value) {
                result.put(entry.getKey(), value);
            }
        }
        return Map.copyOf(result);
    }

    static synchronized boolean rename(Path saveRoot, String newName) {
        String safe = newName == null ? "" : newName.trim();
        if (safe.isBlank()) return false;
        Root root = read(saveRoot);
        Node data = child(root, "Data", TAG_COMPOUND);
        if (root == null || data == null) return false;
        compound(data).put("LevelName", new Node(TAG_STRING, safe));
        return write(saveRoot, root);
    }

    static synchronized boolean setGameRule(Path saveRoot, String rule, String value) {
        String key = rule == null ? "" : rule.trim();
        String safeValue = value == null ? "" : value.trim();
        if (key.isBlank() || safeValue.isBlank()) return false;

        Root root = read(saveRoot);
        Node data = child(root, "Data", TAG_COMPOUND);
        if (root == null || data == null) return false;

        Map<String, Node> dataMap = compound(data);
        Node rules = dataMap.get("GameRules");
        if (rules == null || rules.type != TAG_COMPOUND) {
            rules = new Node(TAG_COMPOUND, new LinkedHashMap<String, Node>());
            dataMap.put("GameRules", rules);
        }
        compound(rules).put(key, new Node(TAG_STRING, safeValue));
        return write(saveRoot, root);
    }

    private static Root read(Path saveRoot) {
        if (saveRoot == null) return null;
        Path file = saveRoot.resolve("level.dat");
        if (!Files.isRegularFile(file)) return null;

        try (InputStream raw = new BufferedInputStream(Files.newInputStream(file));
             DataInputStream in = new DataInputStream(new GZIPInputStream(raw))) {
            int type = in.readUnsignedByte();
            if (type != TAG_COMPOUND) throw new IOException("Root NBT tag is not a compound: " + type);
            String name = in.readUTF();
            Node value = readPayload(in, type, 0);
            return new Root(name, value);
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Could not read level.dat for world metadata editing at '{}'.", file, exception);
            return null;
        }
    }

    private static boolean write(Path saveRoot, Root root) {
        if (saveRoot == null || root == null || root.value == null) return false;
        Path file = saveRoot.resolve("level.dat");
        Path daiDir = saveRoot.resolve("dai");
        Path backup = daiDir.resolve("level.dat.modify.bak");
        Path temporary = saveRoot.resolve("level.dat.dai.tmp");

        try {
            Files.createDirectories(daiDir);
            if (Files.isRegularFile(file)) {
                Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }

            try (OutputStream raw = new BufferedOutputStream(Files.newOutputStream(temporary));
                 DataOutputStream out = new DataOutputStream(new GZIPOutputStream(raw))) {
                out.writeByte(root.value.type);
                out.writeUTF(root.name == null ? "" : root.name);
                writePayload(out, root.value, 0);
            }

            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception exception) {
            try { Files.deleteIfExists(temporary); } catch (Exception ignored) { }
            DAI_Core.LOGGER.error("<DAI>: Could not write modified world metadata to '{}'.", file, exception);
            return false;
        }
    }

    private static Node readPayload(DataInputStream in, int type, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("NBT nesting exceeds " + MAX_DEPTH);
        return switch (type) {
            case TAG_BYTE -> new Node(type, in.readByte());
            case TAG_SHORT -> new Node(type, in.readShort());
            case TAG_INT -> new Node(type, in.readInt());
            case TAG_LONG -> new Node(type, in.readLong());
            case TAG_FLOAT -> new Node(type, in.readFloat());
            case TAG_DOUBLE -> new Node(type, in.readDouble());
            case TAG_BYTE_ARRAY -> {
                int length = checkedLength(in.readInt());
                byte[] values = new byte[length];
                in.readFully(values);
                yield new Node(type, values);
            }
            case TAG_STRING -> new Node(type, in.readUTF());
            case TAG_LIST -> {
                int elementType = in.readUnsignedByte();
                int length = checkedLength(in.readInt());
                ArrayList<Node> values = new ArrayList<>(Math.min(length, 4096));
                for (int i = 0; i < length; i++) values.add(readPayload(in, elementType, depth + 1));
                yield new Node(type, new ListValue(elementType, values));
            }
            case TAG_COMPOUND -> {
                LinkedHashMap<String, Node> values = new LinkedHashMap<>();
                while (true) {
                    int childType = in.readUnsignedByte();
                    if (childType == TAG_END) break;
                    String name = in.readUTF();
                    values.put(name, readPayload(in, childType, depth + 1));
                }
                yield new Node(type, values);
            }
            case TAG_INT_ARRAY -> {
                int length = checkedLength(in.readInt());
                int[] values = new int[length];
                for (int i = 0; i < length; i++) values[i] = in.readInt();
                yield new Node(type, values);
            }
            case TAG_LONG_ARRAY -> {
                int length = checkedLength(in.readInt());
                long[] values = new long[length];
                for (int i = 0; i < length; i++) values[i] = in.readLong();
                yield new Node(type, values);
            }
            default -> throw new IOException("Unsupported NBT tag type " + type);
        };
    }

    private static void writePayload(DataOutputStream out, Node node, int depth) throws IOException {
        if (node == null) throw new IOException("Cannot write null NBT node");
        if (depth > MAX_DEPTH) throw new IOException("NBT nesting exceeds " + MAX_DEPTH);

        switch (node.type) {
            case TAG_BYTE -> out.writeByte((Byte) node.value);
            case TAG_SHORT -> out.writeShort((Short) node.value);
            case TAG_INT -> out.writeInt((Integer) node.value);
            case TAG_LONG -> out.writeLong((Long) node.value);
            case TAG_FLOAT -> out.writeFloat((Float) node.value);
            case TAG_DOUBLE -> out.writeDouble((Double) node.value);
            case TAG_BYTE_ARRAY -> {
                byte[] values = (byte[]) node.value;
                out.writeInt(values.length);
                out.write(values);
            }
            case TAG_STRING -> out.writeUTF((String) node.value);
            case TAG_LIST -> {
                ListValue list = (ListValue) node.value;
                out.writeByte(list.elementType);
                out.writeInt(list.values.size());
                for (Node value : list.values) writePayload(out, value, depth + 1);
            }
            case TAG_COMPOUND -> {
                for (Map.Entry<String, Node> entry : compound(node).entrySet()) {
                    Node value = entry.getValue();
                    if (value == null || value.type == TAG_END) continue;
                    out.writeByte(value.type);
                    out.writeUTF(entry.getKey());
                    writePayload(out, value, depth + 1);
                }
                out.writeByte(TAG_END);
            }
            case TAG_INT_ARRAY -> {
                int[] values = (int[]) node.value;
                out.writeInt(values.length);
                for (int value : values) out.writeInt(value);
            }
            case TAG_LONG_ARRAY -> {
                long[] values = (long[]) node.value;
                out.writeInt(values.length);
                for (long value : values) out.writeLong(value);
            }
            default -> throw new IOException("Unsupported NBT tag type " + node.type);
        }
    }

    private static int checkedLength(int length) throws IOException {
        if (length < 0 || length > MAX_COLLECTION_LENGTH) {
            throw new IOException("Invalid NBT collection length " + length);
        }
        return length;
    }

    private static Node child(Root root, String name, int type) {
        return root == null ? null : child(root.value, name, type);
    }

    private static Node child(Node parent, String name, int type) {
        if (parent == null || parent.type != TAG_COMPOUND) return null;
        Node value = compound(parent).get(name);
        return value != null && value.type == type ? value : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Node> compound(Node node) {
        return (Map<String, Node>) node.value;
    }

    private record Root(String name, Node value) {}
    private record Node(int type, Object value) {}
    private record ListValue(int elementType, List<Node> values) {}
}
