package com.flashbackinv;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Снимок открытого меню: какие предметы лежат в каких слотах, что у игрока "в руке" (курсоре)
 * и где находится курсор. Этот пакет Flashback записывает в реплей как обычный пакет.
 */
public record ContainerViewPayload(
        UUID player,
        boolean open,
        String title,
        int imageWidth,
        int imageHeight,
        int cursorX,
        int cursorY,
        ItemStack carried,
        List<SlotEntry> slots) implements CustomPacketPayload {

    public record SlotEntry(int x, int y, ItemStack stack) {}

    public static final CustomPacketPayload.Type<ContainerViewPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(FlashbackInvClient.MOD_ID, "container_view"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ContainerViewPayload> CODEC =
            StreamCodec.of(ContainerViewPayload::write, ContainerViewPayload::read);

    public static ContainerViewPayload closed(UUID player) {
        return new ContainerViewPayload(player, false, "", 0, 0, 0, 0, ItemStack.EMPTY, List.of());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, ContainerViewPayload p) {
        buf.writeUUID(p.player);
        buf.writeBoolean(p.open);
        buf.writeUtf(p.title, 256);
        buf.writeVarInt(p.imageWidth);
        buf.writeVarInt(p.imageHeight);
        buf.writeInt(p.cursorX);
        buf.writeInt(p.cursorY);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.carried);
        buf.writeVarInt(p.slots.size());
        for (SlotEntry e : p.slots) {
            buf.writeShort(e.x);
            buf.writeShort(e.y);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, e.stack);
        }
    }

    private static ContainerViewPayload read(RegistryFriendlyByteBuf buf) {
        UUID player = buf.readUUID();
        boolean open = buf.readBoolean();
        String title = buf.readUtf(256);
        int w = buf.readVarInt();
        int h = buf.readVarInt();
        int cx = buf.readInt();
        int cy = buf.readInt();
        ItemStack carried = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        int n = Math.min(buf.readVarInt(), 1024);
        List<SlotEntry> slots = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int x = buf.readShort();
            int y = buf.readShort();
            ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            slots.add(new SlotEntry(x, y, stack));
        }
        return new ContainerViewPayload(player, open, title, w, h, cx, cy, carried, slots);
    }
}
