package com.flashbackinv;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Во время записи: следит за открытым меню и пишет его состояние в реплей. */
public final class ContainerRecorder {
    /** Обновляются из ScreenEvents.afterRender. */
    public static volatile int mouseX;
    public static volatile int mouseY;

    private static final int HEARTBEAT_TICKS = 20;

    private static boolean wasOpen;
    private static int lastContainerId = -1;
    private static List<ItemStack> lastStacks = new ArrayList<>();
    private static ItemStack lastCarried = ItemStack.EMPTY;
    private static int lastCursorX;
    private static int lastCursorY;
    private static int ticksSinceSend;

    private ContainerRecorder() {}

    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || !FlashbackBridge.canRecord()) {
            reset();
            return;
        }

        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) {
            if (wasOpen) {
                FlashbackBridge.record(ContainerViewPayload.closed(player.getUUID()));
            }
            reset();
            return;
        }

        AbstractContainerMenu menu = screen.getMenu();

        List<ItemStack> stacks = new ArrayList<>(menu.slots.size());
        for (Slot slot : menu.slots) {
            stacks.add(slot.getItem().copy());
        }
        ItemStack carried = menu.getCarried().copy();
        int cursorX = mouseX - screen.leftPos;
        int cursorY = mouseY - screen.topPos;

        boolean changed = !wasOpen
                || menu.containerId != lastContainerId
                || !sameStacks(stacks, lastStacks)
                || !ItemStack.matches(carried, lastCarried)
                || (!carried.isEmpty() && (cursorX != lastCursorX || cursorY != lastCursorY));

        ticksSinceSend++;
        if (!changed && ticksSinceSend < HEARTBEAT_TICKS) {
            return;
        }

        List<ContainerViewPayload.SlotEntry> entries = new ArrayList<>(menu.slots.size());
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            entries.add(new ContainerViewPayload.SlotEntry(slot.x, slot.y, stacks.get(i)));
        }

        FlashbackBridge.record(new ContainerViewPayload(
                player.getUUID(),
                true,
                screen.getTitle().getString(),
                screen.imageWidth,
                screen.imageHeight,
                cursorX,
                cursorY,
                carried,
                entries));

        wasOpen = true;
        lastContainerId = menu.containerId;
        lastStacks = stacks;
        lastCarried = carried;
        lastCursorX = cursorX;
        lastCursorY = cursorY;
        ticksSinceSend = 0;
    }

    private static boolean sameStacks(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.matches(a.get(i), b.get(i))) return false;
        }
        return true;
    }

    private static void reset() {
        wasOpen = false;
        lastContainerId = -1;
        lastStacks = new ArrayList<>();
        lastCarried = ItemStack.EMPTY;
        ticksSinceSend = 0;
    }
}
