/*
 * This file is part of the Litematica Server Paster project, licensed under the
 * GNU Lesser General Public License v3.0
 *
 * Copyright (C) 2025  Fallen_Breath and contributors
 *
 * Litematica Server Paster is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Litematica Server Paster is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Litematica Server Paster.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.fallenbreath.lmspaster.network;

import com.google.common.collect.Sets;
import io.netty.buffer.Unpooled;
import me.fallenbreath.lmspaster.LiteModLmsPaster;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Network utilities for the LiteLoader 1.12.2 port.
 *
 * <p>Protocol overview (same logical protocol as the Fabric version):
 * <ul>
 *   <li>Plugin-channel name: {@value #CHANNEL} (≤ 20 chars, fits 1.12.2 vanilla limit)</li>
 *   <li>Packet wire format: {@code [varint packetId] [NBTTagCompound payload]}</li>
 *   <li>C2S packet IDs: see {@link C2S}</li>
 *   <li>S2C packet IDs: see {@link S2C}</li>
 * </ul>
 *
 * <p><b>Server-side note:</b> For the full feature to work, the server must also run a
 * compatible server-side component (Forge mod or Bukkit plugin) that listens on the same
 * channel and handles these packet IDs.  Without a server-side component the client falls
 * back to the vanilla {@code /setblock} / {@code /summon} chat commands which do not carry
 * NBT data.
 */
public class LmsNetwork
{
    /**
     * Plugin-channel name.  Must be ≤ 20 bytes because MC 1.12.2 reads channel names
     * with {@code PacketBuffer.readString(20)}.
     */
    public static final String CHANNEL = "lmspaster";

    // -------------------------------------------------------------------------
    // C2S packet IDs (client → server)
    // -------------------------------------------------------------------------

    public static class C2S
    {
        public static final int HI                   = 0;
        public static final int CHAT                 = 1;
        public static final int VERY_LONG_CHAT_START   = 2;
        public static final int VERY_LONG_CHAT_CONTENT = 3;
        public static final int VERY_LONG_CHAT_END     = 4;

        /** All defined packet IDs, collected via reflection to avoid manual maintenance. */
        public static final int[] ALL_PACKET_IDS;

        static
        {
            Set<Integer> ids = Sets.newLinkedHashSet();
            for (Field f : C2S.class.getFields())
            {
                if (f.getType() == int.class
                        && Modifier.isStatic(f.getModifiers())
                        && Modifier.isFinal(f.getModifiers()))
                {
                    try
                    {
                        int id = (int) f.get(null);
                        if (!ids.add(id))
                        {
                            LiteModLmsPaster.LOGGER.error("Duplicate C2S packet id {} ({})", id, f.getName());
                        }
                    }
                    catch (Exception e)
                    {
                        LiteModLmsPaster.LOGGER.error("Failed to read field {}: {}", f, e);
                    }
                }
            }
            ALL_PACKET_IDS = new int[ids.size()];
            int i = 0;
            for (int id : ids) { ALL_PACKET_IDS[i++] = id; }
        }

        /**
         * Builds a {@link CPacketCustomPayload} for the given packet ID and NBT payload.
         *
         * @param packetId C2S packet ID (one of the constants above)
         * @param builder  fills in the NBT payload
         * @return a ready-to-send packet
         */
        public static CPacketCustomPayload packet(int packetId, Consumer<NBTTagCompound> builder)
        {
            NBTTagCompound nbt = new NBTTagCompound();
            builder.accept(nbt);
            PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
            LmsPasterPacket.write(buf, packetId, nbt);
            return new CPacketCustomPayload(CHANNEL, buf);
        }
    }

    // -------------------------------------------------------------------------
    // S2C packet IDs (server → client)
    // -------------------------------------------------------------------------

    public static class S2C
    {
        public static final int HI             = 0;
        public static final int ACCEPT_PACKETS = 1;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Sends a C2S custom-payload packet on the current connection.
     * No-op if there is no active connection.
     */
    public static void sendPacket(CPacketCustomPayload packet)
    {
        if (Minecraft.getMinecraft().getConnection() != null)
        {
            Minecraft.getMinecraft().getConnection().sendPacket(packet);
        }
    }

    /**
     * Wraps a raw byte array received from LiteLoader into a {@link PacketBuffer}
     * for easy reading.
     */
    public static PacketBuffer wrapBytes(byte[] data, int length)
    {
        PacketBuffer buf = new PacketBuffer(Unpooled.wrappedBuffer(data, 0, length));
        return buf;
    }
}
