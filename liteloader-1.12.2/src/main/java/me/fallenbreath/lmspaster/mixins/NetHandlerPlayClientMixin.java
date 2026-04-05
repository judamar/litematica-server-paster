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

package me.fallenbreath.lmspaster.mixins;

import me.fallenbreath.lmspaster.network.ClientNetworkHandler;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.SPacketJoinGame;
import net.minecraft.network.play.server.SPacketRespawn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into the two packet handlers that fire when the client (re)joins a world:
 * <ul>
 *   <li>{@code handleJoinGame} — sent when the player enters a world for the first time</li>
 *   <li>{@code handleRespawn} — sent when the player respawns after death or dimension
 *       change</li>
 * </ul>
 *
 * <p>On each event we reset the "server is paster-aware" flag and send the C2S HI
 * handshake so the server can advertise which packet IDs it supports.
 */
@Mixin(NetHandlerPlayClient.class)
public abstract class NetHandlerPlayClientMixin
{
    @Inject(method = "handleJoinGame", at = @At("RETURN"))
    private void onJoinGame(SPacketJoinGame packetIn, CallbackInfo ci)
    {
        ClientNetworkHandler.sendHiToServer((NetHandlerPlayClient) (Object) this);
    }

    @Inject(method = "handleRespawn", at = @At("RETURN"))
    private void onRespawn(SPacketRespawn packetIn, CallbackInfo ci)
    {
        ClientNetworkHandler.sendHiToServer((NetHandlerPlayClient) (Object) this);
    }
}

