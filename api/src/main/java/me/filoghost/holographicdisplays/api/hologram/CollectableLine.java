/*
 * Copyright (C) filoghost and contributors
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package me.filoghost.holographicdisplays.api.hologram;

import org.jetbrains.annotations.Nullable;

/**
 * A hologram line that can be "picked up" by a nearby player.
 *
 * @since 1
 */
public interface CollectableLine extends HologramLine {

    /**
     * Sets the pickup listener.
     *
     * @param pickupListener the new pickup listener
     * @since 1
     */
    void setPickupListener(@Nullable PickupListener pickupListener);

    /**
     * Returns the current pickup listener.
     *
     * @return the current pickup listener
     * @since 1
     */
    @Nullable PickupListener getPickupListener();

}
