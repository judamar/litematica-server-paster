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

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

/**
 * Wire-format helper for the mod's custom plugin-channel packets.
 *
 * <p>Each packet consists of:
 * <ol>
 *   <li>A variable-length integer (packet ID)</li>
 *   <li>An {@link NBTTagCompound} carrying the payload fields</li>
 * </ol>
 *
 * <p>This mirrors the structure used by the Fabric version so that a Fabric server
 * side (1.14.4+) could, in principle, support a 1.12.2 LiteLoader client if both
 * sides are updated to use the same channel name.
 */
public class LmsPasterPacket
{
    private final int id;
    private final NBTTagCompound nbt;

    public LmsPasterPacket(int id, NBTTagCompound nbt)
    {
        this.id  = id;
        this.nbt = nbt;
    }

    // -------------------------------------------------------------------------
    // Static read / write helpers (used by LmsNetwork)
    // -------------------------------------------------------------------------

    /**
     * Reads a packet from {@code buf}.
     *
     * @param buf buffer positioned at the start of packet data
     * @return decoded packet
     */
    public static LmsPasterPacket read(PacketBuffer buf)
    {
        int id = buf.readVarInt();
        NBTTagCompound nbt = buf.readNBTTagCompoundFromBuffer();
        return new LmsPasterPacket(id, nbt != null ? nbt : new NBTTagCompound());
    }

    /**
     * Writes a packet to {@code buf}.
     *
     * @param buf      destination buffer
     * @param packetId packet type ID
     * @param nbt      payload
     */
    public static void write(PacketBuffer buf, int packetId, NBTTagCompound nbt)
    {
        buf.writeVarInt(packetId);
        buf.writeNBTTagCompoundToBuffer(nbt);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int getPacketId()
    {
        return this.id;
    }

    public NBTTagCompound getNbt()
    {
        return this.nbt;
    }
}
