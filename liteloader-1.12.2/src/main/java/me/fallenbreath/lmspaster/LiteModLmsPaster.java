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

package me.fallenbreath.lmspaster;

import com.mumfrey.liteloader.client.IClientPluginChannelListener;
import com.mumfrey.liteloader.core.LiteLoader;
import me.fallenbreath.lmspaster.network.ClientNetworkHandler;
import me.fallenbreath.lmspaster.network.LmsNetwork;
import net.minecraft.network.PacketBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 * Main LiteMod entry point for the Litematica Server Paster 1.12.2 LiteLoader port.
 *
 * <p>This class implements:
 * <ul>
 *   <li>{@link com.mumfrey.liteloader.LiteMod} — required LiteLoader interface</li>
 *   <li>{@link IClientPluginChannelListener} — to receive S2C custom plugin-channel
 *       packets sent by a server that also has this mod installed</li>
 * </ul>
 *
 * <p>World-join / respawn detection is handled by
 * {@link me.fallenbreath.lmspaster.mixins.NetHandlerPlayClientMixin}, which injects
 * into {@code NetHandlerPlayClient.handleJoinGame()} and
 * {@code NetHandlerPlayClient.handleRespawn()} to send the C2S HI handshake at the
 * correct moment.
 */
public class LiteModLmsPaster
        implements com.mumfrey.liteloader.LiteMod,
                   IClientPluginChannelListener
{
    public static final Logger LOGGER = LogManager.getLogger("LmsP");

    public static final String MOD_NAME = "Litematica Server Paster";

    /**
     * Mod version — populated from litemod.json during {@link #init(File)}.
     */
    public static String VERSION = "unknown";

    // -------------------------------------------------------------------------
    // LiteMod
    // -------------------------------------------------------------------------

    @Override
    public String getName()
    {
        return MOD_NAME;
    }

    @Override
    public String getVersion()
    {
        return VERSION;
    }

    @Override
    public void init(File configPath)
    {
        // Read the version from the LiteLoader metadata map.
        try
        {
            VERSION = LiteLoader.getInstance().getModMetaData(this, "version", "unknown");
        }
        catch (Exception ignored) {}

        LOGGER.info("[{}] Initialised (version {})", MOD_NAME, VERSION);
        // Mixins are bootstrapped automatically by the mixin agent via the
        // "MixinConfigs: mixins.lmspaster.json" attribute in the jar manifest.
        // No programmatic registration is needed or possible here (init() is
        // called too late for Mixin injection).
    }

    @Override
    public void upgradeSettings(String version, File configPath, File oldConfigPath)
    {
        // No persistent settings to migrate.
    }

    // -------------------------------------------------------------------------
    // IClientPluginChannelListener  (S2C custom-payload packets)
    // -------------------------------------------------------------------------

    /**
     * Declares the plugin-channel identifiers this mod listens on (S2C direction).
     * LiteLoader automatically sends a REGISTER packet to the server so the server
     * knows the client has this channel available.
     */
    @Override
    public String[] getChannels()
    {
        return new String[]{ LmsNetwork.CHANNEL };
    }

    /**
     * Invoked by LiteLoader whenever the server sends a custom-payload packet on
     * one of our registered channels.
     *
     * @param channel channel name
     * @param length  payload byte length
     * @param data    raw payload bytes
     */
    @Override
    public void onCustomPayload(String channel, int length, byte[] data)
    {
        if (LmsNetwork.CHANNEL.equals(channel))
        {
            PacketBuffer buf = LmsNetwork.wrapBytes(data, length);
            ClientNetworkHandler.handleServerPacket(buf);
        }
    }
}
