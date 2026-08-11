/**
 * Rich single-line progress bar with transfer rate and ETA.
 * Used by upload and download commands to give live feedback.
 * Auto-detects TTY: when stdout is redirected, falls back to a periodic log line.
 */
package com.astrastore.cli.ui;

public final class ProgressRenderer {

    private final long totalBytes;
    private final String filename;
    private final long startTimeNanos;
    private long lastPrintedNanos = 0;
    private long bytesTransferred = 0;
    private final boolean interactive;

    public ProgressRenderer(String filename, long totalBytes) {
        this.filename = filename;
        this.totalBytes = totalBytes;
        this.startTimeNanos = System.nanoTime();
        this.interactive = System.console() != null && ColorSupport.isEnabled();
    }

    public synchronized void update(long bytesSinceLastCall) {
        bytesTransferred += bytesSinceLastCall;
        if (!interactive) return;
        long now = System.nanoTime();
        if (now - lastPrintedNanos < 100_000_000L && bytesTransferred < totalBytes) {
            return;
        }
        lastPrintedNanos = now;
        System.out.print("\r" + renderBar());
        System.out.flush();
        if (bytesTransferred >= totalBytes) {
            System.out.println();
        }
    }

    public synchronized void finish() {
        bytesTransferred = totalBytes;
        if (!interactive) {
            System.out.println(ColorSupport.success("Transfer complete: " + filename + " (" + humanSize(totalBytes) + ")"));
            return;
        }
        System.out.print("\r" + renderBar());
        System.out.println();
    }

    private String renderBar() {
        int width = 30;
        double pct = totalBytes > 0 ? Math.min(1.0, (double) bytesTransferred / totalBytes) : 1.0;
        int filled = (int) (pct * width);
        StringBuilder bar = new StringBuilder();
        bar.append(ColorSupport.cyan(filename)).append(" [");
        bar.append(ColorSupport.GREEN);
        for (int i = 0; i < width; i++) bar.append(i < filled ? "=" : (i == filled ? ">" : " "));
        bar.append(ColorSupport.RESET);
        bar.append("] ");
        bar.append(String.format("%5.1f%%", pct * 100));
        bar.append(" ");
        bar.append(humanSize(bytesTransferred)).append(" / ").append(humanSize(totalBytes));

        long elapsedNanos = System.nanoTime() - startTimeNanos;
        double seconds = elapsedNanos / 1_000_000_000.0;
        if (seconds > 0 && bytesTransferred > 0) {
            double rate = bytesTransferred / seconds;
            bar.append(" (").append(humanSize((long) rate)).append("/s)");
            if (pct < 1.0 && rate > 0) {
                long remaining = (long) ((totalBytes - bytesTransferred) / rate);
                bar.append(" | ETA: ").append(formatEta(remaining));
            }
        }
        return bar.toString();
    }

    private static String formatEta(long seconds) {
        if (seconds < 60) return seconds + "s";
        long m = seconds / 60;
        long s = seconds % 60;
        if (m < 60) return m + "m " + s + "s";
        long h = m / 60;
        m = m % 60;
        return h + "h " + m + "m";
    }

    public static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
