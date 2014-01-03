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

import java.io.*;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * @author <a href="mailto:tkriz@redhat.com">Tadeas Kriz</a>
 */
public class FileUtils {
    private static final Logger logger = Logger.getLogger(FileUtils.class.getName());
    private static final String TEMP_DIRECTORY = System.getProperty("java.io.tmpdir");

    public static String platformIndependentPath(String path) {
        return path.replace('/', File.separatorChar);
    }

    public static File prepareWorkingDirectory() {
        return prepareWorkingDirectory(TEMP_DIRECTORY);
    }

    public static File prepareWorkingDirectory(String parent) {
        return prepareWorkingDirectory(new File(parent));
    }

    public static File prepareWorkingDirectory(File parent) {
        String randomDirectoryName = UUID.randomUUID().toString();

        File workingDirectory = new File(TEMP_DIRECTORY, randomDirectoryName);

        if(workingDirectory.exists()) {
            if(!workingDirectory.delete()) {
                throw new IllegalStateException("Couldn't delete existing directory: \"" + workingDirectory.toString() + "\" !");
            }
        }

        if (!workingDirectory.mkdirs()) {
            throw new IllegalStateException("Couldn't create working directory: \"" + workingDirectory.toString() + "\" !");
        }

        logger.info("Created working directory in \"" + workingDirectory.getPath() + "\".");
        return workingDirectory;
    }

    public static void copy(File source, File destination) throws IOException {
        if(source.isDirectory()) {
            copyDirectory(source, destination);
        } else {
            copyFile(source, destination);
        }
    }

    public static void copyDirectory(File source, File destination) throws IOException {
        if(!source.isDirectory()) {
            throw new IllegalArgumentException("Source \"" + source.getPath() + "\" must be a directory!");
        }
        if(!source.exists()) {
            throw new IllegalStateException("Source directory \"" + source.getPath() + "\" doesn't exist!");
        }
        if(destination.exists()) {
            throw new IllegalStateException("Destination \"" + destination.getPath() + "\" exists!");
        }

        destination.mkdirs();
        final File[] files = source.listFiles();

        for(File file : files) {
            if(file.isDirectory()) {
                copyDirectory(file, new File(destination, file.getName()));
            } else {
                copyFile(file, new File(destination, file.getName()));
            }
        }

    }

    public static void copyFile(File source, File destination) throws IOException {
        FileInputStream fileInputStream = null;
        FileOutputStream fileOutputStream = null;
        IOException exception = null;
        try {
            fileInputStream = new FileInputStream(source);
            fileOutputStream = new FileOutputStream(destination);

            byte[] buffer = new byte[1024];
            int read;
            while((read = fileInputStream.read(buffer, 0, buffer.length)) != -1) {
                fileOutputStream.write(buffer, 0, read);
            }
        } catch(IOException e) {
            exception = e;
        } finally {
            try {
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
            } catch (IOException e) {
                if(exception == null) {
                    exception = e;
                } else {
                    logger.log(Level.WARNING, "Exception thrown while closing input stream.", e);
                }
            }
            try {
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
            } catch (IOException e) {
                if(exception == null) {
                    exception = e;
                } else {
                    logger.log(Level.WARNING, "Exception thrown while closing output stream.", e);
                }
            }

            if(exception != null) {
                throw exception;
            }
        }
    }

    public static void addFilesToExistingZip(String existingZipFilePath, String... filePaths) throws IOException {
        File[] files = new File[filePaths.length];

        for(int i = 0; i < filePaths.length; i++) {
            files[i] = new File(filePaths[i]);
        }

        addFilesToExistingZip(new File(existingZipFilePath), files);
    }

    public static void addFilesToExistingZip(File existingZipFile, File... files) throws IOException{
        File tempZipFile = new File(existingZipFile.getParentFile(), UUID.randomUUID().toString());
        existingZipFile.renameTo(tempZipFile);

        ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(tempZipFile));
        ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(existingZipFile));

        byte[] buffer = new byte[1024];

        ZipEntry zipEntry = null;
        while((zipEntry = zipInputStream.getNextEntry()) != null) {
            String name = zipEntry.getName();
            boolean notInFiles = true;
            for(File file : files) {
                if(file.getName().equals(name)) {
                    notInFiles = false;
                    break;
                }
            }
            if(notInFiles) {
                zipOutputStream.putNextEntry(new ZipEntry(name));

                int read;
                while((read = zipInputStream.read(buffer, 0, buffer.length)) != -1) {
                    zipOutputStream.write(buffer, 0, read);
                }

                zipOutputStream.closeEntry();
            }
        }

        zipInputStream.close();

        for(File file : files) {
            InputStream inputStream = new FileInputStream(file);
            zipOutputStream.putNextEntry(new ZipEntry(file.getName()));

            int read;
            while((read = inputStream.read(buffer, 0, buffer.length)) != -1) {
                zipOutputStream.write(buffer, 0, read);
            }

            zipOutputStream.closeEntry();
            inputStream.close();
        }

        zipOutputStream.close();
        tempZipFile.delete();
    }
}
