package com.flashbackinv;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Мост к Flashback через рефлексию (так аддону не нужен jar Flashback при сборке).
 * Используются только публичные члены Flashback 0.39.x:
 *   Flashback.RECORDER, Flashback.isInReplay(), Flashback.getSpectatingPlayer(),
 *   Recorder.readyToWrite(), Recorder.writePacketAsync(Packet, ConnectionProtocol)
 */
public final class FlashbackBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("flashbackinv");

    private static boolean ok;
    private static Field recorderField;
    private static Method readyToWrite;
    private static Method writePacketAsync;
    private static Method isInReplay;
    private static Method getSpectatingPlayer;

    static {
        try {
            ClassLoader cl = FlashbackBridge.class.getClassLoader();
            Class<?> flashback = Class.forName("com.moulberry.flashback.Flashback", true, cl);
            Class<?> recorder = Class.forName("com.moulberry.flashback.record.Recorder", true, cl);
            recorderField = flashback.getField("RECORDER");
            isInReplay = flashback.getMethod("isInReplay");
            getSpectatingPlayer = flashback.getMethod("getSpectatingPlayer");
            readyToWrite = recorder.getMethod("readyToWrite");
            for (Method m : recorder.getMethods()) {
                if (m.getName().equals("writePacketAsync") && m.getParameterCount() == 2) {
                    writePacketAsync = m;
                }
            }
            ok = writePacketAsync != null;
            if (!ok) LOGGER.error("Flashback.Recorder.writePacketAsync not found - recording disabled");
        } catch (Throwable t) {
            ok = false;
            LOGGER.error("Could not link to Flashback - addon disabled", t);
        }
    }

    private FlashbackBridge() {}

    public static boolean inReplay() {
        if (!ok) return false;
        try {
            return (boolean) isInReplay.invoke(null);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Игрок, от лица которого сейчас смотрят реплей (или null). */
    public static Entity spectatingPlayer() {
        if (!ok) return null;
        try {
            return (Entity) getSpectatingPlayer.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Идёт ли сейчас запись и готов ли Flashback принимать пакеты. */
    public static boolean canRecord() {
        if (!ok || inReplay()) return false;
        try {
            Object rec = recorderField.get(null);
            return rec != null && (boolean) readyToWrite.invoke(rec);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Записать наш пакет в текущую запись Flashback. */
    public static void record(CustomPacketPayload payload) {
        if (!ok) return;
        try {
            Object rec = recorderField.get(null);
            if (rec == null) return;
            writePacketAsync.invoke(rec, new ClientboundCustomPayloadPacket(payload), ConnectionProtocol.PLAY);
        } catch (Throwable t) {
            LOGGER.warn("Failed to write container snapshot to replay", t);
        }
    }
}
