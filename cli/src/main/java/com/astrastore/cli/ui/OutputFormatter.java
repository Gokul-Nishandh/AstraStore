/**
 * Global output formatting for table / json / quiet modes.
 * Centralizes the --format and --quiet flag handling so every command
 * can produce machine-readable output without per-command boilerplate.
 */
package com.astrastore.cli.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

public final class OutputFormatter {

    public static final String FORMAT_TABLE = "table";
    public static final String FORMAT_JSON = "json";
    public static final String FORMAT_TSV = "tsv";
    public static final String FORMAT_YAML = "yaml";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OutputFormatter() {
    }

    public static boolean isJson(String format) {
        return FORMAT_JSON.equalsIgnoreCase(format);
    }

    public static boolean isTsv(String format) {
        return FORMAT_TSV.equalsIgnoreCase(format);
    }

    public static boolean isYAML(String format) {
        return FORMAT_YAML.equalsIgnoreCase(format);
    }

    public static String formatJson(Object data) {
        try {
            return MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            return String.valueOf(data);
        }
    }

    public static String formatTsv(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        Object[] headers = rows.get(0).keySet().toArray();
        for (Object h : headers) sb.append(h).append('\t');
        sb.append('\n');
        for (Map<String, Object> row : rows) {
            for (Object h : headers) {
                Object v = row.get(h);
                sb.append(v == null ? "" : String.valueOf(v)).append('\t');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    public static <T> T parseJson(String body, TypeReference<T> typeRef) {
        if (body == null || body.isEmpty()) return null;
        try {
            return MAPPER.readValue(body, typeRef);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Render a list of maps either as a table (default), JSON, TSV, or YAML.
     * In --quiet mode, print only the first value of the first row (or "" if none).
     */
    public static void printList(String format, boolean quiet, List<Map<String, Object>> rows) {
        if (quiet) {
            if (rows == null || rows.isEmpty()) {
                System.out.println("");
            } else {
                Map<String, Object> first = rows.get(0);
                if (first.values().iterator().hasNext()) {
                    System.out.println(first.values().iterator().next());
                }
            }
            return;
        }
        if (FORMAT_JSON.equalsIgnoreCase(format)) {
            System.out.println(formatJson(rows));
        } else if (FORMAT_TSV.equalsIgnoreCase(format)) {
            System.out.print(formatTsv(rows));
        } else if (FORMAT_YAML.equalsIgnoreCase(format)) {
            System.out.println(formatJson(rows));
        } else {
            if (rows == null || rows.isEmpty()) {
                System.out.println(ColorSupport.dim("(empty)"));
                return;
            }
            for (Map<String, Object> row : rows) {
                for (Map.Entry<String, Object> e : row.entrySet()) {
                    String key = ColorSupport.cyan(e.getKey() + ":");
                    String val = String.valueOf(e.getValue());
                    System.out.println("  " + key + " " + val);
                }
                System.out.println();
            }
        }
    }

    /**
     * Render a single value (used for quiet mode printing of IDs).
     */
    public static void printValue(String format, boolean quiet, String value) {
        if (quiet || FORMAT_JSON.equalsIgnoreCase(format) || FORMAT_TSV.equalsIgnoreCase(format)) {
            System.out.println(value);
        } else {
            System.out.println(value);
        }
    }
}
