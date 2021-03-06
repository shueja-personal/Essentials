/*
 * MIT License
 *
 * Copyright (c) NyliumMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.github.nyliummc.essentials

import io.github.nyliummc.essentials.api.EssentialsMod
import io.github.nyliummc.essentials.api.EssentialsModule
import io.github.nyliummc.essentials.commands.*
import io.github.nyliummc.essentials.configs.UtilitiesConfig
import io.github.nyliummc.essentials.mixin.cast.SleepSetter
import io.github.nyliummc.essentials.entities.TPSTracker
import io.github.nyliummc.essentials.ext.precision
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.LiteralText
import net.minecraft.util.Util
import net.minecraft.world.World

import kotlin.streams.toList


class EssentialsUtilitiesModule : EssentialsModule {
    override val name = "utilities"
    override val toggleable = true
    val essentials: EssentialsMod = EssentialsMod.instance

    val sleeping = mutableListOf<ServerPlayerEntity>()


    override fun registerCommands() {
        essentials.registry.registerCommand(FlightCommand::register)
        essentials.registry.registerCommand(GodCommand::register)
        essentials.registry.registerCommand(HatCommand::register)
        essentials.registry.registerCommand(HeadCommand::register)
        essentials.registry.registerCommand(HealCommand::register)
        essentials.registry.registerCommand(SpeedCommand::register)
        essentials.registry.registerCommand(TPSCommand::register)
    }

    override fun registerConfigs() {
        essentials.registry.registerConfig("essentials-utilities.yaml", UtilitiesConfig::class.java, "essentials-utilities.yaml")
    }

    override fun registerEvents() {
        ServerTickEvents.END_WORLD_TICK.register(ServerTickEvents.EndWorldTick { world ->
            getTracker(world.registryKey.value.path).tick()
        })

        // Skip night by percentage
        ServerTickEvents.END_WORLD_TICK.register(ServerTickEvents.EndWorldTick { world ->
            if (world.isClient || world !== world.server.getWorld(World.OVERWORLD)) {
                return@EndWorldTick
            }

            val players = world.players
            val treshold = essentials.registry.getConfig(UtilitiesConfig::class.java).sleepPercentage

            players.removeIf { obj: PlayerEntity -> obj.isSpectator }

            if (players.isEmpty()) {
                (world as SleepSetter).setSleeping(false)
                return@EndWorldTick
            }

            val total = players.size.toDouble()
            val sleepingPlayers = players.stream().filter { it.isSleepingLongEnough }.toList()

            val sleepingAmount = sleepingPlayers.count().toDouble()
            val percentage = sleepingAmount / total
            val shouldSkip = percentage >= treshold

            sleepingPlayers.filter { !sleeping.contains(it) }.forEach {
                sleeping.add(it as ServerPlayerEntity)
                world.server.sendSystemMessage(
                        LiteralText("${it.displayName.asString()} is now sleeping. (${percentage.precision(2)}, ${"%.2f".format(treshold)} needed)"), Util.NIL_UUID)
            }

            (world as SleepSetter).setSleeping(shouldSkip)
            if (shouldSkip) {
                // world.server.playerManager.sendToAll(LiteralText("Good morning!"))
                sleeping.clear()
            }
        })
    }

    companion object {
        val tpsTrackers = mutableMapOf<String, TPSTracker>()

        @JvmStatic
        fun getTracker(name: String): TPSTracker {
            var c = tpsTrackers[name]
            if (c == null) {
                c = TPSTracker(name)
                tpsTrackers[name] = c
            }
            return c
        }
    }

}
