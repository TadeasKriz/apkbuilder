package org.arquillian.android.apkbuilder;

import android.app.Activity;
import android.os.Bundle;

/**
 * @author <a href="mailto:tkriz@redhat.com">Tadeas Kriz</a>
 */
public class MyActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);
    }
}
