package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
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

    /** The first playing or paused media session: its app, what it names, and its artwork scaled to tile size. */
    static final class Session {
        final String pkg, app, title;
        final boolean playing;
        /** At most ART_PX wide; null when the session carries no picture (a live stream, most of the time). */
        final Bitmap art;

        Session(String pkg, String app, String title, boolean playing, Bitmap art) {
            this.pkg = pkg;
            this.app = app;
            this.title = title;
            this.playing = playing;
            this.art = art;
        }

        /** What a rebuild compares: a new title, app or state is a new tile and a new backdrop. */
        String key() {
            return pkg + '\n' + title + '\n' + playing;
        }
    }

    private static final int ART_PX = 480;

    /** Null when nothing plays or is paused, or the listener is not allowed on this TV. */
    static Session active(Context ctx) {
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
                MediaMetadata meta = session.getMetadata();
                String title = meta == null ? null : meta.getString(MediaMetadata.METADATA_KEY_TITLE);
                title = title == null || title.trim().isEmpty() ? null : title.trim();
                Bitmap art = null;
                if (meta != null) {
                    for (String k : new String[]{MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART,
                            MediaMetadata.METADATA_KEY_DISPLAY_ICON}) {
                        art = meta.getBitmap(k);
                        if (art != null) break;
                    }
                }
                // Metadata pictures can be full posters: keep a tile-sized copy, the original goes back to the session.
                if (art != null && art.getWidth() > ART_PX) {
                    art = Bitmap.createScaledBitmap(art, ART_PX, Math.max(1, art.getHeight() * ART_PX / art.getWidth()), true);
                }
                return new Session(session.getPackageName(), app, title, s == PlaybackState.STATE_PLAYING, art);
            }
        } catch (SecurityException notAllowed) {
            // The listener is not allowed on this TV: no session.
        }
        return null;
    }

    // ponytail: read on resume and each minute like the weather line; a PlaybackState callback would update sooner.
    static void refresh(Context ctx, TextView line) {
        Session s = active(ctx);
        CharSequence shown = null;
        if (s != null) {
            String head = ctx.getString(s.playing ? R.string.now_playing : R.string.now_paused, s.app);
            SpannableString text = new SpannableString(s.title == null ? head : head + "\n" + s.title);
            if (s.title != null) {
                text.setSpan(new RelativeSizeSpan(0.75f), head.length() + 1, text.length(), 0);
                text.setSpan(new ForegroundColorSpan(0x99FFFFFF), head.length() + 1, text.length(), 0);
            }
            shown = text;
        }
        line.setText(shown);
        line.setVisibility(shown == null ? View.GONE : View.VISIBLE);
    }

    /**
     * now_playing_art: the artwork as a soft, dark backdrop at half the screen's size (the wallpaper view covers
     * the screen with it). Scaled down to 40 px and back up with filtering: a blur without RenderScript.
     */
    static Bitmap blur(Bitmap art, int screenW, int screenH) {
        int w = Math.max(2, screenW / 2), h = Math.max(2, screenH / 2);
        Bitmap small = Bitmap.createScaledBitmap(art, 40, Math.max(2, 40 * h / w), true);
        Bitmap soft = Bitmap.createScaledBitmap(small, w, h, true);
        if (small != soft) small.recycle();
        if (!soft.isMutable()) soft = soft.copy(Bitmap.Config.ARGB_8888, true);
        new Canvas(soft).drawColor(0xA6000000);
        return soft;
    }
}
