package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.app.Notification;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.widget.TextView;

/**
 * now_playing: "▶ Playing on Stremio" under the clock while an app plays or is paused, with the title the app
 * names on a smaller second line. Android hands media sessions only to an allowed notification listener, so this
 * class is one. Allow it over ADB with {@code cmd notification allow_listener com.gathin.tvhome/.NowPlaying};
 * not allowed, the line stays hidden. With notification_line it also keeps the latest other app's notification
 * title for Home to show for a minute; nothing is stored beyond that or sent anywhere.
 */
public final class NowPlaying extends NotificationListenerService {

    /** notification_line: the latest title ("Downloader: Download complete"), when it arrived, and Home's hook. */
    static volatile String notice;
    static volatile long noticeAt;
    static volatile Runnable onNotice;
    static final long NOTICE_MS = 60_000L;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        if (sbn.isOngoing() || getPackageName().equals(sbn.getPackageName())
                || Notification.CATEGORY_TRANSPORT.equals(n.category)
                || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
        if (title == null || title.toString().trim().isEmpty()) return;
        PackageManager pm = getPackageManager();
        String app;
        try {
            app = pm.getApplicationLabel(pm.getApplicationInfo(sbn.getPackageName(), 0)).toString();
        } catch (PackageManager.NameNotFoundException gone) {
            app = sbn.getPackageName();
        }
        notice = app + ": " + title.toString().trim();
        noticeAt = SystemClock.elapsedRealtime();
        Runnable hook = onNotice;
        if (hook != null) hook.run();
    }

    // ponytail: read on resume and each minute like the weather line; a PlaybackState callback would update sooner.
    static void refresh(Context ctx, TextView line) {
        CharSequence shown = null;
        try {
            MediaSessionManager sessions = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            for (MediaController session : sessions.getActiveSessions(new ComponentName(ctx, NowPlaying.class))) {
                PlaybackState state = session.getPlaybackState();
                int s = state == null ? PlaybackState.STATE_NONE : state.getState();
                if (s != PlaybackState.STATE_PLAYING && s != PlaybackState.STATE_PAUSED) continue;
                PackageManager pm = ctx.getPackageManager();
                String app;
                try {
                    app = pm.getApplicationLabel(pm.getApplicationInfo(session.getPackageName(), 0)).toString();
                } catch (PackageManager.NameNotFoundException gone) {
                    app = session.getPackageName();
                }
                String head = ctx.getString(s == PlaybackState.STATE_PLAYING ? R.string.now_playing : R.string.now_paused, app);
                MediaMetadata meta = session.getMetadata();
                String title = meta == null ? null : meta.getString(MediaMetadata.METADATA_KEY_TITLE);
                title = title == null || title.trim().isEmpty() ? null : title.trim();
                SpannableString text = new SpannableString(title == null ? head : head + "\n" + title);
                if (title != null) {
                    text.setSpan(new RelativeSizeSpan(0.75f), head.length() + 1, text.length(), 0);
                    text.setSpan(new ForegroundColorSpan(0x99FFFFFF), head.length() + 1, text.length(), 0);
                }
                shown = text;
                break;
            }
        } catch (SecurityException notAllowed) {
            // The listener is not allowed on this TV: no line.
        }
        line.setText(shown);
        line.setVisibility(shown == null ? View.GONE : View.VISIBLE);
    }
}
