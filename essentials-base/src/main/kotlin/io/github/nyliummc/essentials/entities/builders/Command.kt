package io.github.nyliummc.essentials.entities.builders

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.github.nyliummc.essentials.api.builders.Command as ICommandBuilder
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import java.util.concurrent.CompletableFuture

object Command : ICommandBuilder {
    class Builder : ICommandBuilder.Builder {
        private lateinit var dispatcher: CommandDispatcher<ServerCommandSource>

        private var command: LiteralArgumentBuilder<ServerCommandSource>? = null
        private var argument: ArgumentBuilder<ServerCommandSource, *>? = null

        override fun command(vararg names: String, builder: () -> Unit) {
            val base = names.first()
            val aliases = names.filter { it != base }
            val baseCommand = CommandManager.literal(base)
            this.command = baseCommand
            builder()
            val baseNode = dispatcher.register(baseCommand)
            aliases.forEach {
                var node = CommandManager.literal(it)
                node = node.redirect(baseNode)
                dispatcher.register(node)
            }
            this.command = null
        }

        override fun requires(checkFunction: (ServerCommandSource) -> Boolean) {
            val parentIsArgument = (this.argument != null)
            var obj = this.argument ?: this.command
            obj = obj!!.requires(checkFunction) as ArgumentBuilder<ServerCommandSource, *>
            if (parentIsArgument) {
                argument = obj
            } else {
                command = obj as LiteralArgumentBuilder<ServerCommandSource>
            }
        }

        override fun suggests(callback: (CommandContext<ServerCommandSource>, SuggestionsBuilder) -> CompletableFuture<Suggestions>) {
            this.argument = (this.argument!! as RequiredArgumentBuilder<ServerCommandSource, *>).suggests(callback)
        }

        private fun argumentNode(node: ArgumentBuilder<ServerCommandSource, *>, builder: () -> Unit) {
            val parentIsArgument = (this.argument != null)
            val oldArg = this.argument
            var obj = if (parentIsArgument) this.argument else this.command
            this.argument = node
            builder()
            obj = obj!!.then(node) as ArgumentBuilder<ServerCommandSource, *>
            if (!parentIsArgument) {
                this.command = obj as LiteralArgumentBuilder<ServerCommandSource>
            } else {
                this.argument = obj
            }
            this.argument = oldArg
        }

        override fun argument(name: String, type: ArgumentType<*>, builder: () -> Unit) {
            val arg = CommandManager.argument(name, type)
            argumentNode(arg, builder)
        }

        override fun argument(literal: String, builder: () -> Unit) {
            val arg = CommandManager.literal(literal)
            argumentNode(arg, builder)
        }

        override fun executes(callback: (CommandContext<ServerCommandSource>) -> Int) {
            val parentIsArgument = (this.argument != null)
            var item = argument ?: command
            item = item?.executes(callback) as ArgumentBuilder<ServerCommandSource, *>
            if (parentIsArgument) {
                argument = item
            } else {
                command = item as LiteralArgumentBuilder<ServerCommandSource>
            }
        }

        override fun setDispatcher(dispatcher: CommandDispatcher<ServerCommandSource>) {
            this.dispatcher = dispatcher
        }
    }
}