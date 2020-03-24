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

import com.github.noonmaru.math.Vector
import com.github.noonmaru.tap.Tap.MATH
import com.github.noonmaru.tap.entity.TapEntity.wrapEntity
import com.github.noonmaru.tap.event.ASMEventExecutor.registerEvents
import org.bukkit.Bukkit.broadcastMessage
import org.bukkit.Bukkit.getScheduler
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Material.AIR
import org.bukkit.Material.getMaterial
import org.bukkit.Particle.REDSTONE
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList.unregisterAll
import org.bukkit.event.Listener
import org.bukkit.event.block.Action.LEFT_CLICK_AIR
import org.bukkit.event.block.Action.LEFT_CLICK_BLOCK
import org.bukkit.event.block.Action.RIGHT_CLICK_AIR
import org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.permissions.Permissible
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files.readAllLines
import java.nio.file.Files.write
import kotlin.random.Random.Default.nextDouble
import kotlin.streams.toList

class VectorPlugin : JavaPlugin(), Listener {
    private var status = false
    private val selectedEntities = HashMap<Player, Entity>()

    private var lastModified: Long? = null
    private var vectorItem = AIR
    private var bothHands = false
    private var singleTime = false
    private var visibilityLength = 0.0
    private var velocityModifier = 0.0
    private var maxVelocity = 0.0

    private fun CommandSender.unrecognizedMessage(message: String, value: String) =
        sendMessage("Unrecognized $message: '$value'")

    private fun String.resetRegexMatch(): Boolean = contains(Regex("(?i)conf|set"))

    private fun List<String>.filter(key: String) = filter { it.startsWith(key, true) }

    private fun getKeys() = config.getKeys(false).toList()

    private fun newRandom(): Double = nextDouble(255.0)

    private fun Permissible.perm(perm: String) = hasPermission(perm)

    private fun statusOn() {
        status = true
        registerEvents(this, this)
        getScheduler().runTaskTimer(this, { selectedEntities.forEach { newParticle(it) } }, 0, 1)
        broadcastMessage("Vector On")
    }

    private fun statusOff() {
        status = false
        unregisterAll(this as JavaPlugin)
        getScheduler().cancelTasks(this)
        broadcastMessage("Vector Off")
    }

    private fun Player.getTarget(): Location {
        val loc = eyeLocation.clone()
        val view = loc.clone().add(loc.clone().direction.normalize().multiply(visibilityLength))
        val block =
            MATH.rayTraceBlock(loc.world, Vector(loc.x, loc.y, loc.z), Vector(view.x, view.y, view.z), 0)
                ?: return loc.clone().add(eyeLocation.direction.clone().normalize().multiply(visibilityLength))
        block.blockPoint.let {
            return loc.world.getBlockAt(it.x, it.y, it.z).getRelative(block.face).location.add(0.5, 0.5, 0.5)
        }
    }

    private fun configCommand(args: Array<out String>, sender: CommandSender) = when (args.size) {
        1 -> sender.sendMessage("Required: key, value")
        2 -> when {
            args[1].contains("reset", true) -> {
                File(dataFolder, "config.yml").delete()
                saveDefaultConfig()
            }
            getKeys().contains(args[1]) -> getCurrentConfig(args, sender)
            else -> sender.unrecognizedMessage("key", args[1])
        }
        3 -> when {
            getKeys().contains(args[1]) -> setConfig(args, sender)
            else -> sender.unrecognizedMessage("Unrecognized key", args[1])
        } else -> sender.unrecognizedMessage("args", args.drop(3).toString())
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage("===== Command <vector> =====\n" +
                "/vector -> Toggles vector feature\n" +
                "/vector help -> Shows vector help\n" +
                "/vector config <key|reset> [value] -> Updates plugin.yml\n\n\n")
    }

    private fun getCurrentConfig(args: Array<out String>, sender: CommandSender) {
        try {
            val path = File(dataFolder, "config.yml").toPath()
            val lines = readAllLines(path, UTF_8)

            for (i in 0 until lines.count())
                if (lines[i].contains(args[1]))
                    sender.sendMessage("\n \n \n" +
                            "${lines[i - 3].substring(2)}\n" +
                            "${lines[i - 2].substring(2)}\n" +
                            "${lines[i - 1].substring(2)}\n \n" +
                            "Current ${args[1]}: ${config.get(args[1])}\n")
        } catch (e: IOException) { logger.info("Cannot read/write to config.yml") }
    }

    private fun setConfig(args: Array<out String>, sender: CommandSender) {
        try {
            val path = File(dataFolder, "config.yml").toPath()
            val lines = readAllLines(path, UTF_8)
            for (i in 0 until lines.count()) {
                if (lines[i].contains(args[1])) when {
                    args[1].contains("double") -> lines[i] = "${args[1]}: ${args[2].toDouble()}"
                    args[1].contains("item") -> {
                        getMaterial(args[2].toUpperCase())?: sender.unrecognizedMessage("key", args[2])
                        lines[i] = "${args[1]}: ${args[2].toUpperCase()}"
                    }
                    args[2].matches(Regex("true|false")) -> lines[i] = "${args[1]}: ${args[2]}"
                    else -> sender.unrecognizedMessage("value", args[2])
                }
            }
            write(path, lines, UTF_8)
        } catch (e: Exception) {
            when (e) {
                is IOException -> logger.info("Cannot read/write to config.yml")
                is NumberFormatException -> sender.unrecognizedMessage("value", args[2])
            }
        }
    }

    private fun newParticle(entry: Map.Entry<Player, Entity>) {
        val pos = entry.value.location.clone()
        if (!entry.key.isValid || !entry.value.isValid) selectedEntities.remove(entry.key)
        else entry.key.let { player ->
            player.getTarget().let { target ->
                for (i in 0 until pos.distance(target).times(5).toInt()) {
                    val loc =
                        pos.add(target.toVector().clone().subtract(pos.toVector()).normalize().multiply(0.2))
                    pos.world.spawnParticle(REDSTONE, loc, 0, newRandom(), newRandom(), newRandom())
                }
            }
        }
    }

    private fun setTargetVelocity(player: Player, remove: Boolean): Boolean? {
        selectedEntities[player]?.let {
            val vector = player.getTarget().subtract(it.location).toVector()
            it.velocity = if (vector.length() < maxVelocity / velocityModifier) vector.multiply(velocityModifier) else
                vector.normalize().multiply(maxVelocity)
            if (remove) selectedEntities.remove(player)
            return true
        }
        return null
    }

    private fun newRayTrace(player: Player) {
        val loc = player.eyeLocation
        val view = loc.clone().add(loc.clone().direction.normalize().multiply(20.0))
        var found: Entity? = null
        var distance = 0.0

        player.world.entities?.forEach { entity ->
            if (entity != player)
                wrapEntity(entity)?.boundingBox?.expand(5.0)
                    ?.calculateRayTrace(Vector(loc.x, loc.y, loc.z), Vector(view.x, view.y, view.z))?.let {
                        val currentDistance = loc.distance(entity.location)
                        if (currentDistance < distance || distance == 0.0) {
                            distance = currentDistance
                            found = entity
                        }
                    }
        }
        found?.let { selectedEntities[player] = it }
    }

    override fun onEnable() {
        saveDefaultConfig()
        getScheduler().runTaskTimer(this, {
            val file = File(dataFolder, "config.yml")
            val last = file.lastModified()
            if (last != lastModified) {
                lastModified = last
                reloadConfig()
                vectorItem = getMaterial(config.getString("vector-item"))
                bothHands = config.getBoolean("use-both-hands")
                singleTime = config.getBoolean("set-single-time")
                visibilityLength = config.getDouble("visibility-length-double")
                velocityModifier = config.getDouble("velocity-modifier-double")
                maxVelocity = config.getDouble("max-velocity-double")
            }
        }, 0, 1)
    }

    override fun onCommand(sender: CommandSender, command: Command?, label: String?, args: Array<out String>): Boolean {
        if (args.isNotEmpty()) when {
            args[0].contains("help", true) -> sendHelp(sender)
            args[0].resetRegexMatch() && sender.perm("command.vector.config") -> configCommand(args, sender)
            else -> sender.unrecognizedMessage("args", args[0])
        } else if (sender.perm("command.vector.toggle")) if (!status) statusOn() else statusOff()
        return true
    }

    override fun onTabComplete(sender: CommandSender?, command: Command?, alias: String?, args: Array<out String>) =
        when (args.size) {
            1 -> listOf("config", "help").filter(args[0])
            2 -> if (args[0].resetRegexMatch()) getKeys().filter(args[1]) else emptyList()
            3 -> if (args[0].resetRegexMatch() && getKeys().contains(args[1])) when {
                args[1].contains("item", true) ->
                    Material.values().toList().stream().map(Material::name).toList().filter(args[2])
                !args[1].contains("double", true) -> listOf("true", "false").filter(args[2])
                else -> emptyList()
            } else emptyList()
            else -> emptyList()
        }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        val action = event.action
        if (event.item?.type == vectorItem && player.perm("command.vector.use")) {
            if (setOf(RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK).contains(action)) {
                if (!bothHands) setTargetVelocity(player, singleTime) ?: newRayTrace(player)
                else newRayTrace(player)
            }
            if (setOf(LEFT_CLICK_AIR, LEFT_CLICK_BLOCK).contains(action) && bothHands)
                setTargetVelocity(player,singleTime)
            event.isCancelled = true
        }
    }
}