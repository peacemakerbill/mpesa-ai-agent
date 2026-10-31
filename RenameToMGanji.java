import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Renames the project to M-Ganji inside a working copy.
 *
 * Run it from the repository root (the folder that holds docker-compose.yml):
 *
 *     java RenameToMGanji.java --dry-run     shows what would change, writes nothing
 *     java RenameToMGanji.java               applies the change
 *
 * What it does
 *   1. Rewrites text in every text file (Java, Dart, YAML, XML, JSON, SQL, HTML, Markdown, env files and so on).
 *   2. Renames files and folders whose names carry an old name (companion-api, companion-app, com/mpesaagent ...).
 *   3. Lists anything that still carries an old name so that you can look at it.
 *
 * It is safe to run twice: the second run finds nothing to change. Commit or stash your work first, so that
 * "git diff" shows exactly what the tool did and "git checkout ." can undo it.
 */
public class RenameToMGanji {

    /** Whole phrases first, in prose and in the few user-facing strings. Case sensitive, applied in order. */
    static final String[][] PHRASES = {
            {"M-Pesa AI Agent", "M-Ganji"},
            {"M-Pesa Assistant companion app", "M-Ganji app"},
            {"M-Pesa Assistant", "M-Ganji"},
            {"Assistant Console", "M-Ganji Console"},
            {"of the M-Pesa assistant", "of M-Ganji"},
            {"Chat with the M-Pesa assistant", "Chat with M-Ganji"},
            {"COMPANION_APP_NAME=Assistant", "GANJI_APP_NAME=M-Ganji"},
            {"COMPANION_APP_NAME:Assistant}", "GANJI_APP_NAME:M-Ganji}"},
            {"companion.app-name:Assistant}", "ganji.app-name:M-Ganji}"},
            {"const Text('Assistant')", "const Text('M-Ganji')"},
            {"the companion app", "the M-Ganji app"},
            {"The companion app", "The M-Ganji app"},
            {"companion app", "M-Ganji app"},
            {"Companion app", "M-Ganji app"},
            {"the companion API", "the M-Ganji API"},
            {"companion API", "M-Ganji API"},
            {"Companion API", "M-Ganji API"},
    };

    /** Names, used for text and for file and folder names. Case sensitive, applied in order. */
    static final String[][] NAMES = {
            {"mpesa_companion", "ganji_app"},
            {"mpesa-agent", "m-ganji"},
            {"mpesaagent", "mganji"},
            {"COMPANION", "GANJI"},
            {"Companion", "Ganji"},
            {"companion", "ganji"},
    };

    static final Set<String> SKIP_DIRS = Set.of(".git", "build", "target", "node_modules", ".dart_tool", ".idea", ".gradle", ".vscode", ".mvn", "Pods");
    static final long MAX_BYTES = 5L * 1024 * 1024;

    static boolean dry;
    static int changedFiles, changedLines, renamed;
    static final List<String> report = new ArrayList<>();
    static final List<String> problems = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        dry = Arrays.asList(args).contains("--dry-run");
        Path root = Paths.get("").toAbsolutePath();
        if (!Files.exists(root.resolve("docker-compose.yml")) || !(Files.isDirectory(root.resolve("companion-api")) || Files.isDirectory(root.resolve("ganji-api")))) {
            System.err.println("Run this from the repository root, the folder that contains docker-compose.yml and companion-api.");
            System.exit(2);
        }
        System.out.println((dry ? "DRY RUN in " : "Renaming in ") + root);

        List<Path> files = new ArrayList<>();
        List<Path> all = new ArrayList<>();
        walk(root, root, files, all);

        for (Path f : files) rewrite(f);

        // Rename the deepest paths first so that the parents still exist.
        all.sort(Comparator.comparingInt((Path p) -> p.getNameCount()).reversed());
        for (Path p : all) renamePath(p);

        System.out.println();
        System.out.println("Text files changed: " + changedFiles + " (" + changedLines + " lines)");
        System.out.println("Files and folders renamed: " + renamed);
        report.forEach(System.out::println);
        if (!problems.isEmpty()) {
            System.out.println();
            System.out.println("Look at these:");
            problems.forEach(s -> System.out.println("  " + s));
        }
        leftovers(root);
        if (dry) System.out.println("\nNothing was written. Run again without --dry-run to apply.");
    }

    static void walk(Path root, Path dir, List<Path> files, List<Path> all) throws IOException {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                String n = p.getFileName().toString();
                if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                    if (SKIP_DIRS.contains(n)) continue;
                    all.add(p);
                    walk(root, p, files, all);
                } else if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                    if (n.equals("RenameToMGanji.java")) continue;
                    all.add(p);
                    files.add(p);
                }
            }
        }
    }

    /** "companion object" is a Kotlin keyword, so it is protected while the names are replaced. */
    static final String KEEP = "\u0001KOTLIN\u0001";

    static String apply(String s, boolean withPhrases) {
        s = s.replace("companion object", KEEP);
        if (withPhrases) for (String[] r : PHRASES) s = s.replace(r[0], r[1]);
        for (String[] r : NAMES) s = s.replace(r[0], r[1]);
        return s.replace(KEEP, "companion object");
    }

    static void rewrite(Path f) throws IOException {
        if (Files.size(f) > MAX_BYTES) return;
        byte[] raw = Files.readAllBytes(f);
        int probe = Math.min(raw.length, 8192);
        for (int i = 0; i < probe; i++) if (raw[i] == 0) return; // binary
        // ISO-8859-1 maps bytes one to one, so UTF-8 text and line endings come back exactly as they were.
        String before = new String(raw, StandardCharsets.ISO_8859_1);
        String after = apply(before, true);
        if (after.equals(before)) return;
        changedFiles++;
        int n = 0;
        String[] a = before.split("\n", -1), b = after.split("\n", -1);
        for (int i = 0; i < Math.min(a.length, b.length); i++) if (!a[i].equals(b[i])) n++;
        changedLines += n;
        if (!dry) Files.write(f, after.getBytes(StandardCharsets.ISO_8859_1));
    }

    static void renamePath(Path p) throws IOException {
        String name = p.getFileName().toString();
        String now = apply(name, false);
        if (now.equals(name)) return;
        Path to = p.resolveSibling(now);
        if (Files.exists(to)) {
            if (Files.isDirectory(p) && Files.isDirectory(to)) {
                problems.add("Both " + p + " and " + to + " exist. Move the contents by hand.");
            } else {
                problems.add("Cannot rename " + p + ": " + to + " already exists.");
            }
            return;
        }
        renamed++;
        report.add("  rename " + p.getFileName() + "  to  " + now + "   (" + p.getParent().getFileName() + ")");
        if (!dry) Files.move(p, to);
    }

    static void leftovers(Path root) throws IOException {
        if (dry) return;
        List<String> hits = new ArrayList<>();
        List<Path> files = new ArrayList<>(), all = new ArrayList<>();
        walk(root, root, files, all);
        for (Path f : files) {
            if (Files.size(f) > MAX_BYTES) continue;
            byte[] raw = Files.readAllBytes(f);
            int probe = Math.min(raw.length, 8192);
            boolean bin = false;
            for (int i = 0; i < probe; i++) if (raw[i] == 0) { bin = true; break; }
            if (bin) continue;
            String[] lines = new String(raw, StandardCharsets.ISO_8859_1).split("\n");
            for (int i = 0; i < lines.length; i++) {
                String l = lines[i].toLowerCase(Locale.ROOT);
                if ((l.contains("companion") && !l.replace("companion object", "").isEmpty() && l.replace("companion object", "").contains("companion")) || l.contains("mpesaagent") || l.contains("mpesa_companion"))
                    hits.add(root.relativize(f) + ":" + (i + 1) + "  " + lines[i].trim());
            }
        }
        System.out.println();
        if (hits.isEmpty()) System.out.println("No old names are left.");
        else {
            System.out.println("Old names still present on " + hits.size() + " lines (first 25):");
            hits.stream().limit(25).forEach(h -> System.out.println("  " + h));
        }
    }
}
