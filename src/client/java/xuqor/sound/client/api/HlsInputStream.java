package xuqor.sound.client.api;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Concatenates one unencrypted MP3 segment at a time. Never caches audio to disk. */
public final class HlsInputStream extends InputStream {
    private final SoundCloudApi api;
    private final Iterator<String> segments;
    private volatile InputStream current;
    private volatile boolean closed;
    public HlsInputStream(SoundCloudApi api, String url) throws IOException, InterruptedException {
        this.api = api;
        List<String> list = playlist(api, url, 0);
        segments = list.iterator();
    }
    private static List<String> playlist(SoundCloudApi api, String url, int depth) throws IOException, InterruptedException {
        if (depth > 3) throw new IOException("Слишком много HLS-плейлистов");
        String body = new String(api.bytes(url, 1_000_000), StandardCharsets.UTF_8);
        if (body.contains("#EXT-X-KEY") && !body.contains("METHOD=NONE"))
            throw new IOException("Зашифрованный HLS не поддерживается");
        List<String> urls = new ArrayList<>();
        for (String line : body.split("\\R"))
            if (!line.isBlank() && !line.startsWith("#")) urls.add(URI.create(url).resolve(line.trim()).toString());
        if (body.contains("#EXT-X-STREAM-INF") && !urls.isEmpty()) return playlist(api, urls.getFirst(), depth + 1);
        if (!body.contains("#EXT-X-ENDLIST")) throw new IOException("Live HLS не поддерживается");
        if (body.contains("#EXT-X-BYTERANGE")) throw new IOException("HLS byte-range не поддерживается; нужен progressive MP3");
        return urls;
    }
    @Override public int read() throws IOException {
        byte[] b = new byte[1]; return read(b, 0, 1) < 0 ? -1 : b[0] & 255;
    }
    @Override public int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) return 0;
        while (!closed) {
            if (current == null) {
                if (!segments.hasNext()) return -1;
                try {
                    InputStream opened = api.open(segments.next());
                    if (closed) { opened.close(); return -1; }
                    current = opened;
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
            }
            int n = current.read(b, off, len);
            if (n >= 0) return n;
            current.close(); current = null;
        }
        return -1;
    }
    @Override public void close() throws IOException {
        closed = true;
        InputStream in = current;
        if (in != null) in.close();
    }
}
