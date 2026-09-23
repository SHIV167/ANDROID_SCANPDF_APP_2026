package com.scanforge.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class Document {
    String id = UUID.randomUUID().toString();
    String name;
    long created = System.currentTimeMillis();
    boolean favorite;
    String text = "";
    final List<String> pages = new ArrayList<>();

    Document(String name) { this.name = name; }

    JSONObject json() throws JSONException {
        return new JSONObject().put("id", id).put("name", name).put("created", created)
            .put("favorite", favorite).put("text", text).put("pages", new JSONArray(pages));
    }

    static Document from(JSONObject o) throws JSONException {
        Document d = new Document(o.getString("name"));
        d.id = o.getString("id");
        d.created = o.getLong("created");
        d.favorite = o.optBoolean("favorite");
        d.text = o.optString("text", "");
        JSONArray pages = o.getJSONArray("pages");
        for (int i = 0; i < pages.length(); i++) d.pages.add(pages.getString(i));
        return d;
    }
}
