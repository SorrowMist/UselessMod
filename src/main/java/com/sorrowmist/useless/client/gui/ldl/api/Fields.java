package com.sorrowmist.useless.client.gui.ldl.api;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** 一个界面上的全部输入框：统一创建、每 tick 同步、回车提交、Esc 以外按键优先给输入框、关界面提交。 */
public final class Fields {
    private final List<NumberField> numbers = new ArrayList<>();
    private final List<NumberField.Text> texts = new ArrayList<>();

    public NumberField add(NumberField f) { numbers.add(f); return f; }
    public NumberField.Text add(NumberField.Text f) { texts.add(f); return f; }

    /** init 时创建 EditBox 并交给 Screen.addRenderableWidget。位置由布局里的 AppleTextField/AppleStepper 每帧接管。 */
    public void create(int left, int top, Consumer<EditBox> addWidget) {
        for (NumberField f : numbers) addWidget.accept(f.create(left, top, 46, Component.empty()));
        for (NumberField.Text t : texts) addWidget.accept(t.create(left, top, 80, Component.empty()));
    }

    public void tick() { for (NumberField f : numbers) f.sync(false); }

    /** @return true = 已消费；{@code defocus} 用于回车后清焦点。 */
    public boolean keyPressed(int key, int scan, int mods, Runnable defocus) {
        for (NumberField f : numbers) {
            if (f.box == null || !f.box.isFocused()) continue;
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { f.submit(); defocus.run(); return true; }
        }
        for (EditBox b : boxes())
            if (key != GLFW.GLFW_KEY_ESCAPE && (b.keyPressed(key, scan, mods) || b.canConsumeInput())) return true;
        return false;
    }

    public List<EditBox> boxes() {
        List<EditBox> l = new ArrayList<>();
        for (NumberField f : numbers) if (f.box != null) l.add(f.box);
        for (NumberField.Text t : texts) if (t.box != null) l.add(t.box);
        return l;
    }

    public void onClose() { for (NumberField f : numbers) f.commitIfDirty(); }
}
