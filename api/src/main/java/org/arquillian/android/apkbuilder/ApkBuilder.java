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
package org.arquillian.android.apkbuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.arquillian.android.apkbuilder.util.Command;
import org.arquillian.android.apkbuilder.util.FileUtils;
import org.arquillian.android.apkbuilder.util.SDKUtils;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.Node;

/**
 * @author <a href="mailto:tkriz@redhat.com">Tadeas Kriz</a>
 */
public class ApkBuilder {
    private static final Logger logger = Logger.getLogger(ApkBuilder.class.getName());

    private final File workingDirectory;
    private final Configuration configuration = new Configuration();

    private ApkBuilder(String name, File workingDirectory) {
        this.workingDirectory = workingDirectory;
        this.configuration.outputName = name;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public File build() { // FIXME add some error handling!
        try {
            compileResources();
        } catch (IOException e) {
            // TODO log exception
            return null;
        }

        try {
            compileJava();
        } catch (IOException e) {
            // TODO log exception
            return null;
        }

        try {
            compileDex();
        } catch (IOException e) {
            // TODO log exception
            return null;
        }

        try {
            packageApk();
        } catch (IOException e) {
            // TODO log exception
            return null;
        }

        try {
            addDexToApk();
        } catch (IOException e) {
            // TODO log exception
            return null;
        }

        try {
            signApk();
        } catch (IOException e) {
            // TODO log exception
            return null;
        }

        try {
            alignApk();
        } catch (IOException e) {
            // TODO log exception
            return null;
        }

        return new File(workingDirectory, "target" + File.separator + configuration.getOutputName() + ".apk");
    }

    private void compileResources() throws IOException {
        Command command = new Command();
        command
            .add(configuration.getAaptPath())
            .add("package")
            .add("-m")
            .add("-J")
            .add(workingDirectory.getAbsolutePath() + FileUtils.platformIndependentPath("/java/"))
            .add("-M")
            .add(workingDirectory.getAbsolutePath() + FileUtils.platformIndependentPath("/AndroidManifest.xml"))
            .add("-S")
            .add(workingDirectory.getAbsolutePath() + FileUtils.platformIndependentPath("/res/"))
            .add("-I")
            .add(configuration.getAndroidJarPath());

        runCommand(command);
    }

    private void compileJava() throws IOException {
        Command command = new Command();
        command
            .add(configuration.getJavacPath())
            .add("-source")
            .add("1.6")
            .add("-target")
            .add("1.6")
            .add("-d")
            .add(workingDirectory.getAbsolutePath() + FileUtils.platformIndependentPath("/target/generated-classes"))
            .add("-s")
            .add(workingDirectory.getAbsolutePath() + FileUtils.platformIndependentPath("/target/generated-sources")); // TODO
                                                                                                                       // do we
                                                                                                                       // need
                                                                                                                       // this
                                                                                                                       // (generated-sources)?

        findJavaSourceFiles(command);

        runCommand(command);
    }

    private void findJavaSourceFiles(Command command) {
        File javaDirectory = new File(workingDirectory.getAbsolutePath() + FileUtils.platformIndependentPath("/java"));

        findJavaSourceFiles(command, javaDirectory);
    }

    private void findJavaSourceFiles(Command command, File directory) {

        final File[] files = directory.listFiles();

        for (File file : files) {
            if (file.isDirectory()) {
                findJavaSourceFiles(command, file);
            } else if (file.getPath().endsWith(".java")) {
                command.add(file.getPath());
            }
        }
    }

    private void compileDex() throws IOException {
        Command command = new Command();
        command
            .add(configuration.getDxPath())
            .add("--dex")
            .add("--output=" + workingDirectory.getAbsolutePath() + FileUtils.platformIndependentPath("/target/classes.dex"))
            .add(workingDirectory.getAbsolutePath() + FileUtils.platformIndependentPath("/class"))
            .add(workingDirectory.getAbsolutePath() + FileUtils.platformIndependentPath("/target/generated-classes"));

        runCommand(command);
    }

    private void packageApk() throws IOException {
        Command command = new Command();
        command
            .add(configuration.getAaptPath())
            .add("package")
            .add("-f")
            .add("-M")
            .add(workingDirectory.getAbsolutePath() + FileUtils.platformIndependentPath("/AndroidManifest.xml"))
            .add("-S")
            .add(workingDirectory.getAbsolutePath() + FileUtils.platformIndependentPath("/res/"))
            .add("-I")
            .add(configuration.getAndroidJarPath())
            .add("-F")
            .add(workingDirectory.getAbsolutePath()
                + FileUtils.platformIndependentPath("/target/" + configuration.getOutputName() + ".apk.unaligned"));

        runCommand(command);
    }

    private void addDexToApk() throws IOException {

        FileUtils.addFilesToExistingZip(
            workingDirectory.getAbsolutePath()
                + FileUtils.platformIndependentPath("/target/" + configuration.getOutputName() + ".apk.unaligned"),
            workingDirectory.getAbsolutePath() + FileUtils.platformIndependentPath("/target/classes.dex")
            );

        /*
         * Command command = new Command(); command .add(configuration.getAaptPath()) .add("add") .add("-f")
         * .add(workingDirectory.getPath() + File.separator + "target" + File.separator + configuration.getOutputName() +
         * ".apk.unaligned") .add(workingDirectory.getPath() + File.separator + "target" + File.separator + "classes.dex");
         *
         * runCommand(command);
         */
    }

    private void signApk() throws IOException {
        Command command = new Command();
        command
            .add(configuration.getJarsignerPath())
            .add("-storepass")
            .add(configuration.getKeystorePassword())
            .add("-keystore")
            .add(configuration.getKeystorePath())
            .add("-keypass")
            .add(configuration.getKeyPassword())
            .add("-sigalg")
            .add("MD5withRSA")
            .add("-digestalg")
            .add("SHA1")
            .add(workingDirectory.getAbsolutePath()
                + FileUtils.platformIndependentPath("/target/" + configuration.getOutputName() + ".apk.unaligned"))
            .add(configuration.getKeyAlias());

        runCommand(command);
    }

    private void alignApk() throws IOException {
        Command command = new Command();
        command
            .add(configuration.getZipalignPath())
            .add("4")
            .add(workingDirectory.getAbsolutePath()
                + FileUtils.platformIndependentPath("/target/" + configuration.getOutputName() + ".apk.unaligned"))
            .add(workingDirectory.getAbsolutePath()
                + FileUtils.platformIndependentPath("/target/" + configuration.getOutputName() + ".apk"));

        runCommand(command);
    }

    private void runCommand(Command command) throws IOException {
        logger.info("Running command: \"" + command.toString() + "\".");

        ProcessBuilder builder = new ProcessBuilder(command.getAsList());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line = null;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }
        reader.close();
    }

    public static ApkBuilder init(Archive<?> archive) {
        File workingDirectory = FileUtils.prepareWorkingDirectory();

        Map<ArchivePath, Node> content = archive.getContent();

        for (ArchivePath path : content.keySet()) {
            Node node = content.get(path);

            if (node.getAsset() == null) {
                // this node is directory
                File directory = new File(workingDirectory, path.get());

                directory.mkdirs();
            } else {
                File file = new File(workingDirectory, path.get());

                file.getParentFile().mkdirs();

                byte[] buffer = new byte[1024];
                int read;
                InputStream inputStream = node.getAsset().openStream();
                FileOutputStream fileOutputStream = null;
                try {
                    fileOutputStream = new FileOutputStream(file);
                    while ((read = inputStream.read(buffer, 0, buffer.length)) != -1) {
                        fileOutputStream.write(buffer, 0, read);
                    }
                } catch (IOException e) {

                } finally {
                    try {
                        if (inputStream != null) {
                            inputStream.close();
                        }
                        if (fileOutputStream != null) {
                            fileOutputStream.close();
                        }
                    } catch (IOException e) {

                    }
                }

            }

            ArchivePath p = node.getPath();
        }

        return init(archive.getName(), workingDirectory, true);
    }

    public static ApkBuilder init(String directory) {
        return init(generateOutputName(), directory);
    }

    public static ApkBuilder init(String name, String directory) {
        return init(name, directory, false);
    }

    public static ApkBuilder init(String name, String directory, boolean safeToEdit) {
        return init(name, new File(directory), safeToEdit);
    }

    public static ApkBuilder init(File directory) {
        return init(generateOutputName(), directory);
    }

    public static ApkBuilder init(String name, File directory) {
        return init(name, directory, false);
    }

    public static ApkBuilder init(String name, File directory, boolean safeToEdit) {
        File workingDirectory;
        if (!safeToEdit) {
            workingDirectory = FileUtils.prepareWorkingDirectory();

            try {
                FileUtils.copyDirectory(directory, workingDirectory);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        } else {
            workingDirectory = directory;
        }

        createSubdirectories(workingDirectory);

        return new ApkBuilder(name, workingDirectory);
    }

    private static String generateOutputName() {
        String name = UUID.randomUUID().toString();

        return name.substring(0, 8);
    }

    private static void createSubdirectories(File directory) {
        File targetDir = new File(directory, "target");
        targetDir.mkdir();

        File generatedClassesDir = new File(targetDir, "generated-classes");
        generatedClassesDir.mkdir();

        File generatedSourcesDir = new File(targetDir, "generated-sources");
        generatedSourcesDir.mkdir();

        File javaDir = new File(directory, "java");
        javaDir.mkdir();

        File classesDir = new File(directory, "class");
        classesDir.mkdir();

        File resDir = new File(directory, "res");
        resDir.mkdir();

        File assetDir = new File(directory, "asset");
        assetDir.mkdir();
    }

    public static class Configuration {
        private static String ANDROID_HOME = System.getenv("ANDROID_HOME");
        private static String JAVA_HOME = System.getenv("JAVA_HOME");
        private static int DEFAULT_API_LEVEL = 17;

        private final SDKUtils sdkUtils;

        private String outputName = null;

        private Integer apiLevel = null;
        private String androidHome = null;
        private String aaptPath = null;
        private String aidlPath = null;
        private String dxPath = null;
        private String llvmPath = null;
        private String androidJarPath = null;
        private String zipalignPath = null;

        private String keystorePath = null;
        private String keystorePassword = null;
        private String keyAlias = null;
        private String keyPassword = null;

        private String javaHome = null;
        private String javaBin = null;
        private String javacPath = null;
        private String jarsignerPath = null;

        public Configuration() {
            sdkUtils = new SDKUtils(this);
        }

        public SDKUtils getSdkUtils() {
            return sdkUtils;
        }

        public String getOutputName() {
            if (outputName == null) {
                outputName = generateOutputName();
            }

            return outputName;
        }

        public Configuration setOutputName(String outputName) {
            this.outputName = outputName;
            return this;
        }

        public int getApiLevel() {
            if (apiLevel == null) {
                apiLevel = DEFAULT_API_LEVEL;
            }

            return apiLevel;
        }

        public Configuration setApiLevel(int apiLevel) {
            this.apiLevel = apiLevel;
            return this;
        }

        public String getAndroidHome() {
            if (androidHome == null) {
                androidHome = ANDROID_HOME;
            }

            return androidHome;
        }

        public Configuration setAndroidHome(String androidHome) {
            this.androidHome = androidHome;
            return this;
        }

        public String getAaptPath() {
            if (aaptPath == null) {
                aaptPath = sdkUtils.getBuildTool("aapt");
            }

            return aaptPath;
        }

        public Configuration setAaptPath(String aaptPath) {
            this.aaptPath = aaptPath;
            return this;
        }

        public String getAidlPath() {
            if (aidlPath == null) {
                aidlPath = sdkUtils.getBuildTool("aidl");
            }

            return aidlPath;
        }

        public Configuration setAidlPath(String aidlPath) {
            this.aidlPath = aidlPath;
            return this;
        }

        public String getDxPath() {
            if (dxPath == null) {
                dxPath = sdkUtils.getBuildTool("dx");
            }

            return dxPath;
        }

        public Configuration setDxPath(String dxPath) {
            this.dxPath = dxPath;
            return this;
        }

        public String getLlvmPath() {
            if (llvmPath == null) {
                llvmPath = sdkUtils.getBuildTool("llvm-rs-cc");
            }

            return llvmPath;
        }

        public Configuration setLlvmPath(String llvmPath) {
            this.llvmPath = llvmPath;
            return this;
        }

        public String getAndroidJarPath() {
            if (androidJarPath == null) {
                androidJarPath = new File(sdkUtils.getPlatformDirectory(), "android.jar").getAbsolutePath();
            }

            return androidJarPath;
        }

        public Configuration setAndroidJarPath(String androidJarPath) {
            this.androidJarPath = androidJarPath;
            return this;
        }

        public String getZipalignPath() {
            if (zipalignPath == null) {
                zipalignPath = sdkUtils.getPathForTool("zipalign");
            }

            return zipalignPath;
        }

        public Configuration setZipalignPath(String zipalignPath) {
            this.zipalignPath = zipalignPath;
            return this;
        }

        public String getKeystorePath() {
            if (keystorePath == null) {
                keystorePath = System.getProperty("user.home") + FileUtils.platformIndependentPath("/.android/debug.keystore");
            }

            return keystorePath;
        }

        public Configuration setKeystorePath(String keystorePath) {
            this.keystorePath = keystorePath;
            return this;
        }

        public String getKeystorePassword() {
            if (keystorePassword == null) {
                keystorePassword = "android";
            }

            return keystorePassword;
        }

        public Configuration setKeystorePassword(String keystorePassword) {
            this.keystorePassword = keystorePassword;
            return this;
        }

        public String getKeyAlias() {
            if (keyAlias == null) {
                keyAlias = "androiddebugkey";
            }

            return keyAlias;
        }

        public Configuration setKeyAlias(String keyAlias) {
            this.keyAlias = keyAlias;
            return this;
        }

        public String getKeyPassword() {
            if (keyPassword == null) {
                keyPassword = "android";
            }

            return keyPassword;
        }

        public Configuration setKeyPassword(String keyPassword) {
            this.keyPassword = keyPassword;
            return this;
        }

        public String getJavaHome() {
            if (javaHome == null) {
                javaHome = JAVA_HOME;
            }

            return javaHome;
        }

        public Configuration setJavaHome(String javaHome) {
            this.javaHome = javaHome;
            return this;
        }

        public String getJavacPath() {
            if (javacPath == null) {
                javacPath = sdkUtils.getPathForJavaTool("javac");
            }

            return javacPath;
        }

        public Configuration setJavacPath(String javacPath) {
            this.javacPath = javacPath;
            return this;
        }

        public String getJarsignerPath() {
            if (jarsignerPath == null) {
                jarsignerPath = sdkUtils.getPathForJavaTool("jarsigner");
            }

            return jarsignerPath;
        }

        public Configuration setJarsignerPath(String jarsignerPath) {
            this.jarsignerPath = jarsignerPath;
            return this;
        }

        public void validate() {
            File aapt = new File(getAaptPath());
            if (!aapt.exists()) {
                throw new IllegalStateException("Aapt \"" + aapt.getPath() + "\" doesn't exist!");
            }
            if (!aapt.isFile()) {
                throw new IllegalStateException("Aapt \"" + aapt.getPath() + "\" isn't a file!");
            }

            File aidl = new File(getAidlPath());
            if (!aidl.exists()) {
                throw new IllegalStateException("Aidl \"" + aidl.getPath() + "\" doesn't exist!");
            }
            if (!aidl.isFile()) {
                throw new IllegalStateException("Aidl \"" + aidl.getPath() + "\" isn't a file!");
            }

            File dx = new File(getDxPath());
            if (!dx.exists()) {
                throw new IllegalStateException("Dx \"" + dx.getPath() + "\" doesn't exist!");
            }
            if (!dx.isFile()) {
                throw new IllegalStateException("Dx \"" + dx.getPath() + "\" isn't a file!");
            }

            File llvm = new File(getLlvmPath());
            if (!llvm.exists()) {
                throw new IllegalStateException("Llvm \"" + llvm.getPath() + "\" doesn't exist!");
            }
            if (!llvm.isFile()) {
                throw new IllegalStateException("Llvm \"" + llvm.getPath() + "\" isn't a file!");
            }

            File androidJar = new File(getAndroidJarPath());
            if (!androidJar.exists()) {
                throw new IllegalStateException("Android.jar \"" + androidJar.getPath() + "\" doesn't exist!");
            }
            if (!androidJar.isFile()) {
                throw new IllegalStateException("Android.jar \"" + androidJar.getPath() + "\" isn't a file!");
            }

            File javac = new File(getJavacPath());
            if (!javac.exists()) {
                throw new IllegalStateException("Javac \"" + javac.getPath() + "\" doesn't exist!");
            }
            if (!javac.isFile()) {
                throw new IllegalStateException("Javac \"" + javac.getPath() + "\" isn't a file!");
            }

            File jarsigner = new File(getJarsignerPath());
            if (!jarsigner.exists()) {
                throw new IllegalStateException("Jarsigner \"" + jarsigner.getPath() + "\" doesn't exist!");
            }
            if (!jarsigner.isFile()) {
                throw new IllegalStateException("Jarsigner \"" + jarsigner.getPath() + "\" isn't a file!");
            }
        }
    }

}