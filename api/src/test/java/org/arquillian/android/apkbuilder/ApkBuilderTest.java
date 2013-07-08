package org.arquillian.android.apkbuilder;

import org.jboss.shrinkwrap.android.api.spec.AndroidManifest;
import org.jboss.shrinkwrap.android.api.spec.node.*;
import org.jboss.shrinkwrap.api.ArchiveFactory;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.android.api.spec.AndroidArchive;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertNotNull;

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

        System.out.println(manifest.toString());


        ApkBuilder builder = ApkBuilder.init(archive);

        assertNotNull(builder.build());
    }


}
