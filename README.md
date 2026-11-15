# Aho-Corasick Algorithm

## Run

```text
javac -d build src/*.java
java -cp build Main
```

The project has three source files: `AhoCorasick.java` for the algorithm,
`AhoCorasickBenchmark.java` for tests and AC/KMP measurements, and `Main.java`
for starting the program. Running `Main` performs the tests and all experiments.

## How it works

The patterns are stored in a trie, so patterns with the same prefix share nodes. Failure links are built using BFS. Each link points to the longest proper suffix that is also a prefix in the trie. This lets the search continue after a mismatch without starting over in the text.

Missing transitions are filled using failure links. Output links connect terminal suffix states, so all matching patterns are reported. For example, matching `she` also reports `he`. The text is scanned once.

## Tests and results

There are 516 correctness tests, including overlaps, duplicate patterns, empty text, Unicode and 500 random cases compared with a simple brute-force search.

The experiments use 10, 100 and 1,000 patterns with texts of 1,000, 10,000 and 100,000 characters. Patterns have lengths from 5 to 12 and use the alphabet `a`-`d`. Text is randomly generated with some patterns inserted to ensure matches. Both algorithms use the same data.

Each case has 10 warmup runs, followed by **5 measured runs using System.nanoTime()**. The table shows the average **preprocessing + search** time in milliseconds. Input generation and result checking are outside the measurements.

| Patterns | Text length | Aho-Corasick (ms) | KMP (ms) |
|---:|---:|---:|---:|
| 10 | 1000 | 0.0743 | 0.2505 |
| 10 | 10000 | 0.6391 | 0.8405 |
| 10 | 100000 | 0.9791 | 6.0962 |
| 100 | 1000 | 0.2344 | 0.5769 |
| 100 | 10000 | 0.2775 | 5.4520 |
| 100 | 100000 | 1.0066 | 56.4701 |
| 1000 | 1000 | 0.5310 | 5.5899 |
| 1000 | 10000 | 0.6375 | 49.6729 |
| 1000 | 100000 | 2.3321 | 548.6947 |

## Complexity

Let `M` be the total pattern length, `N` the text length, `k` the number of patterns and `Z` the number of matches.

For a fixed alphabet, Aho-Corasick takes O(M) to build and O(N + Z) to search, with O(M) automaton space. This implementation stores a transition array at each state. More precisely, build time and space are O(M + S * sigma), where `S <= M + 1` is the number of states and `sigma` is the number of distinct pattern characters. Time bounds assume constant-time HashMap lookups on average.

Running KMP separately for every pattern takes O(M) preprocessing and O(k * N + Z) searching. The stored prefix tables use O(M) space. Both implementations also use O(Z) space to store matches.

## Comparison

Aho-Corasick was faster in all measured cases in this run. Its advantage was largest with 1,000 patterns and 100,000 characters: about 2.33 ms compared with 548.69 ms for KMP. KMP had cheaper preprocessing, but its separate text scans took longer.

Aho-Corasick is useful for many patterns because they share one text scan. Its disadvantages are more complex code and larger transition tables, especially with a large alphabet. KMP is simpler and works well for one or a few patterns, but repeating it scans the same text many times.

These results are from one run of the experiment. JVM warmup, garbage collection and background processes can affect timings, especially for small inputs.
