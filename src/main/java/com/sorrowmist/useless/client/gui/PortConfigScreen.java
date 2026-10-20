package com.sorrowmist.useless.client.gui;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.Gson;
import com.sorrowmist.useless.core.config.ConfigManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import java.util.*;

/** Forge 1.20.1 config editor: validates the whole visible page before applying anything. */
public final class PortConfigScreen extends Screen {
    private static final Gson JSON = new Gson();
    private final Screen parent;
    private ForgeConfigSpec spec = ConfigManager.COMMON_SPEC;
    private final List<ForgeConfigSpec.ConfigValue<?>> values = new ArrayList<>();
    private final Map<ForgeConfigSpec.ConfigValue<?>, EditBox> editors = new LinkedHashMap<>();
    private int page;
    private boolean singleSpec;
    private String message = "";
    public PortConfigScreen(Screen parent) { super(Component.literal("Useless Mod 配置")); this.parent = parent; }
    public PortConfigScreen(Screen parent, ForgeConfigSpec selected, Component title) {
        super(title); this.parent=parent; this.spec=selected; this.singleSpec=true;
    }
    private void collect(UnmodifiableConfig config) {
        for (Object value : config.valueMap().values()) {
            if (value instanceof ForgeConfigSpec.ConfigValue<?> entry) values.add(entry);
            else if (value instanceof UnmodifiableConfig nested) collect(nested);
        }
    }
    private void select(ForgeConfigSpec selected) { spec=selected; page=0; message=""; rebuildWidgets(); }
    private int rows() { return Math.max(1, (height-135)/40); }
    @Override protected void init() {
        values.clear(); editors.clear();
        if (spec.isLoaded()) collect(spec.getValues());
        values.sort(Comparator.comparing(value -> String.join(".",value.getPath())));
        if (!singleSpec) {
        addRenderableWidget(Button.builder(Component.literal("通用"),b->select(ConfigManager.COMMON_SPEC)).bounds(width/2-155,25,100,20).build());
        addRenderableWidget(Button.builder(Component.literal("客户端"),b->select(ConfigManager.CLIENT_SPEC)).bounds(width/2-50,25,100,20).build());
        Button server=Button.builder(Component.literal("单人存档"),b->select(ConfigManager.SERVER_SPEC)).bounds(width/2+55,25,100,20).build();
        server.active=minecraft.hasSingleplayerServer() && ConfigManager.SERVER_SPEC.isLoaded(); addRenderableWidget(server);
        }
        page=Math.min(page,Math.max(0,(values.size()-1)/rows()));
        int start=page*rows();
        for(int i=start;i<Math.min(values.size(),start+rows());i++) {
            var value=values.get(i); int y=60+(i-start)*40;
            EditBox box=new EditBox(font,15,y+13,width-30,20,Component.literal(String.join(".",value.getPath())));
            box.setMaxLength(32767); Object current=value.get();
            box.setValue(current instanceof List<?> ? JSON.toJson(current) : String.valueOf(current));
            ForgeConfigSpec.ValueSpec rule=spec.getSpec().get(value.getPath());
            if(rule.getComment()!=null) box.setTooltip(Tooltip.create(Component.literal(rule.getComment())));
            editors.put(value,box); addRenderableWidget(box);
        }
        addRenderableWidget(Button.builder(Component.literal("上一页"),b->{page--;rebuildWidgets();}).bounds(15,height-60,75,20).build()).active=page>0;
        addRenderableWidget(Button.builder(Component.literal("下一页"),b->{page++;rebuildWidgets();}).bounds(95,height-60,75,20).build()).active=(page+1)*rows()<values.size();
        addRenderableWidget(Button.builder(Component.literal("保存本页"),b->save()).bounds(width-175,height-60,75,20).build()).active=spec.isLoaded();
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(width-95,height-60,80,20).build());
        if(!spec.isLoaded()) message="该配置尚未加载。";
    }
    private Object parse(Object original,String text) {
        if(original instanceof Boolean) { if(!text.equalsIgnoreCase("true")&&!text.equalsIgnoreCase("false")) throw new IllegalArgumentException("请输入 true 或 false"); return Boolean.parseBoolean(text); }
        if(original instanceof Integer) return Integer.valueOf(text);
        if(original instanceof Long) return Long.valueOf(text);
        if(original instanceof Double) return Double.valueOf(text);
        if(original instanceof Float) return Float.valueOf(text);
        if(original instanceof Enum<?> item) return Arrays.stream(item.getDeclaringClass().getEnumConstants()).filter(e->((Enum<?>)e).name().equalsIgnoreCase(text)).findFirst().orElseThrow();
        if(original instanceof List<?>) return JSON.fromJson(text,List.class);
        return text;
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private void save() {
        Map<ForgeConfigSpec.ConfigValue<?>,Object> pending=new LinkedHashMap<>();
        try {
            for(var entry:editors.entrySet()) {
                var key=entry.getKey(); Object parsed=parse(key.get(),entry.getValue().getValue());
                ForgeConfigSpec.ValueSpec rule=spec.getSpec().get(key.getPath());
                if(!rule.test(parsed)) throw new IllegalArgumentException(String.join(".",key.getPath()));
                pending.put(key,parsed);
            }
            pending.forEach((key,value)->((ForgeConfigSpec.ConfigValue)key).set(value)); spec.save();
            message="已保存。标记为需要重启的配置将在重启后生效。";
        } catch(RuntimeException invalid) { message="未保存："+Objects.toString(invalid.getMessage(),"配置值无效"); }
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        renderBackground(graphics); super.render(graphics,mouseX,mouseY,partialTick);
        graphics.drawCenteredString(font,title,width/2,8,0xFFFFFF);
        int start=page*rows();
        for(int i=start;i<Math.min(values.size(),start+rows());i++) graphics.drawString(font,font.plainSubstrByWidth(String.join(".",values.get(i).getPath()),width-30),15,60+(i-start)*40,0xFFFFFF);
        graphics.drawString(font,font.plainSubstrByWidth(message,width-30),15,height-30,0xFFFF80);
    }
}
