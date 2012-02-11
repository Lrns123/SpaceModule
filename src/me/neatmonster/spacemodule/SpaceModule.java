/*
 * This file is part of SpaceModule (http://spacebukkit.xereo.net/).
 * 
 * SpaceModule is free software: you can redistribute it and/or modify it under the terms of the
 * Attribution-NonCommercial-ShareAlike Unported (CC BY-NC-SA) license as published by the Creative
 * Common organization, either version 3.0 of the license, or (at your option) any later version.
 * 
 * SpaceBukkit is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without 
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the 
 * Attribution-NonCommercial-ShareAlike Unported (CC BY-NC-SA) license for more details.
 * 
 * You should have received a copy of the Attribution-NonCommercial-ShareAlike Unported (CC BY-NC-SA)
 * license along with this program. If not, see <http://creativecommons.org/licenses/by-nc-sa/3.0/>.
 */
package me.neatmonster.spacemodule;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;

import me.neatmonster.spacemodule.management.ImprovedClassLoader;
import me.neatmonster.spacemodule.management.VersionsManager;
import me.neatmonster.spacemodule.utilities.Utilities;

import org.bukkit.util.config.Configuration;

import com.drdanick.McRKit.ToolkitAction;
import com.drdanick.McRKit.ToolkitEvent;
import com.drdanick.McRKit.Wrapper;
import com.drdanick.McRKit.module.Module;
import com.drdanick.McRKit.module.ModuleLoader;
import com.drdanick.McRKit.module.ModuleMetadata;

@SuppressWarnings("deprecation")
public class SpaceModule extends Module {
    public static final File   MAIN_DIRECTORY = new File("SpaceModule");
    public static final File   CONFIGURATION  = new File(MAIN_DIRECTORY.getPath(), "configuration.yml");
    public static final File   DATABASE       = new File(MAIN_DIRECTORY.getPath(), "cache.db");

    private static SpaceModule instance;

    public static SpaceModule getInstance() {
        return instance;
    }

    public String              type            = null;
    public boolean             development     = false;
    public boolean             recommended     = false;
    public String              artifactPath    = null;

    public Timer               timer           = new Timer();
    public Object              spaceRTK        = null;
    public ImprovedClassLoader classLoader     = null;
    public VersionsManager     versionsManager = null;

    public SpaceModule(final ModuleMetadata meta, final ModuleLoader moduleLoader, final ClassLoader cLoader) {
        super(meta, moduleLoader, cLoader, ToolkitEvent.ON_TOOLKIT_START, ToolkitEvent.NULL_EVENT);
        instance = this;
        System.out.print("Done.\nLoading SpaceModule...");
    }

    public void execute(final VersionsManager versionsManager, final boolean firstTime) {
        File artifact = null;
        if (type.equals("Bukkit"))
            artifact = new File("plugins", versionsManager.ARTIFACT_NAME);
        if (!artifact.exists()) {
            System.out.print("Done.\nInstalling Space" + type + " #"
                    + (recommended ? versionsManager.RECOMMENDED : versionsManager.DEVELOPMENT) + "...");
            update(versionsManager, artifact, firstTime);
        } else
            try {
                final String md5 = Utilities.getMD5(artifact);
                final int buildNumber = versionsManager.match(md5);
                if (recommended && buildNumber != versionsManager.RECOMMENDED || development
                        && buildNumber != versionsManager.DEVELOPMENT) {
                    System.out.print("Done.\nUpdating Space" + type + " #" + buildNumber + " to #"
                            + (recommended ? versionsManager.RECOMMENDED : versionsManager.DEVELOPMENT) + "...");
                    update(versionsManager, artifact, firstTime);
                } else
                    System.out.print("Done.\n");
            } catch (final Exception e) {
                e.printStackTrace();
            }
        System.out.print("Loading Space" + type + " #"
                + (recommended ? versionsManager.RECOMMENDED : versionsManager.DEVELOPMENT) + "...Done.\n");
        load(artifact);
    }

    private void load(final File jar) {
        try {
            final URL url = new URL("file:" + jar.getAbsolutePath());
            classLoader = new ImprovedClassLoader(new URL[] {url}, getClass().getClassLoader());
            final Class<?> loadedClass = classLoader.loadClass("me.neatmonster.spacertk.SpaceRTK");
            spaceRTK = loadedClass.getConstructor().newInstance();
            final Method onEnable = loadedClass.getMethod("onEnable");
            onEnable.invoke(spaceRTK);
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    private void loadConfiguration() {
        final Configuration configuration = new Configuration(CONFIGURATION);
        configuration.load();
        type = configuration.getString("SpaceModule.Type", "Bukkit");
        configuration.setProperty("SpaceModule.Type", type = "Bukkit");
        recommended = configuration.getBoolean("SpaceModule.Recommended", true);
        development = configuration.getBoolean("SpaceModule.Development", false);
        artifactPath = configuration.getString("SpaceModule.Artifact", "<automatic>");
        if (recommended && development)
            configuration.setProperty("SpaceModule.Recommended", recommended = false);
        configuration.save();
    }

    @Override
    public void onDisable() {
        unload();
        instance = null;
    }

    @Override
    public void onEnable() {
        if (!MAIN_DIRECTORY.exists())
            MAIN_DIRECTORY.mkdir();
        if (!CONFIGURATION.exists())
            try {
                CONFIGURATION.createNewFile();
            } catch (final IOException e) {
                e.printStackTrace();
            }
        loadConfiguration();
        if (recommended || development) {
            System.out.print("Checking for updates...");
            versionsManager = new VersionsManager("Space" + type);
            versionsManager.setup();
            execute(versionsManager, true);
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    System.out.print("Checking for updates...");
                    versionsManager.setup();
                    execute(versionsManager, false);
                }
            }, 21600000L, 21600000L);
        } else {
            final File artifact = new File(artifactPath);
            System.out.print("Loading Space" + type + "...Done.\n");
            load(artifact);
        }
    }

    private void unload() {
        try {
            final Method onDisable = spaceRTK.getClass().getMethod("onDisable");
            onDisable.invoke(spaceRTK);
            spaceRTK = null;
            classLoader.release();
            classLoader = null;
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    private void update(final VersionsManager versionsManager, final File artifact, final boolean firstTime) {
        boolean wasRunning = false;
        if (!firstTime)
            try {
                final Field field = Wrapper.getInstance().getClass().getDeclaredField("serverRunning");
                field.setAccessible(true);
                wasRunning = (Boolean) field.get(Wrapper.getInstance());
                if (wasRunning)
                    Wrapper.getInstance().performAction(ToolkitAction.HOLD, null);
            } catch (final Exception e) {
                e.printStackTrace();
            }
        String url;
        if (recommended)
            url = "http://dev.drdanick.com/jenkins/job/Space" + type + "/Recommended/artifact/target/"
                    + versionsManager.ARTIFACT_NAME;
        else
            url = "http://dev.drdanick.com/jenkins/job/Space" + type + "/lastStableBuild/artifact/target/"
                    + versionsManager.ARTIFACT_NAME;
        if (spaceRTK != null)
            unload();
        if (artifact.exists())
            artifact.delete();
        Utilities.downloadFile(url, artifact);
        System.out.print("Done.\n");
        if (!firstTime && wasRunning)
            Wrapper.getInstance().performAction(ToolkitAction.UNHOLD, null);
    }
}
