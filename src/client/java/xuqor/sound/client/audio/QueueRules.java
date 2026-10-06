package xuqor.sound.client.audio;

import java.util.random.RandomGenerator;

public final class QueueRules {
    public enum Repeat { OFF, ALL, ONE;
        public Repeat next() { return values()[(ordinal() + 1) % values().length]; }
    }
    public static int next(int current, int count, Repeat repeat, boolean shuffle,
                           boolean naturalEnd, RandomGenerator random) {
        if (count == 0) return -1;
        if (naturalEnd && repeat == Repeat.ONE && current >= 0) return current;
        if (shuffle && count > 1) {
            int n = random.nextInt(count - 1);
            return n >= current && current >= 0 ? n + 1 : n;
        }
        int n = current + 1;
        return n < count ? n : repeat == Repeat.ALL ? 0 : -1;
    }
}
