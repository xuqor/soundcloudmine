package xuqor.sound.client.api;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;

public final class SoundCloudApi {
    public record SearchResult(String kind, String title, String subtitle, String permalink, Track track) {
        public boolean playable() { return track != null && track.playable(); }
    }
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private volatile String clientId = "";
    public void setClientId(String id) { clientId = id == null ? "" : id.trim(); }
    private static String encode(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private HttpRequest request(String url) {
        URI uri = URI.create(url);
        if (!List.of("https", "http").contains(uri.getScheme())) throw new IllegalArgumentException("Только HTTP/HTTPS");
        return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                .header("User-Agent", "SoundCloudMine/1.0").GET().build();
    }
    public InputStream open(String url) throws IOException, InterruptedException {
        var response = http.send(request(url), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() / 100 != 2) {
            response.body().close();
            throw new IOException("HTTP " + response.statusCode() + ": источник недоступен");
        }
        return response.body();
    }
    public byte[] bytes(String url, int limit) throws IOException, InterruptedException {
        try (InputStream in = open(url)) {
            byte[] b = in.readNBytes(limit + 1);
            if (b.length > limit) throw new IOException("Ответ сервера слишком большой");
            return b;
        }
    }
    private String text(String url) throws IOException, InterruptedException {
        return new String(bytes(url, 6_000_000), StandardCharsets.UTF_8);
    }
    private synchronized String id() throws IOException, InterruptedException {
        if (!clientId.isBlank()) return clientId;
        // Public web application id, NOT a listener account token. No access restrictions are bypassed.
        String html = text("https://soundcloud.com");
        Matcher scripts = Pattern.compile("<script[^>]+src=\"(https://a-v2\\.sndcdn\\.com/[^\"]+)\"").matcher(html);
        List<String> urls = new ArrayList<>();
        while (scripts.find()) urls.add(scripts.group(1));
        Collections.reverse(urls);
        Pattern pattern = Pattern.compile("client_id\\s*[:=]\\s*[\"']([A-Za-z0-9]{32})[\"']");
        for (String url : urls.stream().limit(14).toList()) {
            Matcher m = pattern.matcher(text(url));
            if (m.find()) { clientId = m.group(1); return clientId; }
        }
        throw new IOException("SoundCloud изменил веб-API. Укажите client_id в настройках.");
    }
    private JsonObject api(String path) throws IOException, InterruptedException {
        String key = id();
        String url = path.startsWith("https://") ? path : "https://api-v2.soundcloud.com/" + path;
        if (!URI.create(url).getHost().equals("api-v2.soundcloud.com")) throw new IOException("Неверный хост API");
        String raw = text(url + (url.contains("?") ? "&" : "?") + "client_id=" + encode(key));
        return JsonParser.parseString(raw).getAsJsonObject();
    }
    public List<Track> find(String query) throws IOException, InterruptedException {
        String q = query.trim();
        if (q.isEmpty()) return List.of();
        if (q.startsWith("http://") || q.startsWith("https://")) {
            String host = URI.create(q).getHost();
            if (host == null) throw new IOException("Некорректная ссылка");
            if (!(host.equals("soundcloud.com") || host.endsWith(".soundcloud.com") ||
                    host.equals("on.soundcloud.com") || host.endsWith(".snd.sc"))) return List.of(Track.direct(q));
            JsonObject resolved = api("resolve?url=" + encode(q));
            if ("playlist".equals(str(resolved, "kind", ""))) {
                List<Track> tracks = new ArrayList<>();
                for (JsonElement el : resolved.getAsJsonArray("tracks")) {
                    JsonObject t = el.getAsJsonObject();
                    if (!t.has("media")) t = api("tracks/" + t.get("id").getAsLong());
                    if (playable(t)) tracks.add(parse(t));
                }
                return tracks;
            }
            if (!playable(resolved)) throw new IOException("Трек закрыт, платный или недоступен для полного воспроизведения");
            return List.of(parse(resolved));
        }
        List<Track> tracks = new ArrayList<>();
        for (SearchResult item : findResults(q)) if (item.track() != null) tracks.add(item.track());
        return tracks;
    }
    /** Searches the public result types separately because the API exposes typed search endpoints. */
    public List<SearchResult> findResults(String query) throws IOException, InterruptedException {
        String q = query.trim();
        if (q.isEmpty()) return List.of();
        if (q.startsWith("http://") || q.startsWith("https://"))
            return find(q).stream().map(t -> new SearchResult("track", t.title(), t.artist(), t.permalink(), t)).toList();
        List<SearchResult> out = new ArrayList<>();
        addTracks(out, searchPages("tracks", q));
        addUsers(out, searchPages("users", q));
        addPlaylists(out, searchPages("playlists", q));
        return out;
    }
    private List<JsonObject> searchPages(String type, String query) throws IOException, InterruptedException {
        List<JsonObject> items = new ArrayList<>();
        String path = "search/" + type + "?q=" + encode(query);
        while (path != null && !path.isBlank()) {
            JsonObject page = api(path);
            for (JsonElement el : page.getAsJsonArray("collection")) items.add(el.getAsJsonObject());
            path = page.has("next_href") && !page.get("next_href").isJsonNull() ? page.get("next_href").getAsString() : null;
        }
        return items;
    }
    private void addTracks(List<SearchResult> out, List<JsonObject> items) {
        for (JsonObject t : items) {
            if (playable(t)) { Track track = parse(t); out.add(new SearchResult("track", track.title(), track.artist(), track.permalink(), track)); }
        }
    }
    private void addUsers(List<SearchResult> out, List<JsonObject> items) {
        for (JsonObject u : items) {
            out.add(new SearchResult("account", str(u, "username", "Account"),
                    str(u, "track_count", "") + " tracks", str(u, "permalink_url", ""),
                    Track.searchItem(u.get("id").getAsLong(), "account", str(u, "username", "Account"), "Account", str(u, "permalink_url", ""))));
        }
    }
    private void addPlaylists(List<SearchResult> out, List<JsonObject> items) {
        for (JsonObject p : items) {
            out.add(new SearchResult("playlist", str(p, "title", "Playlist"),
                    str(p.getAsJsonObject("user"), "username", "SoundCloud"), str(p, "permalink_url", ""),
                    Track.searchItem(p.get("id").getAsLong(), "playlist", str(p, "title", "Playlist"), "Playlist", str(p, "permalink_url", ""))));
        }
    }
    private static boolean playable(JsonObject t) {
        String policy = str(t, "policy", "ALLOW");
        return "track".equals(str(t, "kind", "")) && !policy.equals("BLOCK") && !policy.equals("SNIP")
                && t.has("media") && t.getAsJsonObject("media").has("transcodings");
    }
    public static Track parse(JsonObject t) {
        List<Track.Stream> streams = new ArrayList<>();
        for (JsonElement el : t.getAsJsonObject("media").getAsJsonArray("transcodings")) {
            JsonObject s = el.getAsJsonObject(), f = s.getAsJsonObject("format");
            if (s.has("snipped") && s.get("snipped").getAsBoolean()) continue;
            if ("audio/mpeg".equals(str(f, "mime_type", "")))
                streams.add(new Track.Stream(str(s, "url", ""), str(f, "protocol", ""), "audio/mpeg"));
        }
        streams.sort(Comparator.comparing(s -> !"progressive".equals(s.protocol())));
        return new Track(t.get("id").getAsLong(), str(t, "title", "Untitled"),
                str(t.getAsJsonObject("user"), "username", "SoundCloud"),
                str(t, "permalink_url", ""), str(t, "artwork_url", ""),
                t.has("duration") ? t.get("duration").getAsLong() : 0, List.copyOf(streams));
    }
    public InputStream audio(Track t) throws IOException, InterruptedException {
        Exception last = null;
        for (Track.Stream stream : t.streams()) {
            try {
                if ("direct".equals(stream.protocol())) return open(stream.endpoint());
                String url = api(stream.endpoint()).get("url").getAsString();
                if ("progressive".equals(stream.protocol())) return open(url);
                if ("hls".equals(stream.protocol())) return new HlsInputStream(this, url);
            } catch (IOException ex) { last = ex; }
        }
        throw new IOException("Нет доступного MP3-потока (возможен AAC/платный трек)",
                last);
    }
    private static String str(JsonObject o, String key, String fallback) {
        return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback;
    }
}
