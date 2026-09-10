package com.sorrowmist.useless.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The player-owned layout of the beef tool mode screen. */
public final class BeefToolLayout {
    public static final int FORMAT_VERSION = 1;
    public static final int MAX_PAGES = 32;
    public static final int MAX_GROUPS_PER_PAGE = 16;
    public static final int MAX_MODULES_PER_GROUP = 32;
    public static final int MAX_TOTAL_MODULES = 128;
    public static final int MAX_NAME_LENGTH = 32;
    public static final int MAX_TEXT_LENGTH = 32 * 1024;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private int selectedPage;
    private final List<Page> pages;
    private final List<String> unassignedModules;

    public BeefToolLayout(int selectedPage, List<Page> pages, List<String> unassignedModules) {
        this.selectedPage = selectedPage;
        this.pages = new ArrayList<>();
        if (pages != null) {
            for (Page page : pages) {
                this.pages.add(page.copy());
            }
        }
        this.unassignedModules = unassignedModules == null
                ? new ArrayList<>()
                : new ArrayList<>(unassignedModules);
    }

    public int selectedPage() {
        return selectedPage;
    }

    public void setSelectedPage(int selectedPage) {
        this.selectedPage = selectedPage;
    }

    public List<Page> pages() {
        return pages;
    }

    public List<String> unassignedModules() {
        return unassignedModules;
    }

    public BeefToolLayout copy() {
        return new BeefToolLayout(selectedPage, pages, unassignedModules);
    }

    public boolean containsModule(String moduleId) {
        if (unassignedModules.contains(moduleId)) {
            return true;
        }
        for (Page page : pages) {
            for (Group group : page.groups()) {
                if (group.modules().contains(moduleId)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void validate() throws LayoutException {
        if (pages.isEmpty()) {
            throw new LayoutException(Error.INVALID_STRUCTURE);
        }
        if (pages.size() > MAX_PAGES) {
            throw new LayoutException(Error.LIMIT);
        }
        if (selectedPage < 0) {
            throw new LayoutException(Error.INVALID_STRUCTURE);
        }

        Set<String> seenModules = new HashSet<>();
        int moduleCount = 0;
        for (Page page : pages) {
            validateName(page.name());
            if (page.groups().size() > MAX_GROUPS_PER_PAGE) {
                throw new LayoutException(Error.LIMIT);
            }
            for (Group group : page.groups()) {
                validateName(group.name());
                if (group.modules().size() > MAX_MODULES_PER_GROUP) {
                    throw new LayoutException(Error.LIMIT);
                }
                for (String module : group.modules()) {
                    validateModuleId(module);
                    if (!seenModules.add(module)) {
                        throw new LayoutException(Error.DUPLICATE_MODULE);
                    }
                    moduleCount++;
                }
            }
        }
        for (String module : unassignedModules) {
            validateModuleId(module);
            if (!seenModules.add(module)) {
                throw new LayoutException(Error.DUPLICATE_MODULE);
            }
            moduleCount++;
        }
        if (moduleCount > MAX_TOTAL_MODULES) {
            throw new LayoutException(Error.LIMIT);
        }
    }

    public CompoundTag toNbt() {
        CompoundTag root = new CompoundTag();
        root.putInt("version", FORMAT_VERSION);
        root.putInt("selected_page", selectedPage);

        ListTag pageList = new ListTag();
        for (Page page : pages) {
            CompoundTag pageTag = new CompoundTag();
            pageTag.putString("name", page.name());
            ListTag groupList = new ListTag();
            for (Group group : page.groups()) {
                CompoundTag groupTag = new CompoundTag();
                groupTag.putString("name", group.name());
                ListTag modules = new ListTag();
                for (String module : group.modules()) {
                    modules.add(StringTag.valueOf(module));
                }
                groupTag.put("modules", modules);
                groupList.add(groupTag);
            }
            pageTag.put("groups", groupList);
            pageList.add(pageTag);
        }
        root.put("pages", pageList);

        ListTag unassigned = new ListTag();
        for (String module : unassignedModules) {
            unassigned.add(StringTag.valueOf(module));
        }
        root.put("unassigned", unassigned);
        return root;
    }

    public static BeefToolLayout fromNbt(CompoundTag root) throws LayoutException {
        if (!root.contains("version", Tag.TAG_INT)
                || root.getInt("version") != FORMAT_VERSION
                || !root.contains("selected_page", Tag.TAG_INT)
                || !root.contains("pages", Tag.TAG_LIST)
                || !root.contains("unassigned", Tag.TAG_LIST)) {
            throw new LayoutException(Error.UNSUPPORTED_VERSION);
        }

        List<Page> pages = new ArrayList<>();
        ListTag pageList = root.getList("pages", Tag.TAG_COMPOUND);
        for (int i = 0; i < pageList.size(); i++) {
            CompoundTag pageTag = pageList.getCompound(i);
            if (!pageTag.contains("name", Tag.TAG_STRING)
                    || !pageTag.contains("groups", Tag.TAG_LIST)) {
                throw new LayoutException(Error.INVALID_STRUCTURE);
            }
            List<Group> groups = new ArrayList<>();
            ListTag groupList = pageTag.getList("groups", Tag.TAG_COMPOUND);
            for (int j = 0; j < groupList.size(); j++) {
                CompoundTag groupTag = groupList.getCompound(j);
                if (!groupTag.contains("name", Tag.TAG_STRING)
                        || !groupTag.contains("modules", Tag.TAG_LIST)) {
                    throw new LayoutException(Error.INVALID_STRUCTURE);
                }
                List<String> modules = readStringList(groupTag.getList("modules", Tag.TAG_STRING));
                groups.add(new Group(groupTag.getString("name"), modules));
            }
            pages.add(new Page(pageTag.getString("name"), groups));
        }

        List<String> unassigned = readStringList(root.getList("unassigned", Tag.TAG_STRING));
        BeefToolLayout layout = new BeefToolLayout(root.getInt("selected_page"), pages, unassigned);
        layout.validate();
        return layout;
    }

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);
        root.addProperty("selectedPage", selectedPage);

        JsonArray pageArray = new JsonArray();
        for (Page page : pages) {
            JsonObject pageObject = new JsonObject();
            pageObject.addProperty("name", page.name());
            JsonArray groupArray = new JsonArray();
            for (Group group : page.groups()) {
                JsonObject groupObject = new JsonObject();
                groupObject.addProperty("name", group.name());
                JsonArray moduleArray = new JsonArray();
                for (String module : group.modules()) {
                    moduleArray.add(module);
                }
                groupObject.add("modules", moduleArray);
                groupArray.add(groupObject);
            }
            pageObject.add("groups", groupArray);
            pageArray.add(pageObject);
        }
        root.add("pages", pageArray);

        JsonArray unassigned = new JsonArray();
        for (String module : unassignedModules) {
            unassigned.add(module);
        }
        root.add("unassigned", unassigned);
        return GSON.toJson(root);
    }

    public static BeefToolLayout fromJson(String text) throws LayoutException {
        if (text == null || text.getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_LENGTH) {
            throw new LayoutException(Error.LIMIT);
        }

        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) {
                throw new LayoutException(Error.INVALID_TEXT);
            }
            JsonObject root = parsed.getAsJsonObject();
            if (!root.has("version") || root.get("version").getAsInt() != FORMAT_VERSION
                    || !root.has("selectedPage") || !root.has("pages") || !root.has("unassigned")) {
                throw new LayoutException(Error.UNSUPPORTED_VERSION);
            }

            JsonArray pageArray = requireArray(root, "pages");
            List<Page> pages = new ArrayList<>();
            for (JsonElement pageElement : pageArray) {
                JsonObject pageObject = requireObject(pageElement);
                String name = requireString(pageObject, "name");
                JsonArray groupArray = requireArray(pageObject, "groups");
                List<Group> groups = new ArrayList<>();
                for (JsonElement groupElement : groupArray) {
                    JsonObject groupObject = requireObject(groupElement);
                    String groupName = requireString(groupObject, "name");
                    JsonArray moduleArray = requireArray(groupObject, "modules");
                    List<String> modules = new ArrayList<>();
                    for (JsonElement module : moduleArray) {
                        modules.add(requireString(module));
                    }
                    groups.add(new Group(groupName, modules));
                }
                pages.add(new Page(name, groups));
            }

            List<String> unassigned = new ArrayList<>();
            for (JsonElement module : requireArray(root, "unassigned")) {
                unassigned.add(requireString(module));
            }

            BeefToolLayout layout = new BeefToolLayout(
                    root.get("selectedPage").getAsInt(), pages, unassigned);
            layout.validate();
            return layout;
        } catch (LayoutException exception) {
            throw exception;
        } catch (JsonParseException | IllegalStateException | UnsupportedOperationException
                 | ClassCastException | NumberFormatException exception) {
            throw new LayoutException(Error.INVALID_TEXT);
        }
    }

    private static JsonArray requireArray(JsonObject object, String key) throws LayoutException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonArray()) {
            throw new LayoutException(Error.INVALID_STRUCTURE);
        }
        return value.getAsJsonArray();
    }

    private static JsonObject requireObject(JsonElement element) throws LayoutException {
        if (element == null || !element.isJsonObject()) {
            throw new LayoutException(Error.INVALID_STRUCTURE);
        }
        return element.getAsJsonObject();
    }

    private static String requireString(JsonObject object, String key) throws LayoutException {
        JsonElement value = object.get(key);
        if (value == null) {
            throw new LayoutException(Error.INVALID_STRUCTURE);
        }
        return requireString(value);
    }

    private static String requireString(JsonElement element) throws LayoutException {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new LayoutException(Error.INVALID_STRUCTURE);
        }
        return element.getAsString();
    }

    private static List<String> readStringList(ListTag list) throws LayoutException {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof net.minecraft.nbt.StringTag)) {
                throw new LayoutException(Error.INVALID_STRUCTURE);
            }
            values.add(list.getString(i));
        }
        return values;
    }

    private static void validateName(String name) throws LayoutException {
        if (name == null || name.isBlank()
                || name.codePointCount(0, name.length()) > MAX_NAME_LENGTH
                || name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0) {
            throw new LayoutException(Error.INVALID_NAME);
        }
    }

    private static void validateModuleId(String module) throws LayoutException {
        if (module == null || module.isBlank() || module.length() > 64
                || module.indexOf('\n') >= 0 || module.indexOf('\r') >= 0) {
            throw new LayoutException(Error.INVALID_STRUCTURE);
        }
    }

    public static final class Page {
        private String name;
        private final List<Group> groups;

        public Page(String name, List<Group> groups) {
            this.name = name;
            this.groups = new ArrayList<>();
            if (groups != null) {
                for (Group group : groups) {
                    this.groups.add(group.copy());
                }
            }
        }

        public Page(String name) {
            this(name, List.of());
        }

        public String name() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<Group> groups() {
            return groups;
        }

        private Page copy() {
            return new Page(name, groups);
        }
    }

    public static final class Group {
        private String name;
        private final List<String> modules;

        public Group(String name, List<String> modules) {
            this.name = name;
            this.modules = modules == null ? new ArrayList<>() : new ArrayList<>(modules);
        }

        public Group(String name) {
            this(name, List.of());
        }

        public String name() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> modules() {
            return modules;
        }

        private Group copy() {
            return new Group(name, modules);
        }
    }

    public enum Error {
        INVALID_TEXT,
        UNSUPPORTED_VERSION,
        INVALID_STRUCTURE,
        INVALID_NAME,
        DUPLICATE_MODULE,
        UNKNOWN_MODULE,
        LIMIT
    }

    public static final class LayoutException extends Exception {
        private final Error error;

        public LayoutException(Error error) {
            super(error.name());
            this.error = error;
        }

        public Error error() {
            return error;
        }
    }
}
