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

import fi.dy.masa.litematica.scheduler.tasks.TaskPasteSchematicSetblock;
import me.fallenbreath.lmspaster.LiteModLmsPaster;
import me.fallenbreath.lmspaster.network.ClientNetworkHandler;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Intercepts Litematica's paste-schematic block and entity commands and replaces
 * the plain {@code /setblock} / {@code /summon} chat messages with custom
 * plugin-channel packets that carry full NBT data.
 *
 * <h3>Block pasting</h3>
 * <ol>
 *   <li>{@link #recordCurrentSchematicChunk} — stores the current schematic chunk so
 *       that we can later retrieve the {@link TileEntity} for the block being pasted.</li>
 *   <li>{@link #useCustomPacketToPasteBlockNbt} — fires just before Litematica calls
 *       {@code sendChatMessage} for a {@code /setblock} command; if the server supports
 *       our mod we rebuild the command with the full tile-entity NBT and send it via the
 *       custom channel instead.</li>
 * </ol>
 *
 * <h3>Entity pasting</h3>
 * <ol>
 *   <li>{@link #recordCurrentEntity} — stores the entity whose summon command is about
 *       to be sent.</li>
 *   <li>{@link #useCustomPacketToPasteEntityNbt} — redirects the {@code sendChatMessage}
 *       call for each {@code /summon} command; appends entity NBT and sends via the
 *       custom channel when the server supports it.</li>
 * </ol>
 *
 * <p><b>Note:</b> The exact method/class names in this mixin target
 * {@code fi.dy.masa.litematica.scheduler.tasks.TaskPasteSchematicSetblock} as it
 * existed in the last released LiteLoader build of Litematica for MC 1.12.2.  If
 * masa changed internal names you may need to adjust the {@code method} and
 * {@code target} strings.
 */
@Mixin(TaskPasteSchematicSetblock.class)
public abstract class TaskPasteSchematicSetblockMixin
{
    // -------------------------------------------------------------------------
    // Block pasting
    // -------------------------------------------------------------------------

    /**
     * The schematic {@link Chunk} currently being processed by {@code processBox}.
     * Populated via {@link #recordCurrentSchematicChunk}.
     */
    @Unique
    private Chunk currentSchematicChunk;

    /**
     * Captures the schematic chunk each time {@code processBox} fetches one from the
     * schematic world's chunk provider.
     *
     * <p>Target: the first {@link Chunk} local variable that is stored after a call to
     * {@code ChunkProviderSchematic.provideChunk(int, int)}.
     *
     * <p><em>Adjustment hint:</em> In Litematica 1.12.2 the chunk-provider method may be
     * named {@code provideChunk} or {@code getChunk}; update the {@code target} string
     * accordingly if the mixin fails to apply.
     */
    @ModifyVariable(
            method = "processBox",
            at = @At(
                    value = "STORE",
                    target = "Lfi/dy/masa/litematica/world/ChunkProviderSchematic;provideChunk(II)Lnet/minecraft/world/chunk/Chunk;",
                    remap = false
            ),
            remap = false,
            ordinal = 0
    )
    private Chunk recordCurrentSchematicChunk(Chunk schematicChunk)
    {
        this.currentSchematicChunk = schematicChunk;
        return schematicChunk;
    }

    /**
     * Intercepts the {@code /setblock} command dispatch inside
     * {@code sendSetBlockCommand(int, int, int, IBlockState)}.
     *
     * <p>When the connected server has our mod installed and the target block position
     * holds a tile entity in the schematic, we rebuild the command to include the full
     * tile-entity NBT tag and send it via the custom plugin channel.  Otherwise we fall
     * through to the vanilla {@code sendChatMessage} call.
     *
     * <p>Command format for MC 1.12.2:
     * {@code /setblock <x> <y> <z> <block:name> <meta> replace {nbt}}
     */
    @Redirect(
            method = "sendSetBlockCommand",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/entity/EntityPlayerSP;sendChatMessage(Ljava/lang/String;)V",
                    remap = true
            ),
            remap = false
    )
    private void useCustomPacketToPasteBlockNbt(
            EntityPlayerSP player, String originalCommand,
            /* enclosing method args */ int x, int y, int z, IBlockState state)
    {
        if (ClientNetworkHandler.isServerPasterAvailable() && this.currentSchematicChunk != null)
        {
            TileEntity te = this.currentSchematicChunk.getTileEntity(
                    new BlockPos(x, y, z), Chunk.EnumCreateEntityType.CHECK);

            if (te != null)
            {
                NBTTagCompound tag = te.writeToNBT(new NBTTagCompound());
                tag.removeTag("id");
                tag.removeTag("x");
                tag.removeTag("y");
                tag.removeTag("z");

                ResourceLocation blockName = Block.REGISTRY.getNameForObject(state.getBlock());
                int meta = state.getBlock().getMetaFromState(state);
                String tagString = tag.toString();
                String command = String.format("/setblock %d %d %d %s %d replace %s",
                        x, y, z, blockName, meta, tagString);

                if (ClientNetworkHandler.canSendCommand(command))
                {
                    LiteModLmsPaster.LOGGER.info(
                            "Pasting block {} at [{}, {}, {}] with NBT tag",
                            blockName, x, y, z);
                    ClientNetworkHandler.sendCommand(command);
                    return;
                }
            }
        }
        // Fall back to vanilla chat
        player.sendChatMessage(originalCommand);
    }

    // -------------------------------------------------------------------------
    // Entity pasting
    // -------------------------------------------------------------------------

    /**
     * The entity whose {@code /summon} command is currently being prepared.
     * Populated via {@link #recordCurrentEntity}.
     */
    @Unique
    private Entity currentEntity;

    /**
     * Captures each entity as {@code summonEntities} iterates over the list.
     *
     * <p>Target: the first {@link Entity} local variable that is stored after a call to
     * {@code Iterator.next()} inside {@code summonEntities}.
     */
    @ModifyVariable(
            method = "summonEntities",
            at = @At(
                    value = "STORE",
                    target = "Ljava/util/Iterator;next()Ljava/lang/Object;",
                    remap = false
            ),
            remap = false,
            ordinal = 0
    )
    private Entity recordCurrentEntity(Entity entity)
    {
        this.currentEntity = entity;
        return entity;
    }

    /**
     * Intercepts each {@code /summon} command dispatched inside {@code summonEntities}.
     *
     * <p>When the server supports our mod we append the entity's full NBT data to the
     * command and send it via the custom channel.  Passenger entities (entities that are
     * riding another entity) are silently dropped because their NBT will be included in
     * the bottom-most vehicle's data when that vehicle is processed.
     */
    @Redirect(
            method = "summonEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/entity/EntityPlayerSP;sendChatMessage(Ljava/lang/String;)V",
                    remap = true
            ),
            remap = false
    )
    private void useCustomPacketToPasteEntityNbt(EntityPlayerSP player, String originalCommand)
    {
        if (ClientNetworkHandler.isServerPasterAvailable() && this.currentEntity != null)
        {
            if (this.currentEntity.getRidingEntity() != null)
            {
                // This entity is a passenger; its NBT will be included in the vehicle.
                // Drop this command entirely — the vehicle summon will cover it.
                return;
            }

            NBTTagCompound tag = new NBTTagCompound();
            this.currentEntity.writeToNBT(tag);

            // Remove fields that should not be copied to a fresh summon
            tag.removeTag("UUIDMost");
            tag.removeTag("UUIDLeast");
            tag.removeTag("Pos");
            tag.removeTag("Dimension");

            String tagString = tag.toString();
            String command = originalCommand + " " + tagString;

            if (ClientNetworkHandler.canSendCommand(command))
            {
                LiteModLmsPaster.LOGGER.info(
                        "Summoning entity {} with NBT tag",
                        this.currentEntity.getClass().getSimpleName());
                ClientNetworkHandler.sendCommand(command);
                return;
            }
        }
        // Fall back to vanilla chat
        player.sendChatMessage(originalCommand);
    }
}
