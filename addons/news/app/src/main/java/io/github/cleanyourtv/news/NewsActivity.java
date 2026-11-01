package io.github.cleanyourtv.news;

import android.app.Activity;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Date;
import java.util.List;

/** The headlines full screen: a list on the left, the focused item's summary on the right. */
public final class NewsActivity extends Activity {
    private ListView list;
    private TextView status, source, summary;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private int polls;
    /** A fetch the launcher's query started gives this screen no callback: look at the cache again for a minute. */
    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (list.getCount() > 0) return;
            List<Feed.Item> items = Feed.cached(NewsActivity.this);
            if (!items.isEmpty()) show(items, false);
            else if (++polls < 12) handler.postDelayed(this, 5_000);
            else show(items, true);
        }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_news);
        list = findViewById(R.id.list);
        status = findViewById(R.id.status);
        source = findViewById(R.id.source);
        summary = findViewById(R.id.summary);
        list.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { showItem((Feed.Item) p.getItemAtPosition(pos)); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        List<Feed.Item> items = Feed.cached(this);
        show(items, false);
        // Stale, or nothing yet (the first open, or a failed try under five minutes ago): fetch now.
        if (Feed.stale(Feed.cache(this)) || items.isEmpty()) {
            Feed.refresh(this, ok -> runOnUiThread(() -> show(Feed.cached(this), !ok)));
            polls = 0;
            handler.postDelayed(poll, 5_000);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(poll);
    }

    private void show(List<Feed.Item> items, boolean failed) {
        long fetched = Feed.cache(this).getLong("fetched", 0);
        // The same stories again (a refresh that found nothing new): keep the place in the list.
        if (list.getCount() > 0 && items.size() == list.getCount() && !failed
                && ((Feed.Item) list.getItemAtPosition(0)).title.equals(items.get(0).title)) return;
        if (items.isEmpty()) {
            status.setText(failed ? R.string.offline : R.string.loading);
        } else {
            status.setText(failed ? getString(R.string.offline)
                    : getString(R.string.updated, DateFormat.getTimeFormat(this).format(new Date(fetched))));
        }
        list.setAdapter(new ArrayAdapter<Feed.Item>(this, R.layout.item_headline, items) {
            @Override
            public View getView(int pos, View convert, ViewGroup parent) {
                View v = convert != null ? convert : LayoutInflater.from(getContext()).inflate(R.layout.item_headline, parent, false);
                Feed.Item i = getItem(pos);
                ((TextView) v.findViewById(R.id.title)).setText(i.title);
                ((TextView) v.findViewById(R.id.meta)).setText(i.source + (i.published > 0 ? " · " + age(i.published) : ""));
                return v;
            }
        });
        if (!items.isEmpty()) {
            list.requestFocus();
            showItem(items.get(0));
        }
    }

    private void showItem(Feed.Item i) {
        if (i == null) return;
        source.setText(i.source + (i.published > 0 ? " · " + age(i.published) : ""));
        summary.setText(i.summary);
    }

    private String age(long when) {
        long m = Math.max(0, (System.currentTimeMillis() - when) / 60_000L);
        if (m < 60) return getString(R.string.age_minutes, (int) m);
        if (m < 24 * 60) return getString(R.string.age_hours, (int) (m / 60));
        return getString(R.string.age_days, (int) (m / (24 * 60)));
    }
}
