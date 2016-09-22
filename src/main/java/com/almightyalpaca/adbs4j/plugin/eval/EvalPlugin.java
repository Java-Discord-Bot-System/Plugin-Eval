package com.almightyalpaca.adbs4j.plugin.eval;

import java.util.Collection;

import com.almightyalpaca.adbs4j.exception.PluginLoadingException;
import com.almightyalpaca.adbs4j.exception.PluginUnloadingException;
import com.almightyalpaca.adbs4j.plugins.Plugin;
import com.almightyalpaca.adbs4j.plugins.PluginInfo;
import com.almightyalpaca.adbs4j.storage.CachedStorageProviderInstance;

public class EvalPlugin extends Plugin {

	private static final PluginInfo			INFO	= new PluginInfo("com.almightyalpaca.adbs4j.plugin.eval", "1.0.0", "Almighty Alpaca", "Eval",
			"Debugging, debugging debugging aaaaand....  DEBUGGING!!!");

	private CachedStorageProviderInstance	storage;

	public EvalPlugin() {
		super(EvalPlugin.INFO);
	}

	public Collection<String> getClassImports() {
		return this.storage.getSet(300, "imports.classes");
	}

	public Collection<String> getPackageImports() {
		return this.storage.getSet(300, "imports.package");
	}

	int getTimeout() {
		final String s = EvalPlugin.this.storage.getString(500, "timeout");
		try {
			return Integer.parseInt(s);
		} catch (final Exception e) {
			this.storage.putString("timeout", "10");
			return 10;
		}
	}

	@Override
	public void load() throws PluginLoadingException {
		this.storage = this.getPluginStorageProvider().cached();

		final Collection<String> packages = this.getPackageImports();
		if (packages.isEmpty()) {
			this.storage.setAdd("imports.package", "java.lang");
		}

		final Collection<String> classes = this.getClassImports();
		if (classes.isEmpty()) {
			this.storage.setAdd("imports.classes", "java.lang.System");
		}

		final String s = EvalPlugin.this.storage.getString(500, "timeout");
		try {
			Integer.parseInt(s);
		} catch (final Exception e) {
			this.storage.putString("timeout", "10");
		}

		this.registerCommand(new EvalCommand(this));
	}

	@Override
	public void unload() throws PluginUnloadingException {
		Engine.shutdown();
	}

}
