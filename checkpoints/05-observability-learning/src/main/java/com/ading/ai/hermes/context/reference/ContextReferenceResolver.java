package com.ading.ai.hermes.context.reference;

import com.ading.ai.hermes.workspace.WorkspacePathPolicy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ContextReferenceResolver {

    private static final Pattern REFERENCE_PATTERN = Pattern.compile(
            "(?<![\\w/])@(?:(diff|staged)\\b|(file|folder|git|url):([^\\s]+))"
    );
    private static final Pattern LINE_RANGE = Pattern.compile("^(.*):(\\d+)(?:-(\\d+))?$");

    private final Path workspace;
    private final WorkspacePathPolicy pathPolicy;
    private final int hardCharacterLimit;
    private final UrlContextFetcher urlFetcher;
    private final GitContextReader gitReader;

    public ContextReferenceResolver(
            Path workspace,
            int hardCharacterLimit,
            UrlContextFetcher urlFetcher,
            GitContextReader gitReader
    ) {
        if (hardCharacterLimit < 1) {
            throw new IllegalArgumentException("hardCharacterLimit must be positive");
        }
        this.pathPolicy = new WorkspacePathPolicy(workspace);
        this.workspace = pathPolicy.root();
        this.hardCharacterLimit = hardCharacterLimit;
        this.urlFetcher = urlFetcher;
        this.gitReader = gitReader;
    }

    public ContextReferenceResult resolve(String message) {
        List<ContextReference> references = parse(message);
        List<String> warnings = new ArrayList<>();
        List<String> blocks = new ArrayList<>();

        for (ContextReference reference : references) {
            try {
                String content = expand(reference);
                if (content != null && !content.isBlank()) {
                    blocks.add("### " + reference.raw() + "\n" + content.strip());
                }
            } catch (Exception exception) {
                warnings.add(reference.raw() + ": " + exception.getMessage());
            }
        }

        String attached = String.join("\n\n", blocks);
        if (attached.length() > hardCharacterLimit) {
            warnings.add("context injection refused: content exceeds hard limit " + hardCharacterLimit);
            return new ContextReferenceResult(
                    message, message, "", references, warnings, true
            );
        }

        String resolved = message;
        if (!warnings.isEmpty()) {
            resolved += "\n\n--- Context Warnings ---\n- " + String.join("\n- ", warnings);
        }
        if (!attached.isBlank()) {
            resolved += "\n\n--- Attached Context ---\n\n" + attached;
        }
        return new ContextReferenceResult(
                message, resolved, attached, references, warnings, false
        );
    }

    private List<ContextReference> parse(String message) {
        if (message == null || message.isBlank()) {
            return List.of();
        }
        List<ContextReference> references = new ArrayList<>();
        Matcher matcher = REFERENCE_PATTERN.matcher(message);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                ContextReferenceKind kind = ContextReferenceKind.valueOf(
                        matcher.group(1).toUpperCase(Locale.ROOT)
                );
                references.add(new ContextReference(matcher.group(), kind, "", null, null));
                continue;
            }

            ContextReferenceKind kind = ContextReferenceKind.valueOf(
                    matcher.group(2).toUpperCase(Locale.ROOT)
            );
            String target = trimTrailingPunctuation(matcher.group(3));
            Integer lineStart = null;
            Integer lineEnd = null;
            if (kind == ContextReferenceKind.FILE) {
                Matcher rangeMatcher = LINE_RANGE.matcher(target);
                if (rangeMatcher.matches()) {
                    target = rangeMatcher.group(1);
                    lineStart = Integer.parseInt(rangeMatcher.group(2));
                    lineEnd = rangeMatcher.group(3) == null
                            ? lineStart
                            : Integer.parseInt(rangeMatcher.group(3));
                }
            }
            references.add(new ContextReference(matcher.group(), kind, target, lineStart, lineEnd));
        }
        return List.copyOf(references);
    }

    private String expand(ContextReference reference) throws Exception {
        return switch (reference.kind()) {
            case FILE -> readFile(reference);
            case FOLDER -> readFolder(reference.target());
            case DIFF -> gitReader.read("diff");
            case STAGED -> gitReader.read("staged");
            case GIT -> gitReader.read("git:" + boundedGitCount(reference.target()));
            case URL -> urlFetcher.fetch(reference.target());
        };
    }

    private String readFile(ContextReference reference) throws IOException {
        Path path = resolveWorkspacePath(reference.target());
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("file does not exist");
        }
        List<String> lines = Files.readAllLines(path);
        if (reference.lineStart() == null) {
            return String.join("\n", lines);
        }
        int start = Math.max(1, reference.lineStart());
        int end = Math.min(lines.size(), Math.max(start, reference.lineEnd()));
        if (start > lines.size()) {
            throw new IllegalArgumentException("line range starts after end of file");
        }
        return String.join("\n", lines.subList(start - 1, end));
    }

    private String readFolder(String target) throws IOException {
        Path folder = resolveWorkspacePath(target);
        if (!Files.isDirectory(folder)) {
            throw new IllegalArgumentException("folder does not exist");
        }
        try (Stream<Path> stream = Files.walk(folder)) {
            return stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .limit(200)
                    .map(workspace::relativize)
                    .map(ContextReferenceResolver::portable)
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");
        }
    }

    private Path resolveWorkspacePath(String target) {
        return pathPolicy.resolveExisting(target);
    }

    private static int boundedGitCount(String target) {
        try {
            return Math.max(1, Math.min(Integer.parseInt(target), 10));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("git reference must be a number from 1 to 10");
        }
    }

    private static String trimTrailingPunctuation(String value) {
        int chineseDelimiter = firstIndexOf(value, "，。；！？、）》」】）");
        String bounded = chineseDelimiter < 0 ? value : value.substring(0, chineseDelimiter);
        return bounded.replaceFirst("[,.;!?]+$", "");
    }

    private static int firstIndexOf(String value, String delimiters) {
        for (int index = 0; index < value.length(); index++) {
            if (delimiters.indexOf(value.charAt(index)) >= 0) {
                return index;
            }
        }
        return -1;
    }

    private static String portable(Path path) {
        return path.toString().replace('\\', '/');
    }
}
