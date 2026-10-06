package util;

/**
 * Generates unique QR ticket codes and formatted hashes for bookings and Sentry passes.
 */
public class QRGenerator {

    public static String generateCode(int bookingId, int eventId, int userId) {
        String raw = bookingId + "-" + eventId + "-" + userId + "-" + System.nanoTime();
        int hash = Math.abs(raw.hashCode());
        return "TKT-" + bookingId + "-" + Integer.toHexString(hash).toUpperCase();
    }

    public static String generateQR(String data) {
        int hash = Math.abs(data.hashCode());
        return "QR-" + Integer.toHexString(hash).toUpperCase();
    }

    public static String renderAscii(String code) {
        StringBuilder sb = new StringBuilder();
        int seed = Math.abs(code.hashCode());
        sb.append("+----------------+\n");
        for (int row = 0; row < 6; row++) {
            sb.append("|");
            for (int col = 0; col < 16; col++) {
                int bit = (seed >> ((row * 16 + col) % 30)) & 1;
                sb.append(bit == 1 ? "##" : "  ");
            }
            sb.append("|\n");
        }
        sb.append("+----------------+\n");
        sb.append("Code: ").append(code);
        return sb.toString();
    }
}
