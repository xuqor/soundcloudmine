package xuqor.sound.client.api;

import java.util.List;

public record Track(long id, String title, String artist, String permalink, String artwork,
                    long durationMs, List<Stream> streams) {
    public record Stream(String endpoint, String protocol, String mime) {}
    public static Track direct(String url) {
        return new Track(url.hashCode(), "Direct MP3 stream", "HTTP audio", url, "", 0,
                List.of(new Stream(url, "direct", "audio/mpeg")));
    }

    public String key() { return permalink.isBlank() ? Long.toString(id) : permalink; }
    public boolean playable() { return !streams.isEmpty(); }
    public static Track searchItem(long id, String kind, String title, String subtitle, String permalink) {
        return new Track(id, "[" + kind + "] " + title, subtitle, permalink, "", 0, List.of());
    }
}
