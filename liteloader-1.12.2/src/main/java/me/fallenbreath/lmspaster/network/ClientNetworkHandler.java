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

import me.fallenbreath.lmspaster.LiteModLmsPaster;
import me.fallenbreath.lmspaster.utils.NbtUtils;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Client-side network handler.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Track which packet IDs the server accepts (populated after the S2C HI handshake)</li>
 *   <li>Send the C2S HI packet on world join / respawn</li>
 *   <li>Provide {@link #sendCommand(String)} which the mixin layer calls instead of
 *       {@code EntityPlayerSP.sendChatMessage(String)}</li>
 * </ul>
 */
public class ClientNetworkHandler
{
    private static final int[] MINIMUM_SUPPORT_PACKETS =
            new int[]{ LmsNetwork.C2S.HI, LmsNetwork.C2S.CHAT };

    /** Packet IDs the server has declared it accepts; empty means server has no mod. */
    private static int[] supportedPackets = new int[0];

    // -------------------------------------------------------------------------
    // Incoming S2C packets
    // -------------------------------------------------------------------------

    /**
     * Called by {@link LiteModLmsPaster#onCustomPayload} for every packet received
     * on our plugin channel.
     */
    public static void handleServerPacket(PacketBuffer buf)
    {
        LmsPasterPacket packet = LmsPasterPacket.read(buf);
        int id  = packet.getPacketId();
        NBTTagCompound nbt = packet.getNbt();

        switch (id)
        {
            case LmsNetwork.S2C.HI:
                String serverVersion = NbtUtils.getStringOrEmpty(nbt, "mod_version");
                LiteModLmsPaster.LOGGER.info(
                        "Server has {} @ {}", LiteModLmsPaster.MOD_NAME, serverVersion);
                supportedPackets = MINIMUM_SUPPORT_PACKETS.clone();
                break;

            case LmsNetwork.S2C.ACCEPT_PACKETS:
                supportedPackets = NbtUtils.getIntArrayOrEmpty(nbt, "ids");
                LiteModLmsPaster.LOGGER.debug(
                        "Server accepts packet IDs: {}", Arrays.toString(supportedPackets));
                break;

            default:
                LiteModLmsPaster.LOGGER.warn("Unknown S2C packet id {}", id);
                break;
        }
    }

    // -------------------------------------------------------------------------
    // Outgoing C2S packets
    // -------------------------------------------------------------------------

    /**
     * Resets state and sends the C2S HI packet to the server.
     * Called from {@link me.fallenbreath.lmspaster.mixins.NetHandlerPlayClientMixin}
     * on world join and respawn.
     */
    public static void sendHiToServer(NetHandlerPlayClient connection)
    {
        supportedPackets = new int[0];
        connection.sendPacket(LmsNetwork.C2S.packet(
                LmsNetwork.C2S.HI,
                nbt -> nbt.setString("mod_version", LiteModLmsPaster.VERSION)));
    }

    // -------------------------------------------------------------------------
    // Server capability queries
    // -------------------------------------------------------------------------

    /** Returns {@code true} if the connected server has the mod installed. */
    public static boolean isServerPasterAvailable()
    {
        return supportedPackets.length > 0;
    }

    /** Returns {@code true} if the server supports "very long chat" segmented packets. */
    public static boolean doesServerAcceptVeryLongChat()
    {
        for (int id : supportedPackets)
        {
            if (id == LmsNetwork.C2S.VERY_LONG_CHAT_START) return true;
        }
        return false;
    }

    /** Returns {@code true} if the given command can be sent to the server. */
    public static boolean canSendCommand(String command)
    {
        if (command.isEmpty()) return false;
        if (doesServerAcceptVeryLongChat()) return true;
        return !isStringVeryLong(command);
    }

    // -------------------------------------------------------------------------
    // Send helpers
    // -------------------------------------------------------------------------

    /**
     * Sends a command to the server via the custom plugin channel.
     * Falls back to nothing if the server does not support the mod —
     * callers must check {@link #isServerPasterAvailable()} first.
     */
    public static void sendCommand(String command)
    {
        if (isStringVeryLong(command))
        {
            if (doesServerAcceptVeryLongChat())
            {
                sendVeryLongCommand(command);
            }
            else
            {
                LiteModLmsPaster.LOGGER.warn(
                        "Command too long for server (length {}), dropping", command.length());
            }
            return;
        }

        LmsNetwork.sendPacket(LmsNetwork.C2S.packet(
                LmsNetwork.C2S.CHAT,
                nbt -> nbt.setString("chat", command)));
    }

    private static void sendVeryLongCommand(String command)
    {
        final int segmentLength = 8000; // ~Short.MAX_VALUE/4, safe for 4-byte UTF-8

        LmsNetwork.sendPacket(LmsNetwork.C2S.packet(LmsNetwork.C2S.VERY_LONG_CHAT_START, nbt -> {}));

        for (int i = 0; i < command.length(); i += segmentLength)
        {
            final String segment = command.substring(i, Math.min(command.length(), i + segmentLength));
            LmsNetwork.sendPacket(LmsNetwork.C2S.packet(
                    LmsNetwork.C2S.VERY_LONG_CHAT_CONTENT,
                    nbt -> nbt.setString("segment", segment)));
        }

        LmsNetwork.sendPacket(LmsNetwork.C2S.packet(LmsNetwork.C2S.VERY_LONG_CHAT_END, nbt -> {}));
    }

    private static boolean isStringVeryLong(String s)
    {
        // -100 for safety headroom
        return s.getBytes(StandardCharsets.UTF_8).length > Short.MAX_VALUE - 100;
    }
}
