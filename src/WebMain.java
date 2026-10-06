import web.WebServer;

/**
 * Entry point for the WEB version of the Smart Event Booking System.
 * Run from the project root so the ./frontend and ./data folders are found:
 *
 *     java -cp out WebMain          (default port 8080)
 *     java -cp out WebMain 9090     (custom port)
 *
 * The console version (Main) still works and uses the same data files.
 * Don't run both at the same time - each keeps its own in-memory copy.
 */
public class WebMain {
    public static void main(String[] args) throws Exception {
        int port = 8080;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Port must be a number, e.g. 9090");
                return;
            }
        }
        new WebServer(port).start();
    }
}
