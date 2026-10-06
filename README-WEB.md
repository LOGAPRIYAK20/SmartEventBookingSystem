# Running the web version

The console app still works exactly as before (`Main`). This adds a
second entry point, `WebMain`, that serves a browser frontend backed
by the *same* services, repositories and `data/*.txt` files — nothing
in `src/model`, `src/service`, `src/repository` was changed.

New files:
- `src/web/WebServer.java` — a small REST API (`/api/...`) built on
  the JDK's built-in `com.sun.net.httpserver.HttpServer`, so there's
  nothing extra to install.
- `src/web/Json.java` — a tiny dependency-free JSON reader/writer.
- `src/WebMain.java` — entry point for the web server.
- `frontend/` — the browser UI (plain HTML/CSS/JS, no build step).

## Run it

From the project root (the folder containing `src/` and `frontend/`):

```
mkdir out
javac -d out $(find src -name '*.java')
java -cp out WebMain
```

Then open **http://localhost:8080**. Pass a different port as an
argument, e.g. `java -cp out WebMain 9090`.

Admin login: `admin@events.com` / `admin123` (same as the console app;
created automatically on first run, same as before).

If `data/` is empty, a handful of sample events are seeded on first
launch so the site isn't empty — delete them from the admin page
whenever you like.

## Notes

- The console (`Main`) and web (`WebMain`) versions share the same
  `data/*.txt` files but each keeps its own in-memory copy while
  running — don't run both against the same `data/` folder at the
  same time, or whichever saves last will overwrite the other's
  changes.
- Login sessions are simple in-memory tokens; they reset when the
  server restarts (everyone is logged out).
- The server only listens on `localhost`, since passwords travel as
  plain HTTP with no TLS — don't expose this port to the internet
  as-is.
