import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Renames the project from M-Ganji to Daraja Agent-X inside a working copy.
 *
 * Run it from the repository root (the folder that holds docker-compose.yml):
 *
 *     java RenameToAgentX.java --dry-run     shows what would change, writes nothing
 *     java RenameToAgentX.java               applies the change
 *     java RenameToAgentX.java --root DIR    works on another folder instead of the current one
 *
 * What it does
 *   1. Rewrites text in every text file (Java, Dart, YAML, XML, JSON, SQL, HTML, Markdown, env files and so on).
 *   2. Moves files to their new names and folders (ganji-api, ganji-app, com/mganji/ganji ...).
 *   3. Lists anything that still carries an old name so that you can look at it.
 *
 * The new names
 *   shown name        Daraja Agent-X
 *   Java packages     com.mganji.ganji -> com.darajaagentx.api, com.mganji.agent -> com.darajaagentx.agent (others: com.darajaagentx.*)
 *   folders           ganji-api -> agentx-api, ganji-app -> agentx-app
 *   Dart package      ganji_app -> agentx_app
 *   environment       GANJI_* -> AGENTX_*, and the yml block ganji: -> agentx:
 *   link scheme       mganji:// -> agentx://
 *   Flutter org       ke.co.ganjiai -> ke.co.darajaagentx
 *   leftovers         mpesaagent -> agentx (the old link scheme in files that never went through the M-Ganji rename)
 *   databases         ganji -> agentx (new database name: create it, or keep the old one in your URL)
 *
 * It is safe to run twice: the second run finds nothing to change. Commit or stash your work first, so that
 * "git diff" shows exactly what the tool did and "git checkout ." can undo it.
 */
public class RenameToAgentX {

    /** Case sensitive, applied in order, to file contents and to relative paths (with / between folders). */
    static final String[][] NAMES = {
            {"ke.co.ganjiai", "ke.co.darajaagentx"},
            {"ke/co/ganjiai", "ke/co/darajaagentx"},
            {"package:ganji_app/", "package:agentx_app/"},
            {"com.mganji.ganji", "com.darajaagentx.api"},
            {"com/mganji/ganji", "com/darajaagentx/api"},
            {"com.mganji", "com.darajaagentx"},
            {"com/mganji", "com/darajaagentx"},
            {"ganji_app/", "agentx-app/"},
            {"M-Ganji", "Daraja Agent-X"},
            {"M-GANJI", "DARAJA AGENT-X"},
            {"m-ganji", "daraja-agent-x"},
            {"MGanji", "AgentX"},
            {"Mganji", "AgentX"},
            {"MGANJI", "AGENTX"},
            {"mganji", "agentx"},
            // Left over from before the M-Ganji rename, in files that never went through it.
            {"com.mpesaagent", "com.darajaagentx"},
            {"com/mpesaagent", "com/darajaagentx"},
            {"mpesa-agent", "daraja-agent-x"},
            {"mpesaagent", "agentx"},
            {"GANJI", "AGENTX"},
            {"Ganji", "AgentX"},
            {"ganji", "agentx"},
    };

    static final Set<String> SKIP_DIRS = Set.of(".git", "build", "target", "node_modules", ".dart_tool", ".idea", ".gradle", ".vscode", ".mvn", "Pods");
    static final long MAX_BYTES = 5L * 1024 * 1024;
    /** This tool and the instructions in APPLY.md list the old names on purpose, and the tool of the previous rename is left alone. */
    static final Set<String> SKIP_FILES = Set.of("RenameToAgentX.java", "RenameToMGanji.java", "APPLY.md");

    static boolean dry;
    static int changedFiles, changedLines, moved;
    static final List<String> report = new ArrayList<>();
    static final List<String> problems = new ArrayList<>();
    static final Set<String> written = new HashSet<>();

    public static void main(String[] args) throws IOException {
        List<String> a = Arrays.asList(args);
        dry = a.contains("--dry-run");
        Path root = a.contains("--root") ? Paths.get(a.get(a.indexOf("--root") + 1)).toAbsolutePath().normalize() : Paths.get("").toAbsolutePath();
        if (!a.contains("--root") && !Files.exists(root.resolve("docker-compose.yml"))) {
            System.err.println("Run this from the repository root, the folder that contains docker-compose.yml (or pass --root DIR).");
            System.exit(2);
        }
        System.out.println((dry ? "DRY RUN in " : "Renaming in ") + root);

        List<Path> files = new ArrayList<>();
        walk(root, root, files);
        Set<Path> oldDirs = new TreeSet<>(Comparator.comparingInt(Path::getNameCount).reversed().thenComparing(Comparator.naturalOrder()));

        for (Path f : files) {
            String rel = root.relativize(f).toString().replace('\\', '/');
            String newRel = rename(rel);
            byte[] bytes = Files.readAllBytes(f);
            String text = decode(bytes);
            String out = text == null ? null : rename(text);
            boolean contentChanged = out != null && !out.equals(text);
            boolean moves = !newRel.equals(rel);
            if (!contentChanged && !moves) {
                continue;
            }
            if (contentChanged) {
                changedFiles++;
                changedLines += countChangedLines(text, out);
            }
            if (moves) {
                moved++;
                report.add("move   " + rel + "  ->  " + newRel);
            } else {
                report.add("edit   " + rel);
            }
            if (dry) {
                continue;
            }
            Path target = root.resolve(newRel);
            if (!written.add(newRel)) {
                System.out.println("note: " + rel + " and an earlier file both become " + newRel + ". The later one is kept.");
            }
            Files.createDirectories(target.getParent());
            Files.write(target, contentChanged ? out.getBytes(StandardCharsets.UTF_8) : bytes);
            if (moves) {
                Files.delete(f);
                for (Path p = f.getParent(); p != null && !p.equals(root); p = p.getParent()) {
                    oldDirs.add(p);
                }
            }
        }
        for (Path d : oldDirs) {   // deepest first
            try (Stream<Path> s = Files.list(d)) {
                if (s.findAny().isEmpty()) {
                    Files.delete(d);
                }
            }
        }

        report.forEach(System.out::println);
        System.out.printf("%n%s %d files rewritten (%d lines), %d files moved.%n", dry ? "Would change" : "Changed", changedFiles, changedLines, moved);

        // Anything left over: look at it by hand.
        List<Path> after = new ArrayList<>();
        if (!dry) {
            walk(root, root, after);
            for (Path f : after) {
                String rel = root.relativize(f).toString().replace('\\', '/');
                String text = decode(Files.readAllBytes(f));
                if (rel.toLowerCase().contains("ganji") || (text != null && text.toLowerCase().contains("ganji"))) {
                    problems.add(rel);
                }
            }
            if (problems.isEmpty()) {
                System.out.println("Nothing is left with the old name.");
            } else {
                System.out.println("\nStill mention the old name, please look:");
                problems.forEach(p -> System.out.println("  " + p));
            }
        }
        System.out.println("""

                Do by hand (the tool cannot):
                  - copy your real .env to the new variable names: every GANJI_ becomes AGENTX_ (AGENTX_VAULT_KEY keeps the same key value)
                  - databases: the default name is now agentx. Create it, or keep the old one in POSTGRES_URL / MYSQL_URL / MONGODB_URI
                  - Android: the application id changes with the Flutter org (ke.co.darajaagentx). Reinstall, and update the Play Console and Google/Firebase
                    registrations that use the old id
                  - app links: the link scheme is now agentx://. Update assetlinks / apple-app-site-association settings (AGENTX_ANDROID_PACKAGE, AGENTX_IOS_APP_ID)
                  - the Microsoft contacts sign-in redirect agentxauth://callback must match the redirect URI in your Azure app registration. Change one of them
                  - rename the repository on GitHub if you want it to match (daraja-agent-x)
                """);
    }

    static String rename(String s) {
        for (String[] n : NAMES) {
            s = s.replace(n[0], n[1]);
        }
        return s;
    }

    static void walk(Path root, Path dir, List<Path> files) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            for (Path p : (Iterable<Path>) s.sorted()::iterator) {
                if (Files.isDirectory(p)) {
                    if (!SKIP_DIRS.contains(p.getFileName().toString())) {
                        walk(root, p, files);
                    }
                } else if (Files.isRegularFile(p) && Files.size(p) <= MAX_BYTES && !SKIP_FILES.contains(p.getFileName().toString())) {
                    files.add(p);
                }
            }
        }
    }

    /** The text of a file, or null when it is binary. */
    static String decode(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0) {
                return null;
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    static int countChangedLines(String before, String after) {
        String[] b = before.split("\n", -1);
        String[] a = after.split("\n", -1);
        int n = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            if (!a[i].equals(b[i])) {
                n++;
            }
        }
        return n;
    }
}
