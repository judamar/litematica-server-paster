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

package me.fallenbreath.lmspaster.utils;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Null-safe accessors for common {@link NBTTagCompound} value types.
 */
public class NbtUtils
{
    /**
     * Returns the string value associated with {@code key}, or an empty string if the
     * key does not exist or is of a different type.
     */
    public static String getStringOrEmpty(NBTTagCompound nbt, String key)
    {
        return nbt.hasKey(key, 8 /* TAG_String */) ? nbt.getString(key) : "";
    }

    /**
     * Returns the {@code int[]} value associated with {@code key}, or an empty array if
     * the key does not exist or is of a different type.
     */
    public static int[] getIntArrayOrEmpty(NBTTagCompound nbt, String key)
    {
        return nbt.hasKey(key, 11 /* TAG_Int_Array */) ? nbt.getIntArray(key) : new int[0];
    }
}
