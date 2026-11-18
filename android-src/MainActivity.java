package com.marcelovega.automusic;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(MusicFolderPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
