package xuqor.sound.client.audio;

import xuqor.sound.client.api.*;
import javazoom.jl.decoder.*;
import javax.sound.sampled.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class StreamingPlayer implements AutoCloseable {
    public enum State { IDLE, LOADING, PLAYING, PAUSED, ERROR }
    private final SoundCloudApi api;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SoundCloudMine-audio"); t.setDaemon(true); return t;
    });
    private Future<?> task;
    private final List<Track> queue = new ArrayList<>();
    private volatile Session session;
    private volatile State state = State.IDLE;
    private volatile String message = "Вставьте ссылку или найдите музыку";
    private volatile float volume = 0.65f;
    private volatile int index = -1;
    private volatile long positionMs;
    private QueueRules.Repeat repeat = QueueRules.Repeat.OFF;
    private boolean shuffle, closed;
    public StreamingPlayer(SoundCloudApi api) { this.api = api; }
    public synchronized List<Track> queue() { return List.copyOf(queue); }
    public synchronized Track track() { return index >= 0 && index < queue.size() ? queue.get(index) : null; }
    public State state() { return state; }
    public String message() { return message; }
    public long positionMs() { return positionMs; }
    public float volume() { return volume; }
    public void volume(float value) { volume = Math.clamp(value, 0f, 1f); }
    public float gain() { return volume <= 0f ? 0f : (float)Math.pow(10d, (volume - 1d) * 2d); }
    public int index() { return index; }
    public synchronized QueueRules.Repeat repeat() { return repeat; }
    public synchronized void cycleRepeat() { repeat = repeat.next(); }
    public synchronized boolean shuffle() { return shuffle; }
    public synchronized void toggleShuffle() { shuffle = !shuffle; }
    public synchronized void add(List<Track> tracks) {
        Set<String> existing = new HashSet<>();
        for (Track track : queue) existing.add(track.key());
        for (Track track : tracks) if (existing.add(track.key())) queue.add(track);
        if (index < 0 && !queue.isEmpty()) play(0, 0);
    }
    public synchronized void remove(int i) {
        if (i < 0 || i >= queue.size()) return;
        boolean current = i == index;
        if (current) stop();
        queue.remove(i);
        if (i < index) index--;
        if (current && !queue.isEmpty()) play(Math.min(i, queue.size() - 1), 0);
    }
    public synchronized void clear() { stop(); queue.clear(); }
    public synchronized void play(int i) { play(i, 0); }
    private synchronized void play(int i, long seekMs) {
        if (closed || i < 0 || i >= queue.size()) return;
        cancel();
        index = i; positionMs = seekMs; state = State.LOADING; message = "Подключение к потоку…";
        Session next = new Session(queue.get(i), seekMs);
        session = next;
        task = worker.submit(() -> decode(next));
    }
    public synchronized void seek(double fraction) {
        Track t = track();
        if (t != null && t.durationMs() > 0) play(index, (long)(Math.clamp(fraction, 0d, 0.999d) * t.durationMs()));
    }
    public synchronized void togglePause() {
        Session s = session;
        if (s == null) { if (!queue.isEmpty()) play(index < 0 ? 0 : index, 0); return; }
        if (state == State.LOADING) return;
        s.paused = !s.paused;
        state = s.paused ? State.PAUSED : State.PLAYING;
        SourceDataLine line = s.line;
        if (line != null) { if (s.paused) line.stop(); else line.start(); }
    }
    public synchronized void next() { advance(false); }
    private synchronized void advance(boolean natural) {
        int n = QueueRules.next(index, queue.size(), repeat, shuffle, natural, ThreadLocalRandom.current());
        if (n < 0) stop(); else play(n, 0);
    }
    public synchronized void previous() {
        if (positionMs > 3000 && index >= 0) play(index, 0);
        else if (!queue.isEmpty()) play(index <= 0 ? queue.size() - 1 : index - 1, 0);
    }
    public synchronized void stop() { cancel(); index = -1; positionMs = 0; state = State.IDLE; }
    private void cancel() {
        if (task != null) { task.cancel(false); task = null; }
        Session old = session; session = null; if (old != null) old.close();
    }
    private void decode(Session s) {
        boolean ended = false;
        try {
            InputStream remote = api.audio(s.track);
            if (!s.attach(remote)) return;
            try (BufferedInputStream buffered = new BufferedInputStream(remote, 64 * 1024)) {
                Bitstream bits = new Bitstream(buffered);
                Decoder decoder = new Decoder();
                byte[] pcm = new byte[9216];
                long decodedSamples = 0;
                int rate = 44100, channels = 2;
                try {
                    while (!s.cancelled) {
                        while (s.paused && !s.cancelled) Thread.sleep(15);
                        if (s.cancelled) break;
                        Header header = bits.readFrame();
                        if (header == null) { ended = true; break; }
                        SampleBuffer samples;
                        try { samples = (SampleBuffer)decoder.decodeFrame(header, bits); }
                        finally { bits.closeFrame(); }
                        rate = samples.getSampleFrequency(); channels = samples.getChannelCount();
                        int length = samples.getBufferLength();
                        long frameStart = decodedSamples * 1000 / rate;
                        decodedSamples += length / channels;
                        if (frameStart < s.seekMs) continue; // Bounded-memory seek; re-decodes from beginning.
                        if (s.line == null) {
                            AudioFormat format = new AudioFormat(rate, 16, channels, true, false);
                            SourceDataLine line = AudioSystem.getSourceDataLine(format);
                            line.open(format, rate * channels * 2 / 4); // about 250ms PCM.
                            if (!s.attach(line)) return;
                            line.start();
                            synchronized (this) {
                                if (session == s) { state = State.PLAYING; message = ""; }
                            }
                        }
                        short[] data = samples.getBuffer();
                        float gain = gain();
                        for (int k = 0; k < length; k++) {
                            short value = (short)(data[k] * gain);
                            pcm[2*k] = (byte)value; pcm[2*k+1] = (byte)(value >> 8);
                        }
                        int off = 0;
                        while (off < length * 2 && !s.cancelled) {
                            while (s.paused && !s.cancelled) Thread.sleep(15);
                            if (s.cancelled) break;
                            off += s.line.write(pcm, off, Math.min(2048, length * 2 - off));
                        }
                        if (session == s) positionMs = s.seekMs + s.line.getMicrosecondPosition() / 1000;
                    }
                    if (ended && !s.cancelled && s.line != null) s.line.drain();
                } finally { bits.close(); }
            }
        } catch (Exception ex) {
            synchronized (this) {
                if (!s.cancelled && session == s) {
                    state = State.ERROR;
                    message = ex instanceof LineUnavailableException ? "Аудиоустройство недоступно" :
                            "Ошибка: " + Objects.toString(ex.getMessage(), ex.getClass().getSimpleName());
                }
            }
        } finally {
            s.close();
            synchronized (this) {
                if (session == s) {
                    session = null;
                    if (ended && !closed) advance(true);
                }
            }
        }
    }
    @Override public synchronized void close() {
        closed = true; cancel(); worker.shutdownNow(); queue.clear();
    }
    private static final class Session {
        final Track track;
        final long seekMs;
        volatile boolean cancelled, paused;
        volatile InputStream stream;
        volatile SourceDataLine line;
        Session(Track track, long seekMs) { this.track = track; this.seekMs = seekMs; }
        synchronized boolean attach(InputStream s) throws IOException {
            if (cancelled) { s.close(); return false; } stream = s; return true;
        }
        synchronized boolean attach(SourceDataLine l) {
            if (cancelled) { l.close(); return false; } line = l; return true;
        }
        synchronized void close() {
            cancelled = true; paused = false;
            if (line != null) { line.stop(); line.flush(); line.close(); line = null; }
            if (stream != null) { try { stream.close(); } catch (IOException ignored) {} stream = null; }
        }
    }
}
