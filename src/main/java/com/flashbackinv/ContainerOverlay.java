package com.flashbackinv;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/** При просмотре реплея: рисует меню, которое открыто у игрока, чью камеру ты смотришь. */
public final class ContainerOverlay {
    /** Если новых данных нет дольше этого времени (в тиках реплея) — считаем меню закрытым. */
    private static final long TIMEOUT_TICKS = 60;

    private static volatile ContainerViewPayload current;
    private static volatile long receivedAt;

    private ContainerOverlay() {}

    public static void accept(ContainerViewPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (!payload.open()) {
            current = null;
            return;
        }
        current = payload;
        receivedAt = mc.level != null ? mc.level.getGameTime() : 0L;
    }

    public static void render(GuiGraphics g) {
        ContainerViewPayload p = current;
        if (p == null || !FlashbackBridge.inReplay()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // Показываем только когда смотрим именно от лица записывавшего игрока.
        Entity spectated = FlashbackBridge.spectatingPlayer();
        if (spectated == null || !spectated.getUUID().equals(p.player())) return;

        // Устаревшие данные (перемотка, потеря пакета о закрытии).
        long now = mc.level.getGameTime();
        if (now - receivedAt > TIMEOUT_TICKS || now < receivedAt - 5) {
            current = null;
            return;
        }

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int w = Math.max(p.imageWidth(), 1);
        int h = Math.max(p.imageHeight(), 1);
        int ox = (screenW - w) / 2;
        int oy = (screenH - h) / 2;

        // Фон окна
        g.fill(ox - 1, oy - 1, ox + w + 1, oy + h + 1, 0xFF000000);
        g.fill(ox, oy, ox + w, oy + h, 0xFFC6C6C6);
        g.drawString(mc.font, p.title(), ox + 8, oy + 6, 0xFF404040, false);

        // Слоты и предметы
        for (ContainerViewPayload.SlotEntry e : p.slots()) {
            int sx = ox + e.x();
            int sy = oy + e.y();
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF373737);
            g.fill(sx, sy, sx + 16, sy + 16, 0xFF8B8B8B);
        }
        for (ContainerViewPayload.SlotEntry e : p.slots()) {
            ItemStack stack = e.stack();
            if (stack.isEmpty()) continue;
            g.renderItem(stack, ox + e.x(), oy + e.y());
            g.renderItemDecorations(mc.font, stack, ox + e.x(), oy + e.y());
        }

        // Предмет «в руке» (перетаскиваемый курсором)
        ItemStack carried = p.carried();
        if (!carried.isEmpty()) {
            int cx = ox + p.cursorX() - 8;
            int cy = oy + p.cursorY() - 8;
            g.renderItem(carried, cx, cy);
            g.renderItemDecorations(mc.font, carried, cx, cy);
        }
    }
}
