package org.arquillian.android.apkbuilder;

import org.arquillian.android.apkbuilder.util.FileUtils;
import org.jboss.shrinkwrap.android.api.spec.AndroidManifest;
import org.jboss.shrinkwrap.android.api.spec.node.*;
import org.jboss.shrinkwrap.api.ArchiveFactory;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.android.api.spec.AndroidArchive;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author <a href="mailto:tkriz@redhat.com">Tadeas Kriz</a>
 */
@RunWith(JUnit4.class)
public class ApkBuilderTest {

    @Test
    public void apkBuilderFromArchive() {

        Activity myActivity = new Activity()
                .setName("MyActivity")
                .setLabel("ArquillianApkBuilderTest")
                .addIntentFilter(
                        new IntentFilter()
                                .addAction(new Action().setName("android.intent.action.MAIN"))
                                .addCategory(new Category().setName("android.intent.category.LAUNCHER"))
                );

        Application application = new Application()
                .setLabel("ArquillianApkBuilderTest")
                .addActivity(myActivity);


        AndroidManifest manifest = new AndroidManifest();
        manifest
                .setApplication(application)
                .setPackage("org.arquillian.android.apkbuilder")
                .setVersionCode(1)
                .setVersionName("1.0")
                .setUsesSdk(new UsesSdk().setMinSdkVersion(17));

        AndroidArchive archive = ShrinkWrap.create(AndroidArchive.class);

        archive.addAsResource("Joan_Baez_Bob_Dylan.jpg", "drawable/obrazek1.jpg");
        archive.addAsResource("main_layout.xml", "layout/main_layout.xml");
        archive.addClass(ApkBuilder.class);
        archive.addClass(ApkBuilderTest.class);
        archive.addClass(MyActivity.class);
        archive.addAsAndroidManifest(manifest);


        ApkBuilder builder = ApkBuilder.init(archive);

        assertNotNull(builder.build());
    }

    @Test
    @Ignore
    public void apkBuilderFromDirectory() {

        Application application = new Application()
                .setLabel("ArquillianApkBuilderTest");

        AndroidManifest manifest = new AndroidManifest();
        manifest
                .setApplication(application)
                .setPackage("org.arquillian.android.apkbuilder")
                .setVersionCode(1)
                .setVersionName("1.0")
                .setUsesSdk(new UsesSdk().setMinSdkVersion(17));

        File workingDirectory = FileUtils.prepareWorkingDirectory();
        File manifestFile = new File(workingDirectory, "AndroidManifest.xml");

        try {
            FileWriter writer = new FileWriter(manifestFile);
            writer.write(manifest.toXmlString());
            writer.close();
        } catch(IOException e) {
            assertNull(e);
        }

        ApkBuilder builder = ApkBuilder.init("ApkBuilderTest", workingDirectory, true);

        assertNotNull(builder.build());

    }

}
