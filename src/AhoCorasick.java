import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AhoCorasick {
    public record Match(int patternId, int start) {}

    private static final class Node {
        final int[] next;
        final List<Integer> terminal = new ArrayList<>();
        int failure;
        int output = -1;

        Node(int alphabetSize) {
            next = new int[alphabetSize];
            Arrays.fill(next, -1);
        }
    }

    private final List<String> patterns;
    private final Map<Character, Integer> alphabet = new HashMap<>();
    private final List<Node> nodes = new ArrayList<>();

    public AhoCorasick(List<String> patterns) {
        this.patterns = List.copyOf(patterns);
        for (String pattern : this.patterns) {
            if (pattern.isEmpty()) throw new IllegalArgumentException("Empty patterns are not supported");
            for (int i = 0; i < pattern.length(); i++) {
                char c = pattern.charAt(i);
                if (!alphabet.containsKey(c)) alphabet.put(c, alphabet.size());
            }
        }
        nodes.add(new Node(alphabet.size()));
        for (int id = 0; id < this.patterns.size(); id++) {
            String pattern = this.patterns.get(id);
            int state = 0;
            for (int i = 0; i < pattern.length(); i++) {
                int c = alphabet.get(pattern.charAt(i));
                if (nodes.get(state).next[c] == -1) {
                    nodes.get(state).next[c] = nodes.size();
                    nodes.add(new Node(alphabet.size()));
                }
                state = nodes.get(state).next[c];
            }
            nodes.get(state).terminal.add(id);
        }
        buildFailureLinks();
    }

    private void buildFailureLinks() {
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        Node root = nodes.get(0);
        for (int c = 0; c < alphabet.size(); c++) {
            if (root.next[c] == -1) root.next[c] = 0;
            else queue.add(root.next[c]);
        }
        while (!queue.isEmpty()) {
            Node current = nodes.get(queue.remove());
            Node fallback = nodes.get(current.failure);
            for (int c = 0; c < alphabet.size(); c++) {
                int childId = current.next[c];
                if (childId == -1) {
                    current.next[c] = fallback.next[c];
                } else {
                    Node child = nodes.get(childId);
                    child.failure = fallback.next[c];
                    Node suffix = nodes.get(child.failure);
                    child.output = suffix.terminal.isEmpty() ? suffix.output : child.failure;
                    queue.add(childId);
                }
            }
        }
    }

    public List<Match> search(String text) {
        Objects.requireNonNull(text, "text");
        List<Match> matches = new ArrayList<>();
        int state = 0;
        for (int i = 0; i < text.length(); i++) {
            Integer c = alphabet.get(text.charAt(i));
            state = c == null ? 0 : nodes.get(state).next[c];
            for (int output = state; output != -1; output = nodes.get(output).output) {
                for (int id : nodes.get(output).terminal) {
                    matches.add(new Match(id, i - patterns.get(id).length() + 1));
                }
            }
        }
        return matches;
    }
}
