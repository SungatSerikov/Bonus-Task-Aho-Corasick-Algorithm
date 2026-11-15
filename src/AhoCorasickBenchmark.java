import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

public final class AhoCorasickBenchmark {
    private static final int REPETITIONS = 5;
    private static final int WARMUPS = 10;
    private static final long SEED = 20261115L;
    private static volatile int sink;
    private record Dataset(List<String> patterns, String text) {}
    private record Timing(long build, long search, List<AhoCorasick.Match> matches) {
        long total() { return build + search; }
    }

    public static void run() throws IOException {
        AlgorithmTests.run();
        Locale.setDefault(Locale.ROOT);
        Path destination = Path.of("results");
        Files.createDirectories(destination);
        StringBuilder raw = new StringBuilder("patterns,text_length,run,algorithm,build_ns,search_ns,total_ns,matches\n");
        StringBuilder summary = new StringBuilder("patterns,text_length,ac_build_ms,ac_search_ms,ac_total_ms,kmp_build_ms,kmp_search_ms,kmp_total_ms,total_speedup,matches\n");
        StringBuilder table = new StringBuilder("# Measured results\n\nTimes are arithmetic means of five runs, in milliseconds. Speedup = KMP total / AC total.\n\n")
                .append("| Patterns | Text | AC build | AC search | AC total | KMP build | KMP search | KMP total | Speedup | Matches |\n")
                .append("|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (int count : new int[] {10, 100, 1000}) {
            for (int length : new int[] {1000, 10000, 100000}) {
                Dataset data = generate(count, length);
                for (int i = 0; i < WARMUPS; i++) {
                    runAc(data);
                    runKmp(data);
                }
                long[] totals = new long[6];
                int matches = 0;
                for (int run = 1; run <= REPETITIONS; run++) {
                    Timing ac, kmp;
                    if (run % 2 == 1) { ac = runAc(data); kmp = runKmp(data); }
                    else { kmp = runKmp(data); ac = runAc(data); }
                    AlgorithmTests.equal(ac.matches(), kmp.matches());
                    matches = ac.matches().size();
                    appendRaw(raw, count, length, run, "AC", ac);
                    appendRaw(raw, count, length, run, "KMP", kmp);
                    totals[0] += ac.build(); totals[1] += ac.search(); totals[2] += ac.total();
                    totals[3] += kmp.build(); totals[4] += kmp.search(); totals[5] += kmp.total();
                }
                double scale = REPETITIONS * 1_000_000.0;
                double speedup = (double) totals[5] / totals[2];
                summary.append(String.format("%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.3f,%d%n",
                        count, length, totals[0]/scale, totals[1]/scale, totals[2]/scale,
                        totals[3]/scale, totals[4]/scale, totals[5]/scale, speedup, matches));
                table.append(String.format("| %d | %d | %.4f | %.4f | %.4f | %.4f | %.4f | %.4f | %.2fx | %d |%n",
                        count, length, totals[0]/scale, totals[1]/scale, totals[2]/scale,
                        totals[3]/scale, totals[4]/scale, totals[5]/scale, speedup, matches));
                System.out.printf("%4d patterns, %6d chars: AC %.4f ms, KMP %.4f ms, %.2fx%n",
                        count, length, totals[2]/scale, totals[5]/scale, speedup);
            }
        }
        Files.writeString(destination.resolve("raw.csv"), raw);
        Files.writeString(destination.resolve("summary.csv"), summary);
        Files.writeString(destination.resolve("RESULTS.md"), table);

    }

    private static Timing runAc(Dataset data) {
        long start = System.nanoTime();
        AhoCorasick matcher = new AhoCorasick(data.patterns());
        long built = System.nanoTime();
        List<AhoCorasick.Match> matches = matcher.search(data.text());
        long end = System.nanoTime();
        sink = matches.size();
        return new Timing(built - start, end - built, matches);
    }

    private static Timing runKmp(Dataset data) {
        long start = System.nanoTime();
        KmpMatcher matcher = new KmpMatcher(data.patterns());
        long built = System.nanoTime();
        List<AhoCorasick.Match> matches = matcher.search(data.text());
        long end = System.nanoTime();
        sink = matches.size();
        return new Timing(built - start, end - built, matches);
    }

    private static void appendRaw(StringBuilder raw, int count, int length, int run, String name, Timing t) {
        raw.append(String.format("%d,%d,%d,%s,%d,%d,%d,%d%n", count, length, run, name,
                t.build(), t.search(), t.total(), t.matches().size()));
    }

    private static Dataset generate(int count, int length) {
        Random random = new Random(SEED + count * 1_000_003L + length);
        Set<String> unique = new LinkedHashSet<>();
        while (unique.size() < count) unique.add(randomString(random, 5 + random.nextInt(8)));
        List<String> patterns = new ArrayList<>(unique);
        StringBuilder text = new StringBuilder(randomString(random, length));
        for (int i = 0; i < Math.min(count, length / 32); i++) {
            String p = patterns.get(i);
            text.replace(i * 32, i * 32 + p.length(), p);
        }
        return new Dataset(patterns, text.toString());
    }

    private static String randomString(Random random, int length) {
        StringBuilder value = new StringBuilder(length);
        for (int i = 0; i < length; i++) value.append((char) ('a' + random.nextInt(4)));
        return value.toString();
    }
    private static final class KmpMatcher {
        private final List<String> patterns;
        private final int[][] prefix;

        public KmpMatcher(List<String> patterns) {
            this.patterns = List.copyOf(patterns);
            prefix = new int[this.patterns.size()][];
            for (int id = 0; id < this.patterns.size(); id++) {
                String p = this.patterns.get(id);
                if (p.isEmpty()) throw new IllegalArgumentException("Empty patterns are not supported");
                int[] pi = new int[p.length()];
                for (int i = 1, j = 0; i < p.length(); i++) {
                    while (j > 0 && p.charAt(i) != p.charAt(j)) j = pi[j - 1];
                    if (p.charAt(i) == p.charAt(j)) j++;
                    pi[i] = j;
                }
                prefix[id] = pi;
            }
        }

        public List<AhoCorasick.Match> search(String text) {
            Objects.requireNonNull(text, "text");
            List<AhoCorasick.Match> matches = new ArrayList<>();
            for (int id = 0; id < patterns.size(); id++) {
                String p = patterns.get(id);
                int[] pi = prefix[id];
                for (int i = 0, j = 0; i < text.length(); i++) {
                    while (j > 0 && text.charAt(i) != p.charAt(j)) j = pi[j - 1];
                    if (text.charAt(i) == p.charAt(j)) j++;
                    if (j == p.length()) {
                        matches.add(new AhoCorasick.Match(id, i - p.length() + 1));
                        j = pi[j - 1];
                    }
                }
            }
            return matches;
        }
    }

    private static final class AlgorithmTests {
        private static int cases;

        static void run() {
            cases = 0;
            check(List.of("he", "she", "his", "hers"), "ushers",
                    List.of(new AhoCorasick.Match(0, 2), new AhoCorasick.Match(1, 1), new AhoCorasick.Match(3, 2)));
            check(List.of("a", "aa", "aaa"), "aaaa",
                    List.of(new AhoCorasick.Match(0, 0), new AhoCorasick.Match(0, 1), new AhoCorasick.Match(0, 2), new AhoCorasick.Match(0, 3),
                            new AhoCorasick.Match(1, 0), new AhoCorasick.Match(1, 1), new AhoCorasick.Match(1, 2),
                            new AhoCorasick.Match(2, 0), new AhoCorasick.Match(2, 1)));
            check(List.of("ab", "ab", "b"), "ab",
                    List.of(new AhoCorasick.Match(0, 0), new AhoCorasick.Match(1, 0), new AhoCorasick.Match(2, 1)));
            check(List.of("abc"), "ab#abc", List.of(new AhoCorasick.Match(0, 3)));
            check(List.of("longpattern"), "short", List.of());
            check(List.of("x"), "", List.of());
            check(List.of(), "anything", List.of());
            check(List.of("\u0430", "\uD83D\uDE00"), "\uD83D\uDE00\u0430",
                    List.of(new AhoCorasick.Match(0, 2), new AhoCorasick.Match(1, 0)));
            Random random = new Random(42);
            for (int trial = 0; trial < 500; trial++) {
                List<String> patterns = new ArrayList<>();
                for (int i = 0, n = random.nextInt(20); i < n; i++) {
                    patterns.add(randomString(random, 1 + random.nextInt(8)));
                }
                String text = randomString(random, random.nextInt(100));
                check(patterns, text, naive(patterns, text));
            }
            rejects(IllegalArgumentException.class, () -> new AhoCorasick(List.of("")));
            rejects(IllegalArgumentException.class, () -> new KmpMatcher(List.of("")));
            rejects(NullPointerException.class, () -> new AhoCorasick(null));
            rejects(NullPointerException.class, () -> new KmpMatcher(null));
            rejects(NullPointerException.class, () -> new AhoCorasick(Arrays.asList("a", null)));
            rejects(NullPointerException.class, () -> new KmpMatcher(Arrays.asList("a", null)));
            rejects(NullPointerException.class, () -> new AhoCorasick(List.of("a")).search(null));
            rejects(NullPointerException.class, () -> new KmpMatcher(List.of("a")).search(null));
            System.out.println("PASS: " + cases + " cases (both algorithms, including 500 randomized cases).");
        }

        private static void check(List<String> patterns, String text, List<AhoCorasick.Match> expected) {
            AhoCorasick ac = new AhoCorasick(patterns);
            KmpMatcher kmp = new KmpMatcher(patterns);
            equal(expected, ac.search(text));
            equal(expected, kmp.search(text));
            equal(expected, ac.search(text));
            cases++;
        }

        static List<AhoCorasick.Match> naive(List<String> patterns, String text) {
            List<AhoCorasick.Match> matches = new ArrayList<>();
            for (int p = 0; p < patterns.size(); p++) {
                for (int i = 0; i + patterns.get(p).length() <= text.length(); i++) {
                    if (text.startsWith(patterns.get(p), i)) matches.add(new AhoCorasick.Match(p, i));
                }
            }
            return matches;
        }

        static void equal(List<AhoCorasick.Match> expected, List<AhoCorasick.Match> actual) {
            Comparator<AhoCorasick.Match> order = Comparator.comparingInt(AhoCorasick.Match::patternId).thenComparingInt(AhoCorasick.Match::start);
            List<AhoCorasick.Match> a = new ArrayList<>(expected), b = new ArrayList<>(actual);
            a.sort(order);
            b.sort(order);
            if (!a.equals(b)) throw new AssertionError("Expected " + a + " but got " + b);
        }

        private static String randomString(Random r, int length) {
            StringBuilder s = new StringBuilder(length);
            for (int i = 0; i < length; i++) s.append((char) ('a' + r.nextInt(3)));
            return s.toString();
        }

        private static void rejects(Class<? extends Throwable> type, Runnable action) {
            try { action.run(); }
            catch (Throwable e) {
                if (!type.isInstance(e)) throw new AssertionError("Unexpected exception", e);
                cases++;
                return;
            }
            throw new AssertionError("Expected " + type.getSimpleName());
        }
    }
}
