package com.almightyalpaca.discord.bot.plugin.eval;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.codehaus.groovy.jsr223.GroovyScriptEngineImpl;
import org.jruby.embed.jsr223.JRubyEngineFactory;
import org.luaj.vm2.script.LuaScriptEngine;
import org.python.jsr223.PyScriptEngineFactory;

import javaxtools.compiler.CharSequenceCompiler;
import javaxtools.compiler.CharSequenceCompilerException;

public enum Engine {

	JAVASCRIPT("JavaScript", "js", "javascript") {
		private final ScriptEngineManager engineManager = new ScriptEngineManager();

		@Override
		public Triple<Object, String, String> eval(final Map<String, Object> shortcuts, final int timeout, String script) {
			script = " (function() { with (imports) {" + script + "} })();";
			return this.eval(shortcuts, timeout, script, this.engineManager.getEngineByName("nashorn"), engine -> {
				try {
					engine.eval("var imports = new JavaImporter(java.io, java.lang, java.util, java.util.concurrent, java.time);");
				} catch (final ScriptException e) {}
			});
		}
	},
	GROOVY("Groovy", "groovy") {
		@Override
		public Triple<Object, String, String> eval(final Map<String, Object> shortcuts, final int timeout, final String script) {
			return this.eval(shortcuts, timeout, script, new GroovyScriptEngineImpl());
		}
	},
	RUBY("Ruby", "ruby", "jruby") {
		private final JRubyEngineFactory factory = new JRubyEngineFactory();

		@Override
		public Triple<Object, String, String> eval(final Map<String, Object> shortcuts, final int timeout, final String script) {
			return this.eval(shortcuts, timeout, script, this.factory.getScriptEngine());
		}
	},
	PYTHON("Python", "python", "jython") {
		private final PyScriptEngineFactory factory = new PyScriptEngineFactory();

		@Override
		public Triple<Object, String, String> eval(final Map<String, Object> shortcuts, final int timeout, final String script) {
			return this.eval(shortcuts, timeout, script, this.factory.getScriptEngine());
		}
	},
	LUA("Lua", "lua", "luaj") {
		@Override
		public Triple<Object, String, String> eval(final Map<String, Object> shortcuts, final int timeout, final String script) {
			return this.eval(shortcuts, timeout, script, new LuaScriptEngine());
		}
	},
	JAVA("Java", "java") {

		private final String	begin		= "package eval;\npublic class EvalClass {\npublic EvalClass() {}\n";
		private final String	fields		= "public java.io.PrintWriter out;\npublic java.io.PrintWriter err;";
		private final String	methods		= "public <T> T print(T o) {\n		out.println(String.valueOf(o));\n		return o;\n	}\n\n	public <T> T printErr(T o) {\n		err.println(String.valueOf(o));\n		return o;\n	}";
		private final String	methodBegin	= "public Object run()\n{\n";
		private final String	methodEnd	= "\n}\n";
		private final String	end			= "public void finalize(){java.lang.System.out.println(\"FINALIZING EVALCLASS\");}}";

		@Override
		public Triple<Object, String, String> eval(final Map<String, Object> shortcuts, final int timeout, final String script) {
			final CharSequenceCompiler<Object> compiler = new CharSequenceCompiler<>(this.getClass().getClassLoader(), null);

			String code = "";
			code += this.begin;
			code += this.fields;
			for (final Entry<String, Object> shortcut : shortcuts.entrySet()) {
				code += "public " + shortcut.getValue().getClass().getName() + " " + shortcut.getKey() + ";\n";
			}
			code += this.methods;
			code += this.methodBegin;
			code += script;
			code += this.methodEnd;
			code += this.end;

			final String finalScript = code;

			final StringWriter outString = new StringWriter();
			final PrintWriter outWriter = new PrintWriter(outString);

			final StringWriter errorString = new StringWriter();
			final PrintWriter errorWriter = new PrintWriter(errorString);

			Object result = null;

			try {
				final Class<Object> clazz = compiler.compile("eval.EvalClass", finalScript, null);
				final Object object = clazz.newInstance();
				for (final Entry<String, Object> shortcut : shortcuts.entrySet()) {
					try {
						final Field field = clazz.getDeclaredField(shortcut.getKey());
						field.setAccessible(true);
						field.set(object, shortcut.getValue());
					} catch (NoSuchFieldException | SecurityException e) {
						e.printStackTrace(errorWriter);
					}
				}

				final Field fieldErrorWriter = clazz.getDeclaredField("err");
				fieldErrorWriter.setAccessible(true);
				fieldErrorWriter.set(object, errorWriter);

				final Field fieldOutWriter = clazz.getDeclaredField("out");
				fieldOutWriter.setAccessible(true);
				fieldOutWriter.set(object, outWriter);

				final Method method = clazz.getDeclaredMethod("run");
				method.setAccessible(true);

				final ScheduledFuture<Object> future = Engine.service.schedule(() -> {
					return method.invoke(object);
				}, 0, TimeUnit.MILLISECONDS);

				try {
					result = future.get(timeout, TimeUnit.SECONDS);
				} catch (final ExecutionException e) {
					errorWriter.println(e.getCause().toString());
				} catch (TimeoutException | InterruptedException e) {
					future.cancel(true);
					errorWriter.println(e.toString());
				}
			} catch (ClassCastException | CharSequenceCompilerException | InstantiationException | IllegalAccessException | SecurityException | IllegalArgumentException | NoSuchFieldException
					| NoSuchMethodException e) {
				e.printStackTrace(errorWriter);
			}
			return new ImmutableTriple<Object, String, String>(result, outString.toString(), errorString.toString());
		}
	};

	private final static ScheduledExecutorService	service	= Executors.newScheduledThreadPool(1, r -> new Thread(r, "Eval-Thread"));

	private final List<String>						codes;

	private final String							name;

	private Engine(final String name, final String... codes) {
		this.name = name;
		this.codes = new ArrayList<>();
		for (final String code : codes) {
			this.codes.add(code.toLowerCase());
		}
	}

	public static Engine getEngineByCode(String code) {
		code = code.toLowerCase();
		for (final Engine engine : Engine.values()) {
			if (engine.codes.contains(code)) {
				return engine;
			}
		}
		return null;
	}

	public static void shutdown() {
		Engine.service.shutdownNow();
	}

	public abstract Triple<Object, String, String> eval(Map<String, Object> shortcuts, int timeout, String script);

	public Triple<Object, String, String> eval(final Map<String, Object> shortcuts, final int timeout, final String script, final ScriptEngine engine) {
		return this.eval(shortcuts, timeout, script, engine, null);
	}

	public Triple<Object, String, String> eval(final Map<String, Object> shortcuts, final int timeout, final String script, final ScriptEngine engine, final Consumer<ScriptEngine> consumer) {

		for (final Entry<String, Object> shortcut : shortcuts.entrySet()) {
			engine.put(shortcut.getKey(), shortcut.getValue());
		}

		final StringWriter outString = new StringWriter();
		final PrintWriter outWriter = new PrintWriter(outString);
		engine.getContext().setWriter(outWriter);

		final StringWriter errorString = new StringWriter();
		final PrintWriter errorWriter = new PrintWriter(errorString);
		engine.getContext().setErrorWriter(errorWriter);

		if (consumer != null) {
			consumer.accept(engine);
		}

		final ScheduledFuture<Object> future = Engine.service.schedule(() -> {
			return engine.eval(script);
		}, 0, TimeUnit.MILLISECONDS);

		Object result = null;

		try {
			result = future.get(timeout, TimeUnit.SECONDS);
		} catch (final ExecutionException e) {
			errorWriter.println(e.getCause().toString());
		} catch (TimeoutException | InterruptedException e) {
			future.cancel(true);
			errorWriter.println(e.toString());
		}

		return new ImmutableTriple<Object, String, String>(result, outString.toString(), errorString.toString());
	}

	public List<String> getCodes() {
		return this.codes;
	}

	public String getName() {
		return this.name;
	}

}
