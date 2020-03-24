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

import com.github.noonmaru.tap.event.ASMEventExecutor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.event.HandlerList
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import kotlin.streams.toList

class VectorCommand(private val instance: VectorPlugin): CommandExecutor, TabCompleter {
    private var status = false

    private val dataFolder = instance.dataFolder
    private val config = instance.config
    private val logger = instance.logger

    override fun onCommand(sender: CommandSender, command: Command?, label: String?, args: Array<out String>): Boolean {
        if (args.isNotEmpty()) when {
            args[0].contains("help", true) -> sendHelp(sender)
            args[0].resetRegexMatch() && sender.hasPermission("command.vector.config") -> configCommand(args, sender)
            else -> sender.unrecognizedMessage("args", args[0])
        } else if (sender.hasPermission("command.vector.toggle")) if (!status) statusOn() else statusOff()
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

    private fun statusOn() {
        status = true
        ASMEventExecutor.registerEvents(VectorEventListener(), instance)
        Bukkit.getScheduler().runTaskTimer(instance, VectorParticleTask(), 0, 1)
        Bukkit.broadcastMessage("Vector On")
    }

    private fun statusOff() {
        status = false
        HandlerList.unregisterAll(instance as JavaPlugin)
        Bukkit.getScheduler().cancelTasks(instance)
        Bukkit.broadcastMessage("Vector Off")
    }

    private fun sendHelp(sender: CommandSender) = sender.sendMessage("\n \n \n" +
            "===== Command <vector> =====\n" +
            "/vector -> Toggles vector feature\n" +
            "/vector help -> Shows vector help\n" +
            "/vector config <key|reset> [value] -> Updates plugin.yml\n")

    private fun configCommand(args: Array<out String>, sender: CommandSender) = when (args.size) {
        1 -> sender.sendMessage("Required: key, value")
        2 -> when {
            args[1].contains("reset", true) -> {
                File(dataFolder, "config.yml").delete()
                instance.saveDefaultConfig()
            }
            getKeys().contains(args[1]) -> getCurrentConfig(args, sender)
            else -> sender.unrecognizedMessage("key", args[1])
        }
        3 -> when {
            getKeys().contains(args[1]) -> setConfig(args, sender)
            else -> sender.unrecognizedMessage("Unrecognized key", args[1])
        } else -> sender.unrecognizedMessage("args", args.drop(3).toString())
    }

    private fun getCurrentConfig(args: Array<out String>, sender: CommandSender) = try {
        val path = File(dataFolder, "config.yml").toPath()
        val lines = Files.readAllLines(path, UTF_8)

        for (i in 0 until lines.count())
            if (lines[i].contains(args[1]))
                sender.sendMessage("\n \n \n" +
                        "${lines[i - 3].substring(2)}\n" +
                        "${lines[i - 2].substring(2)}\n" +
                        "${lines[i - 1].substring(2)}\n \n" +
                        "Current ${args[1]}: ${config.get(args[1])}\n")
    } catch (e: IOException) { logger.info("Cannot read/write to config.yml") }

    private fun setConfig(args: Array<out String>, sender: CommandSender) {
        try {
            val path = File(dataFolder, "config.yml").toPath()
            val lines = Files.readAllLines(path, UTF_8)
            for (i in 0 until lines.count()) {
                if (lines[i].contains(args[1])) when {
                    args[1].contains("double") -> lines[i] = "${args[1]}: ${args[2].toDouble()}"
                    args[1].contains("item") -> {
                        Material.getMaterial(args[2].toUpperCase()) ?: sender.unrecognizedMessage("key", args[2])
                        lines[i] = "${args[1]}: ${args[2].toUpperCase()}"
                    }
                    args[2].matches(Regex("true|false")) -> lines[i] = "${args[1]}: ${args[2]}"
                    else -> sender.unrecognizedMessage("value", args[2]).also { return }
                }
                sender.sendMessage(lines[i])
            }
            Files.write(path, lines, UTF_8)
        } catch (e: Exception) {
            when (e) {
                is IOException -> logger.info("Cannot read/write to config.yml")
                is NumberFormatException -> sender.unrecognizedMessage("value", args[2])
            }
        }
    }

    private fun String.resetRegexMatch() = contains(Regex("(?i)conf|set"))

    private fun List<String>.filter(key: String) = filter { it.startsWith(key, true) }

    private fun CommandSender.unrecognizedMessage(message: String, value: String) =
        sendMessage("Unrecognized $message: '$value'")

    private fun getKeys() = config.getKeys(false).toList()
}