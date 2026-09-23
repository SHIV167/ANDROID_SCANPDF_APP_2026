package com.scanforge.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.ViewModel;
import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ScanSession extends ViewModel {
    final ExecutorService worker = Executors.newSingleThreadExecutor();
    final Handler main = new Handler(Looper.getMainLooper());
    final DocumentStore store;
    List<Document> documents = new ArrayList<>();
    Document selected;
    Runnable listener;
    boolean busy, initialized, failedLoad;
    String status = "", error, pendingAppendId;
    File export;
    String exportAction;
    boolean showText;
    android.content.IntentSender scannerIntent;
    List<android.net.Uri> capturedPages;
    String capturedAppendId;
    android.net.Uri saveDestination;
    String saveSource;

    ScanSession(Context context) { store = new DocumentStore(context); }
    interface Work { void run() throws Exception; }

    void run(String label, Work work) {
        if (busy) return;
        busy = true; status = label; notifyUi();
        worker.execute(() -> {
            String failure = null;
            try { work.run(); }
            catch (Exception e) { failure = e.getMessage() == null ? "Operation could not be completed" : e.getMessage(); }
            String finalFailure = failure;
            main.post(() -> { busy = false; error = finalFailure; notifyUi(); });
        });
    }

    void initialize() {
        if (initialized) return;
        initialized = true;
        run("Opening your library…", () -> {
            try { documents = store.load(); }
            catch (Exception e) { failedLoad = true; throw e; }
        });
    }

    void persist() throws Exception { store.save(documents); }
    void notifyUi() { if (listener != null) listener.run(); }
    @Override protected void onCleared() { worker.shutdown(); }
}
