/*
 * Copyright (C) 2020 PatrickKR
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * Contact me on <mailpatrickkr@gmail.com>
 */

package com.github.patrick.vector

import com.github.patrick.vector.VectorPlugin.Companion.selectedEntities
import com.github.patrick.vector.VectorUtils.getTarget
import com.github.patrick.vector.VectorUtils.isLegacyVersion
import com.github.patrick.vector.VectorUtils.isModernVersion
import org.bukkit.Color.fromRGB
import org.bukkit.Particle.DustOptions
import org.bukkit.Particle.REDSTONE
import kotlin.random.Random.Default.nextInt

/**
 * This class manages the particle effect of this plugin.
 * When the player has a selected entity, this task will spawn the
 * particle between the player's eye target and the entity.
 */
class VectorParticleTask : Runnable {
    /**
     * This overridden method will run if the vector plugin's status
     * is on (true).
     */
    override fun run() {
        selectedEntities.forEach {
            val pos = it.value.location.clone()
            if (!it.key.isValid || !it.value.isValid) selectedEntities.remove(it.key)
            val target = it.key.getTarget()
            for (i in 0 until pos.distance(target).times(5).toInt()) {
                val loc =
                    pos.add(target.toVector().clone().subtract(pos.toVector()).normalize().multiply(0.2))
                when {
                    isLegacyVersion() ->
                        pos.world?.spawnParticle(REDSTONE, loc, 0, newDouble(), newDouble(), newDouble())
                    isModernVersion() ->
                        pos.world?.spawnParticle(REDSTONE, loc, 0, 0.0, 0.0, 0.0,
                            DustOptions(fromRGB(newInt(), newInt(), newInt()), 1F)
                        )
                }
            }
        }
    }

    private fun newInt() = nextInt(0, 255)

    private fun newDouble() = nextInt(0, 255).toDouble()
}