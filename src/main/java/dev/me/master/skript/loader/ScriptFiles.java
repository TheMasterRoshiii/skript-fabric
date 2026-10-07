package dev.me.master.skript.loader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ScriptFiles {

    private ScriptFiles() {
    }

    public static List<Path> list(Path directory) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(directory, BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory()) {
            throw new NotDirectoryException(directory.toString());
        }
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                if (!dir.equals(directory) && dir.getFileName().toString().startsWith("-")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                boolean regular = attributes.isRegularFile()
                        || attributes.isSymbolicLink() && !Files.isDirectory(file);
                if (regular && isScriptFile(file)) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        files.sort(Path::compareTo);
        return files;
    }

    public static boolean isScriptFile(Path file) {
        Path filename = file.getFileName();
        if (filename == null) {
            return false;
        }
        String name = filename.toString().toLowerCase(Locale.ROOT);
        return !name.startsWith("-") && (name.endsWith(".sk") || name.endsWith(".sk.txt"));
    }

    public static List<String> read(Path file) throws IOException {
        try (PushbackInputStream input = new PushbackInputStream(Files.newInputStream(file), 3)) {
            byte[] prefix = input.readNBytes(3);
            Charset charset = StandardCharsets.UTF_8;
            int skip = 0;
            if (prefix.length >= 3 && prefix[0] == (byte) 0xef
                    && prefix[1] == (byte) 0xbb && prefix[2] == (byte) 0xbf) {
                skip = 3;
            } else if (prefix.length >= 2 && prefix[0] == (byte) 0xff && prefix[1] == (byte) 0xfe) {
                charset = StandardCharsets.UTF_16LE;
                skip = 2;
            } else if (prefix.length >= 2 && prefix[0] == (byte) 0xfe && prefix[1] == (byte) 0xff) {
                charset = StandardCharsets.UTF_16BE;
                skip = 2;
            }
            input.unread(prefix, skip, prefix.length - skip);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input,
                    charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)))) {
                List<String> lines = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
                return lines;
            }
        }
    }
}
