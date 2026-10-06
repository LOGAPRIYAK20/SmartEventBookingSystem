package repository;

import model.Admin;
import model.User;
import util.FileManager;

import java.util.*;

/**
 * In-memory store of Users backed by data/users.txt.
 * Demonstrates use of HashMap for O(1) lookups by ID/email.
 */
public class UserRepository {

    private static final String FILE_NAME = "users.txt";
    private final Map<Integer, User> usersById = new HashMap<>();
    private final Map<String, Integer> idByEmail = new HashMap<>();

    public UserRepository() {
        load();
    }

    public User save(User user) {
        usersById.put(user.getUserId(), user);
        idByEmail.put(user.getEmail().toLowerCase(), user.getUserId());
        persist();
        return user;
    }

    public Optional<User> findById(int id) {
        return Optional.ofNullable(usersById.get(id));
    }

    public Optional<User> findByEmail(String email) {
        Integer id = idByEmail.get(email.toLowerCase());
        return id == null ? Optional.empty() : Optional.ofNullable(usersById.get(id));
    }

    public boolean emailExists(String email) {
        return idByEmail.containsKey(email.toLowerCase());
    }

    public List<User> findAll() {
        return new ArrayList<>(usersById.values());
    }

    public void persist() {
        List<String> lines = new ArrayList<>();
        for (User u : usersById.values()) lines.add(u.toFileLine());
        FileManager.writeAll(FILE_NAME, lines);
    }

    private void load() {
        for (String line : FileManager.readAll(FILE_NAME)) {
            String[] p = line.split("\\|", -1);
            if (p.length < 8) continue;
            int id = Integer.parseInt(p[0]);
            String name = p[1], email = p[2], hashedPw = p[3], city = p[4], preferred = p[5];
            int points = Integer.parseInt(p[6]);
            String role = p[7];
            User user = role.equals("ADMIN")
                    ? new Admin(id, name, email, hashedPw, city, preferred, points)
                    : new User(id, name, email, hashedPw, city, preferred, points);
            usersById.put(id, user);
            idByEmail.put(email.toLowerCase(), id);
        }
    }
}
