package vn.nirussv.egosystem.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EventKillTracker {

    private final Map<UUID, Integer> eventKills = new HashMap<>();

    public void recordKill(UUID killerUuid) {
        eventKills.merge(killerUuid, 1, Integer::sum);
    }

    public int getKills(UUID uuid) {
        return eventKills.getOrDefault(uuid, 0);
    }

    public void clearKills() {
        eventKills.clear();
    }
}
