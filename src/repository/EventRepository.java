package repository;

import model.Event;
import util.FileManager;

import java.time.LocalDate;
import java.util.*;

public class EventRepository {

    private static final String FILE_NAME = "events.txt";
    private final Map<Integer, Event> eventsById = new HashMap<>();

    public EventRepository() {
        load();
    }

    public Event save(Event event) {
        eventsById.put(event.getEventId(), event);
        persist();
        return event;
    }

    public Optional<Event> findById(int id) {
        return Optional.ofNullable(eventsById.get(id));
    }

    public boolean delete(int id) {
        boolean removed = eventsById.remove(id) != null;
        if (removed) persist();
        return removed;
    }

    public List<Event> findAll() {
        return new ArrayList<>(eventsById.values());
    }

    /** Simple linear search across name/location/category/date - demonstrates a searching algorithm. */
    public List<Event> search(String keyword) {
        List<Event> results = new ArrayList<>();
        String k = keyword.toLowerCase();
        for (Event e : eventsById.values()) {
            if (e.getName().toLowerCase().contains(k)
                    || e.getLocation().toLowerCase().contains(k)
                    || e.getCategory().toLowerCase().contains(k)
                    || e.getDate().toString().contains(k)) {
                results.add(e);
            }
        }
        return results;
    }

    public void persist() {
        List<String> lines = new ArrayList<>();
        for (Event e : eventsById.values()) lines.add(e.toFileLine());
        FileManager.writeAll(FILE_NAME, lines);
    }

    private void load() {
        for (String line : FileManager.readAll(FILE_NAME)) {
            String[] p = line.split("\\|", -1);
            if (p.length < 8) continue;
            int id = Integer.parseInt(p[0]);
            String name = p[1], category = p[2], location = p[3];
            LocalDate date = LocalDate.parse(p[4]);
            double basePrice = Double.parseDouble(p[5]);
            int total = Integer.parseInt(p[6]);
            int available = Integer.parseInt(p[7]);
            eventsById.put(id, new Event(id, name, category, location, date, basePrice, total, available));
        }
    }
}
