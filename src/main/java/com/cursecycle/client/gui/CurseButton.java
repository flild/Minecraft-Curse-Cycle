package com.cursecycle.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Vanilla button behavior, focus and narration with Curse-Cycle's shared materials. */
public class CurseButton extends ButtonWidget {
    private final boolean selected;

    public CurseButton(int x, int y, int width, int height, Text text, boolean selected, PressAction action) {
        super(x, y, width, height, text, action, DEFAULT_NARRATION_SUPPLIER);
        this.selected = selected;
    }

    @Override protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean highlighted = isHovered() || isFocused();
        context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(),
                !active ? 0xFF19171D : highlighted ? 0xFF39303E : selected ? 0xFF302638 : 0xFF211E26);
        context.drawBorder(getX(), getY(), getWidth(), getHeight(),
                active && highlighted ? CurseStyle.BRONZE : CurseStyle.EDGE);
        if (selected) context.fill(getX() + 1, getY() + getHeight() - 2,
                getX() + getWidth() - 1, getY() + getHeight() - 1, CurseStyle.PURPLE);
        drawMessage(context, MinecraftClient.getInstance().textRenderer,
                active ? CurseStyle.TEXT : CurseStyle.MUTED);
    }
}
