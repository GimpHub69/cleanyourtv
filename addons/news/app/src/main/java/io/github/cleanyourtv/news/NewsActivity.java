package io.github.cleanyourtv.news;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/** The headlines full screen: a list on the left, the focused item's picture and summary on the right. */
public final class NewsActivity extends Activity {
    private ListView list;
    private TextView status, source, summary;
    private ImageView picture;
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
        picture = findViewById(R.id.picture);
        findViewById(R.id.feeds).setOnClickListener(v -> chooseFeeds());
        list.setOnItemClickListener((p, v, pos, id) -> {
            Feed.Item i = (Feed.Item) p.getItemAtPosition(pos);
            if (i != null && !i.link.isEmpty()) {
                startActivity(new android.content.Intent(this, ArticleActivity.class).putExtra(ArticleActivity.EXTRA_URL, i.link));
            }
        });
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
        if (items.isEmpty()) {
            status.setText(failed ? R.string.offline : R.string.loading);
        } else {
            status.setText(failed ? getString(R.string.offline)
                    : getString(R.string.updated, DateFormat.getTimeFormat(this).format(new Date(fetched))));
        }
        // The same stories again (a refresh that found nothing new): keep the place in the list.
        if (list.getCount() > 0 && items.size() == list.getCount() && !failed
                && ((Feed.Item) list.getItemAtPosition(0)).title.equals(items.get(0).title)) return;
        list.setAdapter(new ArrayAdapter<Feed.Item>(this, R.layout.item_headline, items) {
            @Override
            public View getView(int pos, View convert, ViewGroup parent) {
                View v = convert != null ? convert : LayoutInflater.from(getContext()).inflate(R.layout.item_headline, parent, false);
                Feed.Item i = getItem(pos);
                ((TextView) v.findViewById(R.id.title)).setText(i.title);
                ((TextView) v.findViewById(R.id.meta)).setText(i.source + (i.published > 0 ? " · " + age(i.published) : ""));
                ImageView thumb = v.findViewById(R.id.thumb);
                Bitmap b = cachedPicture(i);
                thumb.setImageBitmap(b);
                thumb.setVisibility(b != null ? View.VISIBLE : View.GONE);
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
        Bitmap b = cachedPicture(i);
        picture.setImageBitmap(b);
        picture.setVisibility(b != null ? View.VISIBLE : View.GONE);
    }

    /** The story's picture from the add-on's own cache (480 px wide JPEG, a few ms to decode), or null. */
    private Bitmap cachedPicture(Feed.Item i) {
        if (i.image.isEmpty()) return null;
        File f = Feed.imageFile(this, i.image);
        if (!f.isFile()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    /** The Feeds page: the presets from options.xml and any address the owner added, ticked or not, plus "Add another". */
    private void chooseFeeds() {
        String[] names = getResources().getStringArray(R.array.preset_names);
        String[] presets = getResources().getStringArray(R.array.preset_urls);
        List<String> current = Arrays.asList(Feed.feeds(this));
        List<String> urls = new ArrayList<>(Arrays.asList(presets));
        List<String> labels = new ArrayList<>(Arrays.asList(names));
        for (String u : current) if (!urls.contains(u)) { urls.add(u); labels.add(u); }
        labels.add(getString(R.string.feeds_add));
        boolean[] ticked = new boolean[labels.size()];
        for (int k = 0; k < urls.size(); k++) ticked[k] = current.contains(urls.get(k));
        new AlertDialog.Builder(this)
                .setTitle(R.string.feeds_title)
                .setMultiChoiceItems(labels.toArray(new String[0]), ticked, (d, which, on) -> {
                    if (which == urls.size()) {
                        ((AlertDialog) d).getListView().setItemChecked(which, false);
                        d.dismiss();
                        addFeed();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    List<String> chosen = new ArrayList<>();
                    for (int k = 0; k < urls.size(); k++) if (ticked[k]) chosen.add(urls.get(k));
                    if (chosen.isEmpty()) {
                        Toast.makeText(this, R.string.feeds_none, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveFeeds(chosen);
                })
                .show();
    }

    /** One more address, typed with the TV's keyboard; it joins the ticked list and the Feeds page reopens. */
    private void addFeed() {
        EditText in = new EditText(this);
        in.setHint(R.string.feeds_add_hint);
        in.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        in.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle(R.string.feeds_add_title)
                .setView(in)
                .setNegativeButton(R.string.cancel, (d, w) -> chooseFeeds())
                .setPositiveButton(R.string.ok, (d, w) -> {
                    List<String> one = Feed.parseFeeds(in.getText().toString());
                    if (one.isEmpty()) {
                        Toast.makeText(this, R.string.feeds_add_bad, Toast.LENGTH_SHORT).show();
                        chooseFeeds();
                        return;
                    }
                    List<String> chosen = new ArrayList<>(Arrays.asList(Feed.feeds(this)));
                    if (!chosen.contains(one.get(0))) chosen.add(one.get(0));
                    saveFeeds(chosen);
                })
                .show();
    }

    private void saveFeeds(List<String> chosen) {
        Feed.saveFeeds(this, chosen);
        status.setText(R.string.loading);
        Feed.refresh(this, ok -> runOnUiThread(() -> show(Feed.cached(this), !ok)));
    }

    private String age(long when) {
        long m = Math.max(0, (System.currentTimeMillis() - when) / 60_000L);
        if (m < 60) return getString(R.string.age_minutes, (int) m);
        if (m < 24 * 60) return getString(R.string.age_hours, (int) (m / 60));
        return getString(R.string.age_days, (int) (m / (24 * 60)));
    }
}
