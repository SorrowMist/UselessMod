package com.sorrowmist.useless.compat.forge1201.jei;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.compat.forge1201.recipe.NbtCanonical;
import net.minecraft.nbt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** JEI 15 restores non-item bookmarks by UID lookup, not by decoding arbitrary NBT.
 * Retain keys seen by the fallback ingredient before JEI reads bookmarks next time.
 * Unknown addon keys remain opaque on disk, allowing the addon to be restored later.
 */
public final class AEKeyBookmarkCatalog {
    private final Map<String, CompoundTag> keys = new LinkedHashMap<>();
    public static String uid(AEKey key) { return uid(key.toTagGeneric()); }
    private static String uid(CompoundTag tag) {
        // JEI uses '&' as an unescaped separator. Base64URL also avoids newlines and
        // distinguishes full NBT and key types without relying on Java hashCode.
        return "useless_mod:ae_key:" + Base64.getUrlEncoder().withoutPadding().encodeToString(
                NbtCanonical.snbt(tag).getBytes(StandardCharsets.UTF_8));
    }
    public boolean remember(AEKey key) {
        var tag = key.toTagGeneric();
        return keys.putIfAbsent(uid(tag), tag.copy()) == null;
    }
    public int size() { return keys.size(); }
    public List<GenericStack> decoded() {
        var result = new ArrayList<GenericStack>();
        for (var tag : keys.values()) {
            var key = AEKey.fromTagGeneric(tag.copy());
            if (key != null) result.add(new GenericStack(key, 1));
        }
        return List.copyOf(result);
    }
    public void load(Path file) throws IOException {
        if (!Files.exists(file)) { keys.clear(); return; }
        CompoundTag root;
        try (var in = Files.newInputStream(file)) { root = NbtIo.readCompressed(in); }
        if (!root.contains("keys", Tag.TAG_LIST)) throw new IOException("Invalid AE key bookmark catalogue");
        var loaded = new LinkedHashMap<String, CompoundTag>();
        for (var entry : root.getList("keys", Tag.TAG_COMPOUND)) {
            var tag = (CompoundTag) entry;
            if (!tag.isEmpty()) loaded.put(uid(tag), tag.copy());
        }
        keys.clear(); keys.putAll(loaded);
    }
    public void save(Path file) throws IOException {
        file = file.toAbsolutePath();
        Files.createDirectories(file.getParent());
        var root = new CompoundTag(); var list = new ListTag();
        keys.values().forEach(tag -> list.add(tag.copy()));
        root.putInt("version", 1); root.put("keys", list);
        Path temp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        try {
            try (var out = Files.newOutputStream(temp)) { NbtIo.writeCompressed(root, out); }
            var backup = file.resolveSibling(file.getFileName() + ".bak");
            if (Files.exists(file) && !Files.exists(backup)) Files.copy(file, backup);
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
    }
}
