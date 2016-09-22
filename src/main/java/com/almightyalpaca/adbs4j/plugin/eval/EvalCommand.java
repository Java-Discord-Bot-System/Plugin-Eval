package com.almightyalpaca.adbs4j.plugin.eval;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Triple;

import com.almightyalpaca.adbs4j.command.Command;
import com.almightyalpaca.adbs4j.command.CommandHandler;
import com.almightyalpaca.adbs4j.command.ExecutionRequirement;
import com.almightyalpaca.adbs4j.command.arguments.Text;
import com.almightyalpaca.adbs4j.events.commands.CommandEvent;
import com.almightyalpaca.adbs4j.jda.AdvancedMessageBuilder;
import com.almightyalpaca.adbs4j.jda.AdvancedMessageBuilder.Formatting;

public class EvalCommand extends Command {

	private final EvalPlugin plugin;

	public EvalCommand(final EvalPlugin evalPlugin) {
		super("eval", "admin", "<engine> <script>", "Evaluate scripts in various languages. Type `{$prefix}eval list` to get an overview of supported languages.");
		this.plugin = evalPlugin;
		this.addRequirements(ExecutionRequirement.USER_IS_BOT_ADMIN);
	}

	@CommandHandler(dm = true, guild = true)
	public void onCommand(final CommandEvent event, final String list) {
		if (list.equalsIgnoreCase("list")) {
			final AdvancedMessageBuilder builder = new AdvancedMessageBuilder();
			builder.appendString("List of supported scripting lanugages:", Formatting.BOLD).newLine();
			for (final Engine engine : Engine.values()) {
				builder.appendString(engine.getName() + "\n");
			}
			event.sendMessage(builder, null);
		}
	}

	@CommandHandler(dm = true, guild = true)
	public void onCommand(final CommandEvent event, final String lang, final String script) {
		final AdvancedMessageBuilder builder = new AdvancedMessageBuilder();

		final Engine engine = Engine.getEngineByCode(lang);
		if (engine != null) {
			// Execute code
			final Map<String, Object> shortcuts = new HashMap<>();

			shortcuts.put("api", event.getJDA());
			shortcuts.put("jda", event.getJDA());
			shortcuts.put("event", event);
			if (event.isPrivate()) {
				shortcuts.put("channel", event.getPrivateChannel());
			} else {
				shortcuts.put("channel", event.getTextChannel());
				shortcuts.put("server", event.getTextChannel().getGuild());
				shortcuts.put("guild", event.getTextChannel().getGuild());
			}

			shortcuts.put("message", event.getMessage());
			shortcuts.put("msg", event.getMessage());
			shortcuts.put("me", event.getAuthor());
			shortcuts.put("bot", event.getJDA().getSelfInfo());

			final int timeout = this.plugin.getTimeout();

			final Triple<Object, String, String> result = engine.eval(shortcuts, this.plugin.getClassImports(), this.plugin.getPackageImports(), timeout, script);

			if (result.getLeft() != null) {
				builder.appendCodeBlock(result.getLeft().toString(), "");
			}
			if (!result.getMiddle().isEmpty()) {
				builder.ensureNewLine().appendCodeBlock(result.getMiddle(), "");
			}
			if (!result.getRight().isEmpty()) {
				builder.ensureNewLine().appendCodeBlock(result.getRight(), "");
			}

			if (builder.getLength() == 0) {
				builder.appendString("✅");
			}

		} else {
			// Engine name invalid
			builder.appendString("Invalid script engine.", Formatting.BOLD);
		}
		event.sendMessage(builder, null);
	}

	@CommandHandler(dm = true, guild = true)
	public void onCommand(final CommandEvent event, final String engineName, final Text script) {
		this.onCommand(event, engineName, script.toString());
	}

	@CommandHandler(dm = true, guild = true, priority = 1)
	public void onCommand(final CommandEvent event, final String engineName, final URL scriptURL) {
		try {
			final String script = IOUtils.toString(scriptURL.openStream(), "UTF-8");
			this.onCommand(event, engineName, script);
		} catch (final IOException e) {
			e.printStackTrace();
			event.sendMessage(new AdvancedMessageBuilder().appendString("An unexpected error occured!", Formatting.BOLD), null);
		}
	}
}