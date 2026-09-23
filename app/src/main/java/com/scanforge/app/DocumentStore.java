package com.scanforge.app;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.util.AtomicFile;
import org.json.JSONArray;
import java.io.*;
import java.util.*;

final class DocumentStore {
    private final Context context;
    private final File root;
    private final AtomicFile index;

    DocumentStore(Context context) {
        this.context = context.getApplicationContext();
        root = new File(context.getFilesDir(), "documents");
        root.mkdirs();
        index = new AtomicFile(new File(root, "index.json"));
    }

    synchronized List<Document> load() throws Exception {
        List<Document> result = new ArrayList<>();
        if (!index.getBaseFile().exists() && !new File(root, "index.json.bak").exists()) return result;
        JSONArray array = new JSONArray(new String(index.readFully(), java.nio.charset.StandardCharsets.UTF_8));
        for (int i = 0; i < array.length(); i++) result.add(Document.from(array.getJSONObject(i)));
        return result;
    }

    synchronized void save(List<Document> documents) throws Exception {
        JSONArray array = new JSONArray();
        for (Document d : documents) array.put(d.json());
        FileOutputStream out = index.startWrite();
        try {
            out.write(array.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            index.finishWrite(out);
        } catch (Exception e) { index.failWrite(out); throw e; }
    }

    File page(Document document, String name) throws IOException {
        File folder = new File(root, document.id);
        File file = new File(folder, name);
        if (!folder.getCanonicalFile().getParentFile().equals(root.getCanonicalFile()) ||
            !file.getCanonicalFile().getParentFile().equals(folder.getCanonicalFile())) {
            throw new IOException("Invalid document path");
        }
        return file;
    }

    List<String> importPages(Document document, List<Uri> uris) throws Exception {
        List<String> imported = new ArrayList<>();
        try {
            for (Uri uri : uris) {
                String name = UUID.randomUUID() + ".jpg";
                File file = page(document, name);
                file.getParentFile().mkdirs();
                imported.add(name);
                try (InputStream in = context.getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(file)) {
                    if (in == null) throw new IOException("Cannot read selected image");
                    byte[] buffer = new byte[32768];
                    int count;
                    while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                }
                Bitmap probe = bitmap(file, 64);
                probe.recycle();
            }
            return imported;
        } catch (Exception e) {
            for (String name : imported) page(document, name).delete();
            throw e;
        }
    }

    static Bitmap bitmap(File file, int maxDimension) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > maxDimension * 2) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap decoded = BitmapFactory.decodeFile(file.getPath(), options);
        if (decoded == null) throw new IOException("This image cannot be opened");
        float ratio = Math.min(1f, (float) maxDimension / Math.max(decoded.getWidth(), decoded.getHeight()));
        if (ratio < 1f) {
            Bitmap scaled = Bitmap.createScaledBitmap(decoded, Math.max(1, Math.round(decoded.getWidth() * ratio)),
                Math.max(1, Math.round(decoded.getHeight() * ratio)), true);
            if (scaled != decoded) decoded.recycle();
            return scaled;
        }
        return decoded;
    }

    // Edits create a new page file, so a failed metadata save cannot damage the original.
    String transform(Document document, String name, boolean rotate) throws Exception {
        Bitmap source = bitmap(page(document, name), 2600);
        Bitmap output = null;
        String next = UUID.randomUUID() + ".jpg";
        File file = page(document, next);
        try {
            if (rotate) {
                Matrix matrix = new Matrix(); matrix.postRotate(90);
                output = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
            } else {
                output = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
                ColorMatrix gray = new ColorMatrix(); gray.setSaturation(0);
                ColorMatrix contrast = new ColorMatrix(new float[] {
                    1.35f,0,0,0,-30, 0,1.35f,0,0,-30, 0,0,1.35f,0,-30, 0,0,0,1,0});
                contrast.postConcat(gray);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setColorFilter(new ColorMatrixColorFilter(contrast));
                new Canvas(output).drawBitmap(source, 0, 0, paint);
            }
            try (OutputStream out = new FileOutputStream(file)) {
                if (!output.compress(Bitmap.CompressFormat.JPEG, 94, out)) throw new IOException("Image edit failed");
            }
            return next;
        } catch (Exception e) { file.delete(); throw e; }
        finally { if (output != null && output != source) output.recycle(); source.recycle(); }
    }

    File export(Document document, boolean letter, int resolution, boolean pageNumbers) throws Exception {
        File exports = new File(context.getCacheDir(), "exports");
        exports.mkdirs();
        File destination = new File(exports, PdfLayout.safeName(document.name) + "-" + UUID.randomUUID() + ".pdf");
        try (PdfWriter pdf = new PdfWriter(destination, document.pages.size())) {
            for (int i = 0; i < document.pages.size(); i++) {
                Bitmap image = bitmap(page(document, document.pages.get(i)), resolution);
                try {
                    int width = letter ? 612 : 595, height = letter ? 792 : 842;
                    ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
                    if (!image.compress(Bitmap.CompressFormat.JPEG, resolution <= 1200 ? 75 : 90, jpeg))
                        throw new IOException("Could not encode PDF page");
                    pdf.addPage(jpeg.toByteArray(), image.getWidth(), image.getHeight(), width, height, pageNumbers);
                } finally { image.recycle(); }
            }
            pdf.finish();
            return destination;
        } catch (Exception e) { destination.delete(); throw e; }
    }

    void deleteFiles(Document document) throws IOException {
        for (String name : document.pages) page(document, name).delete();
        // Retired edit versions are retained until the whole document is deleted.
        File dir = new File(root, document.id);
        File[] children = dir.listFiles();
        if (children != null) for (File child : children) if (child.isFile()) child.delete();
        dir.delete();
    }
}
