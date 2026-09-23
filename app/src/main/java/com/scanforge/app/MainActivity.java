package com.scanforge.app;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.google.mlkit.vision.documentscanner.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.android.gms.tasks.Tasks;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class MainActivity extends ComponentActivity {
    private static final int BG = 0xFFF5F6F2, INK = 0xFF183D33, MUTED = 0xFF6A7871,
        GREEN = 0xFF167D65, PALE = 0xFFE3EFE6, WHITE = 0xFFFFFFFF;
    private ScanSession session;
    private LinearLayout root, content, library;
    private SharedPreferences preferences;
    private String query = "";
    private boolean favorites;
    private final ArrayList<Bitmap> previews = new ArrayList<>();
    private ActivityResultLauncher<IntentSenderRequest> scanner;
    private ActivityResultLauncher<String> savePdf;
    private String savingPath;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        preferences = getSharedPreferences("settings", MODE_PRIVATE);
        session = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @Override public <T extends ViewModel> T create(Class<T> type) {
                return type.cast(new ScanSession(getApplicationContext()));
            }
        }).get(ScanSession.class);
        if (saved != null) {
            savingPath = saved.getString("savingPath");
            session.pendingAppendId = saved.getString("appendId");
            query = saved.getString("query", ""); favorites = saved.getBoolean("favorites");
        }
        scanner = registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
            String appendId = session.pendingAppendId; session.pendingAppendId = null;
            if (result.getResultCode() != RESULT_OK) return;
            GmsDocumentScanningResult scan = GmsDocumentScanningResult.fromActivityResultIntent(result.getData());
            if (scan == null || scan.getPages() == null || scan.getPages().isEmpty()) return;
            ArrayList<Uri> uris = new ArrayList<>();
            for (GmsDocumentScanningResult.Page page : scan.getPages()) uris.add(page.getImageUri());
            session.capturedPages = uris; session.capturedAppendId = appendId;
            session.notifyUi();
        });
        savePdf = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/pdf"), uri -> {
            String path = savingPath; savingPath = null;
            if (uri == null || path == null) return;
            session.saveDestination = uri; session.saveSource = path; session.notifyUi();
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (session.busy) { toast("Please wait for this operation to finish"); return; }
                if (session.selected != null) { session.selected = null; render(); }
                else finish();
            }
        });
        session.initialize();
    }

    @Override protected void onStart() { super.onStart(); session.listener = this::render; render(); }
    @Override protected void onStop() { session.listener = null; super.onStop(); }
    @Override protected void onDestroy() { releasePreviews(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("savingPath", savingPath); state.putString("appendId", session.pendingAppendId);
        state.putString("query", query); state.putBoolean("favorites", favorites);
        super.onSaveInstanceState(state);
    }

    private void render() {
        if (!session.busy && session.saveDestination != null) {
            Uri uri = session.saveDestination; String path = session.saveSource;
            session.saveDestination = null; session.saveSource = null;
            session.run("Saving PDF…", () -> {
                try (InputStream in = new FileInputStream(path);
                     OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                    if (out == null) throw new IOException("The selected location is not writable");
                    byte[] buffer = new byte[32768]; int n;
                    while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                }
                session.main.post(() -> toast("PDF saved to your selected location"));
            }); return;
        }
        if (!session.busy && !session.failedLoad && session.capturedPages != null) {
            List<Uri> pages = session.capturedPages; String append = session.capturedAppendId;
            session.capturedPages = null; session.capturedAppendId = null;
            importScan(pages, append); return;
        }
        root = column(); root.setBackgroundColor(BG);
        root.setPadding(dp(24), dp(12), dp(24), dp(16));
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()
                | androidx.core.view.WindowInsetsCompat.Type.displayCutout() | androidx.core.view.WindowInsetsCompat.Type.ime());
            view.setPadding(dp(24) + bars.left, dp(12) + bars.top, dp(24) + bars.right, dp(16) + bars.bottom);
            return insets;
        });
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        androidx.core.view.WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightStatusBars(true);
        androidx.core.view.WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightNavigationBars(true);
        setContentView(root); root.requestApplyInsets(); releasePreviews();
        if (session.busy) {
            LinearLayout waiting = column(); waiting.setGravity(Gravity.CENTER);
            root.addView(waiting, new LinearLayout.LayoutParams(-1, -1));
            waiting.addView(new ProgressBar(this)); space(waiting, 24);
            waiting.addView(text(session.status, 20, INK, true));
            waiting.addView(text("Your documents stay on this device.", 14, MUTED, false));
            return;
        }
        if (session.failedLoad) {
            root.addView(text("Library could not be opened", 26, INK, true));
            root.addView(text("Your files have been preserved. Close and reopen the app to retry.", 16, MUTED, false));
        } else if (session.selected == null) home(); else detail();
        if (session.error != null) {
            String error = session.error; session.error = null;
            new AlertDialog.Builder(this).setTitle("Unable to finish").setMessage(error).setPositiveButton("OK", null).show();
        }
        if (session.export != null) {
            File file = session.export; String action = session.exportAction; session.export = null;
            deliverPdf(file, action);
        }
        if (session.showText) { session.showText = false; showText(); }
        if (session.scannerIntent != null) {
            android.content.IntentSender intent = session.scannerIntent; session.scannerIntent = null;
            try { scanner.launch(new IntentSenderRequest.Builder(intent).build()); }
            catch (Exception e) { toast("Scanner could not open. Please try again."); }
        }
    }

    private void home() {
        LinearLayout brand = row();
        TextView logo = text("▣  ScanForge", 23, INK, true); brand.addView(logo, weight());
        brand.addView(button("Settings", false, this::settings)); root.addView(brand);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        content = column(); scroll.addView(content); space(content, 22);
        content.addView(text("LESS PAPER. MORE POSSIBILITY.", 10, GREEN, true)); space(content, 7);
        content.addView(text("Your paperwork,\nbeautifully organized.", 31, INK, true)); space(content, 10);
        content.addView(text("Capture a page. Keep what matters.", 15, MUTED, false)); space(content, 23);
        LinearLayout hero = card(INK); hero.setPadding(dp(22), dp(22), dp(22), dp(22));
        hero.addView(text("A pocket-sized scanning studio", 21, WHITE, true)); space(hero, 8);
        hero.addView(text("Auto crop · Clean up · Multi-page PDF", 14, 0xFFB9D8C9, false)); space(hero, 20);
        hero.addView(button("＋  Scan a document", true, () -> startScan(false)));
        space(hero, 8); hero.addView(text("Camera or gallery  /  up to 50 pages", 12, 0xFFB9D8C8, false));
        content.addView(hero); space(content, 20);
        int count = 0; for (Document d : session.documents) count += d.pages.size();
        LinearLayout stats = row();
        stats.addView(stat(String.valueOf(session.documents.size()), "DOCUMENTS"), weight());
        stats.addView(stat(String.valueOf(count), "PAGES SAVED"), weight());
        stats.addView(stat("Local", "STORAGE"), weight()); content.addView(stats); space(content, 25);
        LinearLayout heading = row(); heading.addView(text("Your library", 22, INK, true), weight());
        heading.addView(button("Sort ↕", false, this::sort)); content.addView(heading);
        EditText search = new EditText(this); search.setSingleLine(true); search.setTextSize(15);
        search.setHint("Search names or extracted text"); search.setText(query);
        search.setPadding(dp(16), dp(8), dp(16), dp(8)); search.setBackground(shape(WHITE, 14));
        content.addView(search, new LinearLayout.LayoutParams(-1, dp(52))); space(content, 10);
        LinearLayout filters = row();
        filters.addView(button(favorites ? "All documents" : "● All documents", false, () -> { favorites = false; render(); }), weight());
        filters.addView(button(favorites ? "★ Favorites" : "☆ Favorites", false, () -> { favorites = true; render(); }), weight());
        content.addView(filters); space(content, 10);
        library = column(); content.addView(library); renderLibrary();
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { query = s.toString(); renderLibrary(); }
            public void afterTextChanged(Editable s) {}
        });
        space(root, 8); TextView footer = text("PRIVATE BY DESIGN    •    NO ACCOUNT NEEDED", 10, MUTED, true);
        footer.setGravity(Gravity.CENTER); root.addView(footer);
    }

    private void renderLibrary() {
        library.removeAllViews();
        ArrayList<Document> docs = new ArrayList<>(session.documents);
        int sort = preferences.getInt("sort", 0);
        docs.sort(sort == 1 ? Comparator.comparing(d -> d.name.toLowerCase(Locale.ROOT)) :
            sort == 2 ? Comparator.comparingLong(d -> d.created) : (a, b) -> Long.compare(b.created, a.created));
        int matches = 0;
        for (Document d : docs) {
            String q = query.toLowerCase(Locale.ROOT);
            if (favorites && !d.favorite || !(d.name + " " + d.text).toLowerCase(Locale.ROOT).contains(q)) continue;
            matches++;
            LinearLayout tile = row(); tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setPadding(dp(16), dp(12), dp(12), dp(12)); tile.setBackground(shape(WHITE, 16));
            TextView icon = text("PDF", 13, GREEN, true); icon.setGravity(Gravity.CENTER);
            icon.setBackground(shape(PALE, 10)); tile.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(58)));
            LinearLayout labels = column(); labels.setPadding(dp(14), 0, dp(4), 0);
            TextView title = text((d.favorite ? "★ " : "") + d.name, 16, INK, true);
            title.setMaxLines(2); title.setEllipsize(TextUtils.TruncateAt.END); labels.addView(title);
            space(labels, 4); labels.addView(text(d.pages.size() + " pages  ·  " + date(d.created) + (d.text.isEmpty() ? "" : "  ·  OCR"), 12, MUTED, false));
            tile.addView(labels, weight()); tile.addView(text("›", 26, GREEN, false));
            tile.setContentDescription("Open " + d.name + ", " + d.pages.size() + " pages");
            tile.setOnClickListener(v -> { session.selected = d; render(); });
            library.addView(tile); space(library, 10);
        }
        if (matches == 0) {
            LinearLayout empty = card(PALE); empty.setGravity(Gravity.CENTER); space(empty, 16);
            empty.addView(text(session.documents.isEmpty() ? "A fresh start for your files" : "No matching documents", 18, INK, true));
            space(empty, 8); TextView caption = text(session.documents.isEmpty() ?
                "Scan receipts, notes, contracts, and more.\nYour first document starts above." :
                "Try another search or switch to all documents.", 14, MUTED, false);
            caption.setGravity(Gravity.CENTER); empty.addView(caption); space(empty, 16); library.addView(empty);
        }
    }

    private void detail() {
        Document doc = session.selected;
        LinearLayout nav = row(); nav.addView(button("‹  Library", false, () -> { session.selected = null; render(); }), weight());
        nav.addView(button(doc.favorite ? "★ Saved" : "☆ Favorite", false, () -> mutate("Updating favorite…", () -> doc.favorite = !doc.favorite)));
        root.addView(nav); space(root, 16);
        TextView title = text(doc.name, 28, INK, true); title.setMaxLines(2); root.addView(title);
        root.addView(text(doc.pages.size() + " pages  ·  " + date(doc.created) + "  ·  Stored on device", 13, MUTED, false)); space(root, 14);
        LinearLayout tools = row(); tools.addView(button("Rename", false, this::rename), weight());
        tools.addView(button("Extract text", false, this::ocr), weight());
        tools.addView(button("More ⋯", false, this::more), weight()); root.addView(tools);
        ScrollView scroll = new ScrollView(this); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout pages = column(); scroll.addView(pages);
        for (int i = 0; i < doc.pages.size(); i++) {
            final int index = i; space(pages, 12);
            LinearLayout page = card(WHITE);
            LinearLayout label = row(); label.addView(text("PAGE " + (i + 1), 11, GREEN, true), weight());
            label.addView(button("Edit page ⋯", false, () -> editPage(index))); page.addView(label);
            ImageView image = new ImageView(this); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setContentDescription("Preview of page " + (i + 1)); image.setBackgroundColor(0xFFEAEBE7);
            page.addView(image, new LinearLayout.LayoutParams(-1, dp(270)));
            // Decode only a small preview off the UI thread; discard results from old screens.
            LinearLayout screen = root;
            session.worker.execute(() -> {
                try {
                    Bitmap bitmap = DocumentStore.bitmap(session.store.page(doc, doc.pages.get(index)), 650);
                    session.main.post(() -> {
                        if (root == screen && !isDestroyed()) { previews.add(bitmap); image.setImageBitmap(bitmap); }
                        else bitmap.recycle();
                    });
                } catch (Exception ignored) {
                    session.main.post(() -> image.setContentDescription("Page preview unavailable"));
                }
            });
            pages.addView(page);
        }
        space(root, 12); LinearLayout actions = row();
        actions.addView(button("＋ Add pages", false, () -> startScan(true)), weight());
        actions.addView(button("Export PDF ↗", true, this::exportMenu), weight()); root.addView(actions);
    }

    private void startScan(boolean append) {
        if (session.busy) return;
        int remaining = append ? 50 - session.selected.pages.size() : 50;
        if (remaining <= 0) { toast("Each document supports up to 50 pages"); return; }
        session.pendingAppendId = append ? session.selected.id : null;
        GmsDocumentScannerOptions options = new GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true).setPageLimit(remaining)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build();
        session.busy = true; session.status = "Preparing scanner…"; render();
        ScanSession state = session;
        GmsDocumentScanning.getClient(options).getStartScanIntent(this)
            .addOnSuccessListener(intent -> {
                state.busy = false; state.scannerIntent = intent; state.notifyUi();
            }).addOnFailureListener(error -> {
                state.busy = false; state.pendingAppendId = null;
                state.error = "Scanning requires Google Play services and at least 1.7 GB of RAM. " +
                    "Connect to the internet for the first scanner download, then try again.\n\n" + error.getLocalizedMessage();
                state.notifyUi();
            });
    }

    private void importScan(List<Uri> uris, String appendId) {
        session.run("Saving scanned pages…", () -> {
            Document target = null;
            if (appendId != null) for (Document d : session.documents) if (d.id.equals(appendId)) target = d;
            boolean isNew = target == null;
            if (isNew) target = new Document("Scan " + new SimpleDateFormat("dd MMM, HH.mm", Locale.getDefault()).format(new Date()));
            List<String> imported = session.store.importPages(target, uris);
            String oldText = target.text;
            target.pages.addAll(imported); target.text = "";
            if (isNew) session.documents.add(target);
            try { session.persist(); }
            catch (Exception e) {
                target.pages.removeAll(imported); target.text = oldText;
                if (isNew) session.documents.remove(target);
                for (String name : imported) session.store.page(target, name).delete();
                throw e;
            }
            session.selected = target;
        });
    }

    private void mutate(String label, ScanSession.Work work) {
        session.run(label, () -> {
            // Restore in-memory state from the atomic index if the write fails.
            String id = session.selected == null ? null : session.selected.id;
            try { work.run(); session.persist(); }
            catch (Exception e) {
                session.documents = session.store.load(); session.selected = null;
                for (Document d : session.documents) if (d.id.equals(id)) session.selected = d;
                throw e;
            }
        });
    }

    private void rename() {
        Document doc = session.selected;
        EditText input = new EditText(this); input.setSingleLine(); input.setText(doc.name); input.selectAll();
        input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(100)});
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Rename document").setView(input)
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) { input.setError("Enter a document name"); return; }
            dialog.dismiss(); mutate("Renaming document…", () -> doc.name = name);
        })); dialog.show();
    }

    private void editPage(int index) {
        Document doc = session.selected;
        ArrayList<String> choices = new ArrayList<>(Arrays.asList("Rotate 90° clockwise", "Enhance grayscale"));
        if (index > 0) choices.add("Move earlier");
        if (index < doc.pages.size() - 1) choices.add("Move later");
        if (doc.pages.size() > 1) choices.add("Remove page");
        new AlertDialog.Builder(this).setTitle("Page " + (index + 1)).setItems(choices.toArray(new String[0]), (d, which) -> {
            String action = choices.get(which);
            if (action.equals("Remove page")) {
                new AlertDialog.Builder(this).setTitle("Remove this page?").setMessage("This removes the page from this document.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Remove", (a, b) -> mutate("Removing page…", () -> {
                        doc.pages.remove(index); doc.text = "";
                    })).show(); return;
            }
            mutate("Updating page…", () -> {
                if (which < 2) doc.pages.set(index, session.store.transform(doc, doc.pages.get(index), which == 0));
                else Collections.swap(doc.pages, index, action.equals("Move earlier") ? index - 1 : index + 1);
                doc.text = "";
            });
        }).show();
    }

    private void ocr() {
        Document doc = session.selected;
        if (!doc.text.isEmpty()) { showText(); return; }
        session.run("Recognizing text on " + doc.pages.size() + " pages…", () -> {
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            try {
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < doc.pages.size(); i++) {
                    Bitmap bitmap = DocumentStore.bitmap(session.store.page(doc, doc.pages.get(i)), 2600);
                    try {
                        InputImage image = InputImage.fromBitmap(bitmap, 0);
                        String text = Tasks.await(recognizer.process(image), 90, TimeUnit.SECONDS).getText();
                        if (!text.trim().isEmpty()) result.append("— Page ").append(i + 1).append(" —\n").append(text).append("\n\n");
                    } finally { bitmap.recycle(); }
                }
                String old = doc.text; doc.text = result.toString().trim();
                try { session.persist(); } catch (Exception e) { doc.text = old; throw e; }
                session.showText = true;
            } finally { recognizer.close(); }
        });
    }

    private void showText() {
        Document doc = session.selected; if (doc == null) return;
        TextView text = text(doc.text.isEmpty() ? "No text was detected. Try a sharper scan with better lighting. Latin-script text is supported." : doc.text, 16, INK, false);
        text.setTextIsSelectable(true); text.setPadding(dp(24), dp(12), dp(24), dp(12));
        ScrollView scroll = new ScrollView(this); scroll.addView(text);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle("Extracted text").setView(scroll).setNegativeButton("Close", null);
        if (!doc.text.isEmpty()) {
            dialog.setPositiveButton("Copy all", (d, w) -> {
                ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(doc.name, doc.text)); toast("Text copied");
            });
            dialog.setNeutralButton("Share text", (d, w) -> startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                .setType("text/plain").putExtra(Intent.EXTRA_TEXT, doc.text), "Share extracted text")));
        }
        dialog.show();
    }

    private void more() {
        new AlertDialog.Builder(this).setTitle("Document actions").setItems(new String[] {"Merge another document", "Delete document"}, (d, which) -> {
            if (which == 0) merge(); else {
                Document doc = session.selected;
                new AlertDialog.Builder(this).setTitle("Delete “" + doc.name + "”?")
                    .setMessage("All pages and extracted text in this document will be permanently removed. Exported copies are kept.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Delete", (a, b) -> session.run("Deleting document…", () -> {
                        session.documents.remove(doc);
                        try { session.persist(); } catch (Exception e) { session.documents.add(doc); throw e; }
                        session.selected = null; session.store.deleteFiles(doc);
                    })).show();
            }
        }).show();
    }

    private void merge() {
        Document target = session.selected;
        ArrayList<Document> options = new ArrayList<>();
        for (Document d : session.documents) if (d != target) options.add(d);
        if (options.isEmpty()) { toast("Scan a second document first"); return; }
        String[] names = new String[options.size()]; for (int i = 0; i < names.length; i++) names[i] = options.get(i).name;
        new AlertDialog.Builder(this).setTitle("Append pages from…").setItems(names, (dialog, which) -> {
            Document source = options.get(which);
            if (source.pages.size() + target.pages.size() > 50) { toast("Merged documents can have up to 50 pages"); return; }
            ArrayList<Uri> uris = new ArrayList<>();
            try { for (String name : source.pages) uris.add(Uri.fromFile(session.store.page(source, name))); }
            catch (IOException e) { toast(e.getMessage()); return; }
            importScan(uris, target.id);
        }).setNegativeButton("Cancel", null).show();
    }

    private void exportMenu() {
        new AlertDialog.Builder(this).setTitle("Export PDF").setItems(new String[] {"Save to device or cloud folder", "Share PDF", "Open PDF / print"}, (d, which) -> {
            Document doc = session.selected;
            session.run("Creating your PDF…", () -> {
                session.export = session.store.export(doc, preferences.getBoolean("letter", false),
                    preferences.getInt("resolution", 2400), preferences.getBoolean("numbers", true));
                session.exportAction = which == 0 ? "save" : which == 1 ? "share" : "open";
            });
        }).show();
    }

    private void deliverPdf(File file, String action) {
        try {
            if ("save".equals(action)) {
                savingPath = file.getAbsolutePath(); savePdf.launch(PdfLayout.safeName(session.selected.name) + ".pdf"); return;
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
            Intent intent;
            if ("share".equals(action)) intent = new Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri);
            else intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("PDF", uri));
            startActivity(Intent.createChooser(intent, "share".equals(action) ? "Share PDF" : "Open PDF (print from your viewer)"));
        } catch (ActivityNotFoundException e) { toast("Install a PDF viewer, or use Save to device"); }
    }

    private void sort() {
        new AlertDialog.Builder(this).setTitle("Sort documents").setSingleChoiceItems(new String[] {"Newest first", "Name A–Z", "Oldest first"},
            preferences.getInt("sort", 0), (dialog, which) -> { preferences.edit().putInt("sort", which).apply(); dialog.dismiss(); render(); }).show();
    }

    private void settings() {
        LinearLayout panel = column(); panel.setPadding(dp(24), dp(12), dp(24), dp(12));
        panel.addView(text("PDF DEFAULTS", 11, GREEN, true)); space(panel, 12);
        CheckBox paper = new CheckBox(this); paper.setText(R.string.paper_size); paper.setChecked(preferences.getBoolean("letter", false));
        panel.addView(paper, new LinearLayout.LayoutParams(-1, dp(52)));
        CheckBox numbers = new CheckBox(this); numbers.setText(R.string.page_numbers); numbers.setChecked(preferences.getBoolean("numbers", true));
        panel.addView(numbers, new LinearLayout.LayoutParams(-1, dp(52))); space(panel, 12);
        panel.addView(text("Image quality", 15, INK, true));
        RadioGroup quality = new RadioGroup(this);
        String[] labels = {"Compact · 1200 px", "Balanced · 1800 px", "High quality · 2400 px"}; int[] values = {1200, 1800, 2400};
        int[] selectedQuality = {preferences.getInt("resolution", 2400)};
        for (int i = 0; i < 3; i++) {
            int value = values[i]; RadioButton r = new RadioButton(this); r.setId(View.generateViewId());
            r.setText(labels[i]); quality.addView(r);
            r.setOnCheckedChangeListener((button, checked) -> { if (checked) selectedQuality[0] = value; });
            if (selectedQuality[0] == value) quality.check(r.getId());
        }
        panel.addView(quality); space(panel, 18);
        panel.addView(text("Scans and OCR stay in app storage. No accounts, ads, or analytics. Uninstalling removes your library; export important PDFs first. The scanner requires Google Play services and a first-use download. OCR supports Latin script. PDFs contain page images; extracted text can be copied or shared separately.", 13, MUTED, false));
        ScrollView scroll = new ScrollView(this); scroll.addView(panel);
        new AlertDialog.Builder(this).setTitle("Settings & privacy").setView(scroll).setNegativeButton("Cancel", null)
            .setPositiveButton("Save", (d, w) -> preferences.edit().putBoolean("letter", paper.isChecked())
                .putBoolean("numbers", numbers.isChecked()).putInt("resolution", selectedQuality[0]).apply()).show();
    }

    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private LinearLayout card(int color) { LinearLayout v = column(); v.setPadding(dp(16), dp(12), dp(16), dp(16)); v.setBackground(shape(color, 20)); return v; }
    private LinearLayout stat(String value, String label) { LinearLayout v = column(); v.addView(text(value, 23, INK, true)); space(v, 3); v.addView(text(label, 9, MUTED, true)); return v; }
    private TextView text(String value, int size, int color, boolean bold) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setFontFeatureSettings("kern"); t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL)); t.setLineSpacing(dp(3), 1); return t; }
    private Button button(String label, boolean primary, Runnable action) { Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(13); b.setTextColor(primary ? WHITE : GREEN); b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48)); b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(dp(12), dp(6), dp(12), dp(6)); b.setBackgroundTintList(ColorStateList.valueOf(primary ? GREEN : PALE)); b.setOnClickListener(v -> action.run()); return b; }
    private GradientDrawable shape(int color, int radius) { GradientDrawable s = new GradientDrawable(); s.setColor(color); s.setCornerRadius(dp(radius)); return s; }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, -2, 1); }
    private void space(LinearLayout parent, int height) { View v = new View(this); parent.addView(v, new LinearLayout.LayoutParams(1, dp(height))); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private String date(long time) { return new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new Date(time)); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private void releasePreviews() { for (Bitmap b : previews) if (!b.isRecycled()) b.recycle(); previews.clear(); }
}
