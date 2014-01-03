/*
 * JBoss, Home of Professional Open Source
 * Copyright 2013 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.arquillian.android.apkbuilder.util;

import org.arquillian.android.apkbuilder.ApkBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

import static org.arquillian.android.apkbuilder.util.FileUtils.platformIndependentPath;

/**
 * Helps finding the right Android tools. It's based on AndroidSDK class.
 *
 * @author <a href="mailto:tkriz@redhat.com">Tadeas Kriz</a>
 * @author <a href="mailto:smikloso@redhat.com">Stefan Miklosovic</a>
 * @author <a href="mailto:kpiwko@redhat.com">Karel Piwko</a>
 * @author hugo.josefson@jayway.com
 * @author Manfred Moser <manfred@simpligility.com>
 */
public class SDKUtils {

    private static final String API_LEVEL_PROPERTY = "AndroidVersion.ApiLevel";
    private static final String BUILD_TOOLS_FOLDER_NAME = "build-tools";
    private static final String PLATFORMS_FOLDER_NAME = "platforms";
    private static final String PLATFORM_TOOLS_FOLDER_NAME = "platform-tools";
    private static final String PLATFORM_VERSION_PROPERTY = "Platform.Version";
    private static final String SOURCE_PROPERTIES_FILENAME = "source.properties";

    private final ApkBuilder.Configuration configuration;

    private Set<Platform> availablePlatforms;

    public SDKUtils(final ApkBuilder.Configuration configuration) {
        this.configuration = configuration;

        availablePlatforms = findAvailablePlatforms();
    }

    public String getPathForJavaTool(String tool) {
        String[] possiblePaths = {
            configuration.getJavaHome() + platformIndependentPath("/bin/" + tool)
        };

        for (String possiblePath : possiblePaths) {
            File file = new File(possiblePath);
            if (file.exists() && !file.isDirectory()) {
                return file.getAbsolutePath();
            }
        }

        throw new RuntimeException("Couldn't find tool \"" + tool
            + "\"! Please ensure you've set JAVA_HOME environment variable properly and that it points to your Java directory.");
    }

    public String getPathForTool(String tool) {
        String[] possiblePaths = {
            getSdkPath() + platformIndependentPath("/" + PLATFORMS_FOLDER_NAME + "/" + tool),
            getSdkPath() + platformIndependentPath("/" + PLATFORMS_FOLDER_NAME + "/" + tool + ".exe"),
            getSdkPath() + platformIndependentPath("/" + PLATFORMS_FOLDER_NAME + "/" + tool + ".bat"),
            getSdkPath() + platformIndependentPath("/" + PLATFORMS_FOLDER_NAME + "/lib/" + tool),
            getPlatformDirectory() + platformIndependentPath("/tools/" + tool),
            getPlatformDirectory() + platformIndependentPath("/tools/" + tool + ".exe"),
            getPlatformDirectory() + platformIndependentPath("/tools/" + tool + ".bat"),
            getPlatformDirectory() + platformIndependentPath("/tools/lib/" + tool),
            getSdkPath() + platformIndependentPath("/tools/" + tool),
            getSdkPath() + platformIndependentPath("/tools/" + tool + ".exe"),
            getSdkPath() + platformIndependentPath("/tools/" + tool + ".bat"),
            getSdkPath() + platformIndependentPath("/tools/lib/" + tool),
            getSdkPath() + platformIndependentPath("/" + PLATFORM_TOOLS_FOLDER_NAME + "/" + tool)
        };

        for (String possiblePath : possiblePaths) {
            File file = new File(possiblePath);
            if (file.exists() && !file.isDirectory()) {
                return file.getAbsolutePath();
            }
        }

        throw new RuntimeException("Could not find tool \"" + tool + "\"!");
    }

    public String getBuildTool(String tool) {

        File possiblePlatformPath = new File(getPlatformDirectory(), platformIndependentPath("/tools/" + tool));

        if (possiblePlatformPath.exists() && !possiblePlatformPath.isDirectory()) {
            return possiblePlatformPath.getAbsolutePath();
        }

        File possibleBuildPath = new File(getSdkPath(), BUILD_TOOLS_FOLDER_NAME);

        File[] dirs = possibleBuildPath.listFiles();
        Arrays.sort(dirs);

        for (File dir : dirs) {
            File tmpTool = new File(dir, tool);
            if (tmpTool.exists() && !tmpTool.isDirectory()) {
                return tmpTool.getAbsolutePath();
            }
        }

        throw new RuntimeException("Couldn't find tool: \"" + tool + "\"!");
    }

    public File getPlatformDirectory() {
        final File platformsDirectory = new File(getSdkPath(), PLATFORMS_FOLDER_NAME);

        Platform currentPlatform = getCurrentPlatform();
        if (currentPlatform == null) {
            final File[] platformDirectories = platformsDirectory.listFiles();

            Arrays.sort(platformDirectories);

            return platformDirectories[platformDirectories.length - 1];
        } else {
            final File platformDirectory = new File(currentPlatform.path);
            return platformDirectory;
        }
    }

    public Platform getCurrentPlatform() {
        return findPlatformByApiLevel(configuration.getApiLevel());
    }

    private Platform findPlatformByApiLevel(Integer apiLevel) {
        for (Platform p : availablePlatforms) {
            if (p.apiLevel.equals(apiLevel)) {
                return p;
            }
        }
        return null;
    }

    private String getSdkPath() {
        return configuration.getAndroidHome();
    }

    private Set<Platform> findAvailablePlatforms() {
        List<Platform> availablePlatforms = new ArrayList<Platform>();

        List<File> platformDirectories = getPlatformDirectories();
        for (File platformDirectory : platformDirectories) {
            File propertiesFile = new File(platformDirectory, SOURCE_PROPERTIES_FILENAME);
            Properties properties = new Properties();
            try {
                properties.load(new FileInputStream(propertiesFile));
            } catch (IOException e) {
                throw new IllegalStateException("Unable to read platform directory details from its configuration file "
                    + propertiesFile.getAbsoluteFile());
            }
            if (properties.containsKey(PLATFORM_VERSION_PROPERTY) && properties.containsKey(API_LEVEL_PROPERTY)) {
                String platform = properties.getProperty(PLATFORM_VERSION_PROPERTY);
                String apiLevel = properties.getProperty(API_LEVEL_PROPERTY);

                Platform p = new Platform(platform, Integer.parseInt(apiLevel), platformDirectory.getAbsolutePath());
                availablePlatforms.add(p);
            }
        }

        Collections.sort(availablePlatforms);
        return new LinkedHashSet<Platform>(availablePlatforms);
    }

    private List<File> getPlatformDirectories() {
        List<File> sourcePropertyFiles = new ArrayList<File>();

        final File platformsDirectory = new File(getSdkPath(), PLATFORMS_FOLDER_NAME);

        final File[] platformDirectories = platformsDirectory.listFiles();
        for (File file : platformDirectories) {
            if (file.isDirectory() && file.getName().startsWith("android-")) {
                sourcePropertyFiles.add(file);
            }
        }
        return sourcePropertyFiles;
    }

    public static class Platform implements Comparable<Platform> {
        final String name;
        final Integer apiLevel;
        final String path;

        public Platform(String name, Integer apiLevel, String path) {
            this.name = name;
            this.apiLevel = apiLevel;
            this.path = path;
        }

        @Override
        public int compareTo(Platform o) {
            return apiLevel.compareTo(o.apiLevel);
        }
    }
}
