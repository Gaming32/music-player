package io.github.gaming32.musicplayer;

import com.destroystokyo.paper.brigadier.BukkitBrigadierCommandSource;
import com.destroystokyo.paper.event.brigadier.CommandRegisteredEvent;
import com.google.common.base.Splitter;
import com.google.common.net.UrlEscapers;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("UnstableApiUsage")
public class MusicPlayer extends JavaPlugin implements Listener {
    private static final Map<String, CompletableFuture<PlayerPackInfo>> PACK_INFO_CACHE = new ConcurrentHashMap<>();

    private Path musicDir;

    private HttpServer server;

    private String baseUri;
    private String fallbackUriHost;

    private Command musicPlayerCommand;
    private Command playMusicCommand;

    private String ffmpegVersion;

    private final Set<String> songsAutocomplete = new CopyOnWriteArraySet<>();
    private WatchService watchService;
    private Thread watchThread;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        musicDir = getDataFolder().toPath().resolve(getConfig().getString("music-dir"));
        try {
            Files.createDirectories(musicDir);
        } catch (IOException e) {
            getSLF4JLogger().error("Failed to create music-dir", e);
            setEnabled(false);
            return;
        }

        final InetSocketAddress address = new InetSocketAddress(
            getConfig().getString("http.host"),
            getConfig().getInt("http.port")
        );
        getSLF4JLogger().info("Starting HTTP server on {}", address);
        try {
            server = HttpServer.create(address, 0);
            server.createContext("/", this::httpHandler);
            server.start();
        } catch (IOException e) {
            server = null;
            getSLF4JLogger().error("Failed to start HTTP server", e);
            setEnabled(false);
            return;
        }
        getSLF4JLogger().info("Started HTTP server");

        baseUri = getConfig().getString("http.external-uri");
        fallbackUriHost = getConfig().getString("http.fallback-uri-host");

        musicPlayerCommand = getCommand("musicplayer");
        playMusicCommand = getCommand("playmusic");
        Bukkit.getPluginManager().registerEvents(this, this);

        songsAutocomplete.clear();
        try (Stream<Path> stream = Files.find(musicDir, Integer.MAX_VALUE, (p, a) -> a.isRegularFile())) {
            stream.map(path -> musicDir.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/"))
                .forEach(songsAutocomplete::add);
        } catch (IOException e) {
            getSLF4JLogger().warn("Failed to populate songs list. Autocomplete may be limited.");
        }

        checkForFfmpeg();

        try {
            watchService = musicDir.getFileSystem().newWatchService();
            musicDir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);

            watchThread = new Thread(this::watchThread);
            watchThread.setDaemon(true);
            watchThread.start();
        } catch (IOException e) {
            getSLF4JLogger().warn("Failed to start watchService. Autocomplete may be limited.", e);
        }
    }

    @Override
    public void onDisable() {
        if (server != null) {
            getSLF4JLogger().info("Stopping HTTP server");
            server.stop(1);
            server = null;
            getSLF4JLogger().info("HTTP server stopped");
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                getSLF4JLogger().error("Failed to close watchService", e);
            }
        }
        watchThread = null;
    }

    @EventHandler
    public void onCommandRegistered(CommandRegisteredEvent<BukkitBrigadierCommandSource> event) {
        if (event.getCommand() == musicPlayerCommand) {
            event.setLiteral(literal("musicplayer")
                .then(literal("play")
                    .requires(s -> s.getBukkitSender().hasPermission("musicplayer.play"))
                    .then(argument("path", StringArgumentType.greedyString())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(songsAutocomplete.stream(), builder))
                        .executes(this::playMusic)
                    )
                )
                .then(literal("ffmpeg")
                    .requires(s -> s.getBukkitSender().hasPermission("musicplayer.ffmpeg"))
                    .then(literal("version")
                        .executes(ctx -> {
                            final CommandSender sender = ctx.getSource().getBukkitSender();
                            if (ffmpegVersion == null) {
                                sender.sendMessage(Component.text("ffmpeg not currently found.", NamedTextColor.RED));
                            } else {
                                sender.sendMessage(Component.text(ffmpegVersion, NamedTextColor.GREEN));
                            }
                            sender.sendMessage(Component.text(
                                "If you believe this information to be out of date, run /musicplayer ffmpeg check.",
                                NamedTextColor.YELLOW
                            ));
                            return ffmpegVersion != null ? 1 : 0;
                        })
                    )
                    .then(literal("check")
                        .executes(ctx -> {
                            final CommandSender sender = ctx.getSource().getBukkitSender();
                            sender.sendMessage(Component.text("Rechecking for ffmpeg installation..."));
                            checkForFfmpeg();
                            if (ffmpegVersion == null) {
                                sender.sendMessage(Component.text("ffmpeg was not found. Check logs for details.", NamedTextColor.RED));
                            } else {
                                sender.sendMessage(Component.text("Found " + ffmpegVersion, NamedTextColor.GREEN));
                            }
                            return ffmpegVersion != null ? 1 : 0;
                        })
                    )
                )
                .build()
            );
        } else if (event.getCommand() == playMusicCommand) {
            event.setLiteral(literal("playmusic")
                .redirect(event.getRoot().getChild("musicplayer").getChild("play"))
                .build()
            );
        }
    }

    private int playMusic(CommandContext<BukkitBrigadierCommandSource> context) {
        final String path = StringArgumentType.getString(context, "path");
        final CommandSender sender = context.getSource().getBukkitSender();
        return playSong(sender.getServer().getOnlinePlayers(), path, sender) ? 1 : 0;
    }

    private void httpHandler(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        final String fullPath = exchange.getRequestURI().getPath();
        if (!fullPath.endsWith(".zip")) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        final CompletableFuture<PlayerPackInfo> future = PACK_INFO_CACHE.get(fullPath.substring(1, fullPath.length() - 4));
        if (future == null) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        final PlayerPackInfo packInfo;
        try {
            packInfo = future.get(1, TimeUnit.SECONDS);
        } catch (InterruptedException | TimeoutException e) {
            // I wish there was a better status code for this
            exchange.sendResponseHeaders(404, -1);
            return;
        } catch (ExecutionException e) {
            getSLF4JLogger().error("Failed to convert PlayerPackInfo", e);
            exchange.sendResponseHeaders(500, -1);
            return;
        }
        exchange.getResponseHeaders().add("Content-Type", "application/zip");
        exchange.sendResponseHeaders(200, packInfo.data().length);
        exchange.getResponseBody().write(packInfo.data());
    }

    private void checkForFfmpeg() {
        getSLF4JLogger().info("Checking for ffmpeg installation");
        try {
            final Process process = new ProcessBuilder("ffmpeg", "-version")
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
            if (process.waitFor() != 0) {
                getSLF4JLogger().warn("ffmpeg terminated with non-zero exit code {}", process.exitValue());
            }
            try (BufferedReader reader = process.inputReader()) {
                ffmpegVersion = reader.readLine();
            }
            getSLF4JLogger().info("Found {}", ffmpegVersion);
        } catch (IOException e) {
            getSLF4JLogger().info("ffmpeg not found. Only .ogg will be supported.", e);
            ffmpegVersion = null;
        } catch (Exception e) {
            getSLF4JLogger().error("Error checking for ffmpeg", e);
            ffmpegVersion = null;
        }
    }

    private void watchThread() {
        try {
            while (true) {
                final WatchKey key = watchService.take();
                final Set<String> toAdd = new HashSet<>();
                final Set<String> toRemove = new HashSet<>();
                for (final WatchEvent<?> event : key.pollEvents()) {
                    if (!(event.context() instanceof Path path)) continue;
                    final String pathString = path.toString().replace(path.getFileSystem().getSeparator(), "/");
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        toAdd.add(pathString);
                        toRemove.remove(pathString);
                    } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                        toAdd.remove(pathString);
                        toRemove.add(pathString);
                    }
                }
                if (!toRemove.isEmpty()) {
                    songsAutocomplete.removeAll(toRemove);
                }
                if (!toAdd.isEmpty()) {
                    songsAutocomplete.addAll(toAdd);
                }
            }
        } catch (ClosedWatchServiceException ignored) {
        } catch (InterruptedException e) {
            getSLF4JLogger().error("Unexpected interrupt of watchThread", e);
            try {
                watchService.close();
            } catch (IOException e1) {
                getSLF4JLogger().error("Failed to close watchService", e1);
            }
        }
    }

    public boolean playSong(Iterable<? extends Player> players, String path, @Nullable CommandSender sender) {
        final Path resolved = musicDir.resolve(path);
        if (!resolved.startsWith(musicDir) || !Files.isRegularFile(resolved)) {
            if (sender != null) {
                sender.sendMessage(Component.text("Could not find song " + path, NamedTextColor.RED));
            }
            return false;
        }
        if (ffmpegVersion == null && !path.endsWith(".ogg")) {
            if (sender != null) {
                sender.sendMessage(Component.text("This server only supports .ogg files.", NamedTextColor.RED));
            }
            return false;
        }
        if (sender != null) {
            sender.sendMessage(Component.text("Preparing to play " + path));
        }
        createPackInfo(path).handle((result, error) -> {
            if (error != null) {
                getSLF4JLogger().error("Failed to convert music", error);
                PACK_INFO_CACHE.remove(path);
                if (sender != null) {
                    runOnMainThread(() -> sender.sendMessage(Component.text("Failed to play song. ", NamedTextColor.RED)));
                }
                return null;
            }
            runOnMainThread(() -> {
                if (sender != null) {
                    sender.sendMessage(Component.text("Now playing " + path, NamedTextColor.GREEN));
                }
                playSong(players, result);
            });
            return null;
        });
        return true;
    }

    public CompletableFuture<PlayerPackInfo> createPackInfo(String path) {
        return PACK_INFO_CACHE.computeIfAbsent(path, this::createPackInfo0);
    }

    private CompletableFuture<PlayerPackInfo> createPackInfo0(String path) {
        return CompletableFuture.supplyAsync(() -> {
            final Path sourcePath = musicDir.resolve(path).toAbsolutePath();
            final UUID uuid = UUID.randomUUID();
            if (path.endsWith(".ogg")) {
                // No conversion necessary
                return PlayerPackInfo.create(sourcePath, uuid, path);
            }
            try {
                final Path tempFile = Files.createTempFile("music-player", ".ogg").toAbsolutePath();
                try {
                    tempFile.toFile().deleteOnExit();
                } catch (UnsupportedOperationException ignored) {
                }
                final Process process = new ProcessBuilder(
                    "ffmpeg", "-y", "-i", sourcePath.toString(), "-vn", tempFile.toString()
                ).start();
                if (process.waitFor() != 0) {
                    Files.deleteIfExists(tempFile);
                    throw new IllegalStateException("ffmpeg conversion failed with exit code " + process.exitValue());
                }
                return PlayerPackInfo.create(tempFile, uuid, path);
            } catch (RuntimeException e) {
                throw e;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void playSong(Iterable<? extends Player> players, PlayerPackInfo packInfo) {
        for (final Player player : players) {
            playSong(player, packInfo);
        }
    }

    @SuppressWarnings("PatternValidation")
    public void playSong(Player player, PlayerPackInfo packInfo) {
        try {
            final String escapedPath = Splitter.on('/')
                .splitToStream(packInfo.path())
                .map(UrlEscapers.urlPathSegmentEscaper().asFunction())
                .collect(Collectors.joining("/"));
            player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .prompt(Component.text("This resourcepack is required to listen to " + packInfo.path() + "."))
                .required(true)
                .packs(ResourcePackInfo.resourcePackInfo()
                    .id(packInfo.uuid())
                    .uri(new URI(getBaseUri(player) + escapedPath + ".zip"))
                    .hash(packInfo.hash())
                )
                .callback((uuid, status, audience) -> {
                    if (status.intermediate()) return;
                    switch (status) {
                        case SUCCESSFULLY_LOADED -> audience.playSound(Sound.sound(
                            Key.key("wav-player", "custom_music." + packInfo.uuid()),
                            Sound.Source.RECORD, 1f, 1f
                        ));
                        case DECLINED -> audience.sendMessage(Component.text(
                            "Cannot play " + packInfo.path() + " unless you accept the resourcepack.",
                            NamedTextColor.RED
                        ));
                    }
                })
            );
        } catch (Exception e) {
            getSLF4JLogger().error("Couldn't play song {} for player {}", packInfo.path(), player, e);
        }
    }

    private String getBaseUri(Player player) {
        if (!baseUri.contains("{}")) {
            return baseUri;
        }
        final InetSocketAddress virtualHost = player.getVirtualHost();
        final String hostString = virtualHost != null ? virtualHost.getHostString() : fallbackUriHost;
        return baseUri.replace("{}", hostString);
    }

    private void runOnMainThread(Runnable action) {
        if (Bukkit.isPrimaryThread()) {
            action.run();
        } else {
            Bukkit.getScheduler().runTask(this, action);
        }
    }

    private static <S extends BukkitBrigadierCommandSource> LiteralArgumentBuilder<S> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static <S extends BukkitBrigadierCommandSource, T> RequiredArgumentBuilder<S, T> argument(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }
}
