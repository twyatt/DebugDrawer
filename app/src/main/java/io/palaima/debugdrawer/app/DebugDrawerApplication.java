package io.palaima.debugdrawer.app;

import android.app.Application;
import android.support.multidex.MultiDex;

import com.squareup.leakcanary.LeakCanary;

import io.palaima.debugdrawer.timber.data.LumberYard;
import timber.log.LogcatTree;
import timber.log.Timber;

public class DebugDrawerApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        MultiDex.install(this);
        LeakCanary.install(this);

        LumberYard lumberYard = LumberYard.getInstance(this);
        lumberYard.cleanUp();

        Timber.INSTANCE.plant(lumberYard.tree());
        Timber.INSTANCE.plant(new LogcatTree());
    }
}
