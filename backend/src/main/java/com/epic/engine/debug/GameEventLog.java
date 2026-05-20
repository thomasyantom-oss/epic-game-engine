package com.epic.engine.debug;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@Component
public class GameEventLog {

    private static final Logger log = LoggerFactory.getLogger("GAME_EVENT");
    private static final int MAX_ENTRIES = 500;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Deque<LogEntry> entries = new ArrayDeque<>();

    public record LogEntry(String timestamp, String playerId, String action, String detail, String result) {}

    public void logEvent(String playerId, String action, String detail, String result) {
        String ts = LocalDateTime.now().format(FMT);
        LogEntry entry = new LogEntry(ts, playerId, action, detail, result);

        synchronized (entries) {
            if (entries.size() >= MAX_ENTRIES) {
                entries.pollFirst();
            }
            entries.addLast(entry);
        }

        log.info("[{}] {} | {} → {} | {}", ts, playerId, action, detail, result);
    }

    public List<LogEntry> getRecentEntries(int count) {
        synchronized (entries) {
            return entries.stream()
                    .skip(Math.max(0, entries.size() - count))
                    .toList();
        }
    }

    public List<LogEntry> getEntriesForPlayer(String playerId, int count) {
        synchronized (entries) {
            return entries.stream()
                    .filter(e -> e.playerId().equals(playerId))
                    .skip(Math.max(0, entries.stream().filter(e -> e.playerId().equals(playerId)).count() - count))
                    .toList();
        }
    }
}
