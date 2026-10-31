package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityOptions;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * Fire OS HOME takeover (config.xml home_hook), ported from dumbtv's Guard (github.com/humblemuzzu/dumbtv,
 * MIT; see launcher/NOTICE). Fire OS keeps HOME bound to Amazon's protected launcher and handles the HOME
 * key before any accessibility filter sees it, so this service waits for Amazon's home window and replaces
 * it with {@link MainActivity}.
 *
 * A plain startActivity right after a HOME press is held back by Android's five-second app-switch delay.
 * An alarm-fired PendingIntent is sent by system_server, which is exempt, so the launch goes through
 * AlarmManager; that alarm only fires at once with {@code min_futurity=0} in the global
 * {@code alarm_manager_constants} (Android 11), which the install step writes and the undo removes.
 * Android 11 also refuses an activity start from an app with no visible window, even one sent through an
 * alarm, so Amazon's home is first covered with an opaque accessibility overlay in the launcher's background
 * colour: the cover is that window, and the launch waits until it has reached the window manager. The cover
 * drops when our own window comes up, or after {@link #COVER_MAX_MS}.
 *
 * Waking from standby resumes whatever was on screen without a new window event, so with Amazon's home as
 * the last window seen, the screen-on broadcast launches ours. With home_hook off the service binds and does
 * nothing: no overlay, no receiver, no alarm.
 *
 * Enable over adb (the undo clears the entry and the alarm constant):
 *   settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeHookService
 *   settings put secure accessibility_enabled 1
 *   settings put global alarm_manager_constants min_futurity=0
 */
public class HomeHookService extends AccessibilityService {
    static final String AMAZON = "com.amazon.tv.launcher";
    static final String AMAZON_HOME = "com.amazon.tv.launcher.ui.HomeActivity_vNext";
    static final long COVER_MAX_MS = 1500;
    static final long REPEAT_MS = 1000;
    private static final long LAUNCH_FALLBACK_MS = 150;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable uncover = this::uncover;
    private final Runnable launch = this::launchNow;
    private View cover;
    private int background = 0xFF000000;
    private boolean repairRecord;
    private boolean onAmazonHome;
    private long lastLaunch;
    private BroadcastReceiver screenOn;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Config config = Config.load(this);
        if (!config.homeHook) return;
        background = config.backgroundColor;
        repairRecord = config.restoreKeyServices;
        if (repairRecord) ServiceRepair.connected(this, getClass());
        View view = new View(this);
        view.setVisibility(View.GONE);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
        lp.windowAnimations = 0;
        getSystemService(WindowManager.class).addView(view, lp);
        cover = view;
        screenOn = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (onAmazonHome) launchWhenCovered();
            }
        };
        registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (repairRecord) ServiceRepair.disconnected(this, getClass());
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(uncover);
        handler.removeCallbacks(launch);
        if (screenOn != null) unregisterReceiver(screenOn);
        if (cover != null) getSystemService(WindowManager.class).removeView(cover);
        cover = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (cover == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;
        if (getPackageName().contentEquals(pkg)) {
            // Ours is up: Amazon's home is hidden behind it, so the cover can go.
            onAmazonHome = false;
            uncover();
            return;
        }
        if (leftFor(pkg)) {
            // Another app came up: a launch still queued must not pull the user back here.
            onAmazonHome = false;
            getSystemService(AlarmManager.class).cancel(pending());
            return;
        }
        if (!AMAZON.contentEquals(pkg)) return;
        onAmazonHome = isAmazonHome(pkg, event.getClassName());
        if (!onAmazonHome) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastLaunch < REPEAT_MS) return;
        lastLaunch = now;
        launchWhenCovered();
    }

    /** Amazon's home screen itself, not its settings grid or another of its screens. */
    static boolean isAmazonHome(CharSequence pkg, CharSequence cls) {
        return pkg != null && cls != null && AMAZON.contentEquals(pkg) && AMAZON_HOME.contentEquals(cls);
    }

    /** A window of an ordinary app: not Amazon's launcher, not the system UI and not the framework's own dialogs. */
    static boolean leftFor(CharSequence pkg) {
        return pkg != null && !AMAZON.contentEquals(pkg) && !"com.android.systemui".contentEquals(pkg)
                && !"android".contentEquals(pkg);
    }

    /**
     * Shows the cover, then fires the launch once that window is visible to the window manager: at once when the
     * cover is already up (a repeat within COVER_MAX_MS), otherwise from its first pre-draw pass, which runs after
     * the window's relayout, with a short fallback for a window that never draws.
     */
    private void launchWhenCovered() {
        handler.removeCallbacks(launch);
        boolean shown = cover.getVisibility() == View.VISIBLE;
        cover.setBackgroundColor(background);
        cover.setVisibility(View.VISIBLE);
        handler.removeCallbacks(uncover);
        handler.postDelayed(uncover, COVER_MAX_MS);
        if (shown) {
            launchNow();
            return;
        }
        cover.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (cover != null) cover.getViewTreeObserver().removeOnPreDrawListener(this);
                handler.removeCallbacks(launch);
                launchNow();
                return true;
            }
        });
        handler.postDelayed(launch, LAUNCH_FALLBACK_MS);
    }

    private void launchNow() {
        getSystemService(AlarmManager.class).setExact(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime(), pending());
    }

    private PendingIntent pending() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setClass(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return PendingIntent.getActivity(this, 0, home,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE,
                ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle());
    }

    private void uncover() {
        handler.removeCallbacks(uncover);
        if (cover != null) cover.setVisibility(View.GONE);
    }

    @Override
    public void onInterrupt() {
    }
}
