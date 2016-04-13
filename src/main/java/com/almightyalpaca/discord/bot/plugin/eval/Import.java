package com.almightyalpaca.discord.bot.plugin.eval;

public class Import {

	public static enum Type {
		CLASS, PACKAGE;
	}

	private final Type		type;
	private final String	name;

	public Import(final Import.Type type, final String name) {
		this.type = type;
		this.name = name;
	}

	public final String getName() {
		return this.name;
	}

	public final Type getType() {
		return this.type;
	}

}
