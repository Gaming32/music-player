package io.github.gaming32.musicplayer;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import org.apache.commons.io.function.IOConsumer;
import org.intellij.lang.annotations.Language;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public record PlayerPackInfo(String path, UUID uuid, byte[] data, String hash) {
    @Language("JSON")
    private static final String PACK_MCMETA = """
        {
            "pack": {
                "pack_format": 22,
                "supported_formats": {
                    "min_inclusive": 22
                },
                "description": "Resource pack for playing %s"
            }
        }
        """;
    @Language("JSON")
    private static final String SOUNDS_JSON = """
        {
            "custom_music.%1$s": {
                "sounds": [
                    "music-player:%1$s"
                ]
            }
        }
        """;

    public static PlayerPackInfo create(InputStream input, String path) {
        return create(path, out -> {
            try (final ZipOutputStream zos = new ZipOutputStream(out)) {
                zos.putNextEntry(entry("pack.mcmeta"));
                zos.write(PACK_MCMETA.formatted(path).getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                directory(zos, "assets/");
                directory(zos, "assets/music-player/");

                zos.putNextEntry(entry("assets/music-player/sounds.json"));
                zos.write(SOUNDS_JSON.formatted(path.length()).getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                directory(zos, "assets/music-player/sounds/");

                zos.putNextEntry(entry("assets/music-player/sounds/" + path.length() + ".ogg"));
                input.transferTo(zos);
                zos.closeEntry();
            }
        });
    }

    private static ZipEntry entry(String name) {
        final ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L); // Deterministic zip
        return entry;
    }

    private static void directory(ZipOutputStream zos, String name) throws IOException {
        if (!name.endsWith("/")) {
            throw new IllegalArgumentException("Name must end with / to be a directory");
        }
        final ZipEntry entry = entry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(0L);
        entry.setCrc(0L);
        zos.putNextEntry(entry);
        zos.closeEntry();
    }

    @SuppressWarnings({"UnstableApiUsage", "deprecation"})
    public static PlayerPackInfo create(String path, IOConsumer<OutputStream> contents) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final HashingOutputStream hos = new HashingOutputStream(Hashing.sha1(), baos);
        try {
            contents.accept(hos);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        final HashCode hashCode = hos.hash();
        return new PlayerPackInfo(path, UuidUtil.uuidFromHashCode(hashCode), baos.toByteArray(), hashCode.toString());
    }
}
