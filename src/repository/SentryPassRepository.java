package repository;

import model.SentryPass;
import util.FileManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class SentryPassRepository {

    private static final String FILE_NAME = "passes.txt";
    private final List<SentryPass> passes;

    public SentryPassRepository() {
        this.passes = new ArrayList<>();
        loadFromFile();
    }

    private void loadFromFile() {
        List<String> lines = FileManager.readAll(FILE_NAME);
        for (String line : lines) {
            try {
                String[] parts = line.split("\\|");
                String passId = parts[0];
                int bookingId = Integer.parseInt(parts[1]);
                int userId = Integer.parseInt(parts[2]);
                String userName = parts[3];
                int eventId = Integer.parseInt(parts[4]);
                String eventName = parts[5];
                String eventDate = parts[6];
                String eventTime = parts[7];
                String venue = parts[8];
                String seatNumbers = parts[9];
                double amountPaid = Double.parseDouble(parts[10]);
                LocalDateTime issuedAt = LocalDateTime.parse(parts[11]);
                LocalDateTime validUntil = LocalDateTime.parse(parts[12]);
                SentryPass.PassStatus passStatus = SentryPass.PassStatus.valueOf(parts[13]);
                String qrCode = parts[14];
                boolean checkedIn = Boolean.parseBoolean(parts[15]);
                LocalDateTime checkInTime = parts[16].equals("null") ? null : LocalDateTime.parse(parts[16]);

                SentryPass pass = new SentryPass(passId, bookingId, userId, userName, eventId, eventName,
                        eventDate, eventTime, venue, seatNumbers, amountPaid, issuedAt, validUntil,
                        passStatus, qrCode, checkedIn, checkInTime);
                passes.add(pass);
            } catch (Exception e) {
                System.err.println("Error parsing SentryPass from file: " + line);
            }
        }
    }

    public void saveAll() {
        List<String> lines = passes.stream()
                .map(SentryPass::toFileLine)
                .collect(Collectors.toList());
        FileManager.writeAll(FILE_NAME, lines);
    }

    public void addPass(SentryPass pass) {
        passes.add(pass);
        saveAll();
    }

    public Optional<SentryPass> findById(String passId) {
        return passes.stream()
                .filter(p -> p.getPassId().equalsIgnoreCase(passId))
                .findFirst();
    }

    public List<SentryPass> findByUserId(int userId) {
        return passes.stream()
                .filter(p -> p.getUserId() == userId)
                .collect(Collectors.toList());
    }

    public List<SentryPass> findAll() {
        return new ArrayList<>(passes);
    }
}
