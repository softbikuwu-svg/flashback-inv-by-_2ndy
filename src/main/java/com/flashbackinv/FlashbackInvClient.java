package com.flashbackinv;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;

public class FlashbackInvClient implements ClientModInitializer {
    public static final String MOD_ID = "flashbackinv";

    @Override
    public void onInitializeClient() {
        // Тип пакета нужен, чтобы Flashback мог записать его в реплей и прочитать обратно.
        PayloadTypeRegistry.playS2C().register(ContainerViewPayload.TYPE, ContainerViewPayload.CODEC);

        // ЗАПИСЬ: раз в тик смотрим, открыто ли меню у записывающего игрока.
        ClientTickEvents.END_CLIENT_TICK.register(ContainerRecorder::tick);

        // Позиция курсора внутри меню (нужна, чтобы показать «перетаскиваемый» предмет).
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof AbstractContainerScreen<?>) {
                ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, tickDelta) -> {
                    ContainerRecorder.mouseX = mouseX;
                    ContainerRecorder.mouseY = mouseY;
                });
            }
        });

        // ВОСПРОИЗВЕДЕНИЕ: пакет приходит из реплея -> запоминаем -> рисуем поверх экрана.
        ClientPlayNetworking.registerGlobalReceiver(ContainerViewPayload.TYPE,
                (payload, context) -> ContainerOverlay.accept(payload));

        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MOD_ID, "container_view"),
                (graphics, tickCounter) -> ContainerOverlay.render(graphics));
    }
}
