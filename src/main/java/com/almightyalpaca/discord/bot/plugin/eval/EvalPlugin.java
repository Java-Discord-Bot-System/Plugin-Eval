package com.almightyalpaca.discord.bot.plugin.eval;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Triple;

import com.almightyalpaca.discord.bot.system.command.Command;
import com.almightyalpaca.discord.bot.system.command.CommandHandler;
import com.almightyalpaca.discord.bot.system.command.arguments.special.Rest;
import com.almightyalpaca.discord.bot.system.config.Config;
import com.almightyalpaca.discord.bot.system.events.commands.CommandEvent;
import com.almightyalpaca.discord.bot.system.exception.PluginLoadingException;
import com.almightyalpaca.discord.bot.system.exception.PluginUnloadingException;
import com.almightyalpaca.discord.bot.system.plugins.Plugin;
import com.almightyalpaca.discord.bot.system.plugins.PluginInfo;

import net.dv8tion.jda.MessageBuilder;
import net.dv8tion.jda.MessageBuilder.Formatting;

public class EvalPlugin extends Plugin {

	class EvalCommand extends Command {

		public EvalCommand() {
			super("eval", "Debugging, debugging debugging aaaaand....  DEBUGGING!!!", "[engine] [script/script url]");
		}

		@CommandHandler(dm = true, guild = true, async = true)
		public void onCommand(final CommandEvent event, final String list) {
			if (list.equalsIgnoreCase("list")) {
				final MessageBuilder builder = new MessageBuilder();
				builder.appendString("List of supported scripting lanugages:", Formatting.BOLD).newLine();
				for (final Engine engine : Engine.values()) {
					builder.appendString(engine.getName() + "\n");
				}
				event.sendMessage(builder);
			}
		}

		@CommandHandler(dm = true, guild = true, async = true)
		public void onCommand(final CommandEvent event, final String engineName, final Rest script) {
			this.onCommand(event, engineName, script.getString());
		}

		@CommandHandler(dm = true, guild = true, async = true)
		public void onCommand(final CommandEvent event, final String engineName, final String script) {
			final MessageBuilder builder = new MessageBuilder();

			final Engine engine = Engine.getEngineByCode(engineName);
			if (engine != null) {
				// Execute code

				final Map<String, Object> shortcuts = new HashMap<>();

				shortcuts.put("api", event.getJDA());
				shortcuts.put("event", event);
				if (event.isPrivate()) {
					shortcuts.put("channel", event.getPrivateChannel());
				} else {
					shortcuts.put("channel", event.getTextChannel());
					shortcuts.put("server", event.getTextChannel().getGuild());
					shortcuts.put("guild", event.getTextChannel().getGuild());
				}
				shortcuts.put("mesage", event.getMessage());
				shortcuts.put("me", event.getAuthor());
				shortcuts.put("bot", event.getJDA().getSelfInfo());

				final Triple<Object, String, String> result = engine.eval(shortcuts, EvalPlugin.this.config.getInt("timeout", 5), script);

				if (result.getRight().length() > 0) {
					builder.appendString("Executed with errors:", Formatting.BOLD).newLine().appendCodeBlock(result.getRight(), "");
				} else {
					builder.appendString("Executed without errors!", Formatting.BOLD);
				}
				if (result.getMiddle().length() > 0) {
					builder.newLine().appendString("Output:", Formatting.BOLD).newLine().appendString(result.getMiddle());
				}
				if (result.getLeft() != null) {
					builder.newLine().appendString("Result:", Formatting.BOLD).newLine().appendString(result.getLeft().toString());
				}
			} else {
				// Engine name invalid
				builder.appendString("Invalid script engine.", Formatting.BOLD);
			}
			event.sendMessage(builder);
		}

		@CommandHandler(dm = true, guild = true, priority = 1, async = true)
		public void onCommand(final CommandEvent event, final String engineName, final URL scriptURL) {
			try {
				final String script = IOUtils.toString(scriptURL.openStream(), "UTF-8");
				this.onCommand(event, engineName, script);
			} catch (final IOException e) {
				e.printStackTrace();
				event.sendMessage(new MessageBuilder().appendString("An unexpected error occured!", Formatting.BOLD).build());
			}
		}
	}

	private static final PluginInfo INFO = new PluginInfo("com.almightyalpaca.discord.bot.plugin.eval", "1.0.0", "Almighty Alpaca", "Eval Plugin",
		"Debugging, debugging debugging aaaaand....  DEBUGGING!!!");

	private Config config;

	public EvalPlugin() {
		super(EvalPlugin.INFO);
	}

	@Override
	public void load() throws PluginLoadingException {

		this.config = this.getPluginConfig();

		this.registerCommand(new EvalCommand());
	}

	@Override
	public void unload() throws PluginUnloadingException {
		Engine.shutdown();
	}

}
