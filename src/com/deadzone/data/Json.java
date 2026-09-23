package com.deadzone.data;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal, dependency-free JSON parser (objects -> LinkedHashMap, arrays -> ArrayList,
 *  strings -> String, numbers -> Double, true/false -> Boolean, null -> null). */
public final class Json {
    private final char[] s;
    private int i;

    private Json(Reader r) {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        try {
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
        } catch (IOException e) {
            throw new RuntimeException("json read failed", e);
        }
        s = sb.toString().toCharArray();
        i = 0;
    }

    public static Object parse(Reader r) {
        Json j = new Json(r);
        j.ws();
        Object v = j.value();
        return v;
    }

    private Object value() {
        if (i >= s.length) return null;
        char c = s[i];
        if (c == '{') return obj();
        if (c == '[') return arr();
        if (c == '"') return str();
        if (c == 't' || c == 'f') return bool();
        if (c == 'n') { skip(4); return null; }
        return num();
    }

    private void ws() { while (i < s.length && Character.isWhitespace(s[i])) i++; }

    private Map<String, Object> obj() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        i++; // {
        ws();
        if (i < s.length && s[i] == '}') { i++; return m; }
        while (true) {
            ws();
            String k = str();
            ws();
            if (s[i] != ':') throw new RuntimeException("json expected : at " + i);
            i++;
            ws();
            m.put(k, value());
            ws();
            if (i >= s.length) throw new RuntimeException("json eof in object");
            if (s[i] == ',') { i++; continue; }
            if (s[i] == '}') { i++; break; }
            throw new RuntimeException("json expected , or } at " + i);
        }
        return m;
    }

    private List<Object> arr() {
        List<Object> l = new ArrayList<Object>();
        i++; // [
        ws();
        if (i < s.length && s[i] == ']') { i++; return l; }
        while (true) {
            ws();
            l.add(value());
            ws();
            if (i >= s.length) throw new RuntimeException("json eof in array");
            if (s[i] == ',') { i++; continue; }
            if (s[i] == ']') { i++; break; }
            throw new RuntimeException("json expected , or ] at " + i);
        }
        return l;
    }

    private String str() {
        if (s[i] != '"') throw new RuntimeException("json expected string at " + i);
        i++;
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (i >= s.length) throw new RuntimeException("json eof in string");
            char c = s[i++];
            if (c == '"') break;
            if (c == '\\') {
                char e = s[i++];
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        sb.append((char) Integer.parseInt(new String(s, i, 4), 16));
                        i += 4;
                        break;
                    default: throw new RuntimeException("bad escape");
                }
            } else sb.append(c);
        }
        return sb.toString();
    }

    private Boolean bool() {
        if (s[i] == 't') { skip(4); return Boolean.TRUE; }
        skip(5);
        return Boolean.FALSE;
    }

    private void skip(int n) { i += n; }

    private Double num() {
        int start = i;
        while (i < s.length && (Character.isDigit(s[i]) || s[i] == '-' || s[i] == '+' || s[i] == '.' || s[i] == 'e' || s[i] == 'E')) i++;
        return Double.parseDouble(new String(s, start, i - start));
    }

    // ---- helpers ----
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObj(Object o) { return (Map<String, Object>) o; }

    @SuppressWarnings("unchecked")
    public static List<Object> asArr(Object o) { return (List<Object>) o; }

    public static Object get(Map<String, Object> m, String k) { return m.get(k); }

    public static double num(Map<String, Object> m, String k, double def) {
        Object o = m.get(k);
        if (o instanceof Number) return ((Number) o).doubleValue();
        return def;
    }

    public static int inum(Map<String, Object> m, String k, int def) {
        return (int) num(m, k, def);
    }

    public static String str(Map<String, Object> m, String k, String def) {
        Object o = m.get(k);
        return o == null ? def : o.toString();
    }

    public static boolean bool(Map<String, Object> m, String k, boolean def) {
        Object o = m.get(k);
        return o instanceof Boolean ? (Boolean) o : def;
    }

    // ---- writer (save games) ----

    public static String write(Object o) {
        StringBuilder sb = new StringBuilder();
        write(o, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(Object o, StringBuilder sb) {
        if (o == null) sb.append("null");
        else if (o instanceof String) {
            sb.append('"');
            for (int i = 0; i < ((String) o).length(); i++) {
                char c = ((String) o).charAt(i);
                switch (c) {
                    case '"': sb.append("\\\""); break;
                    case '\\': sb.append("\\\\"); break;
                    case '\n': sb.append("\\n"); break;
                    case '\t': sb.append("\\t"); break;
                    case '\r': sb.append("\\r"); break;
                    default:
                        if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                        else sb.append(c);
                }
            }
            sb.append('"');
        } else if (o instanceof Boolean) sb.append(o.toString());
        else if (o instanceof Double) {
            double d = (Double) o;
            if (d == Math.floor(d) && Math.abs(d) < 1e15) sb.append((long) d);
            else sb.append(d);
        } else if (o instanceof Number) sb.append(o.toString());
        else if (o instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> en : ((Map<String, Object>) o).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                write(en.getKey(), sb);
                sb.append(':');
                write(en.getValue(), sb);
            }
            sb.append('}');
        } else if (o instanceof List) {
            sb.append('[');
            List<Object> l = (List<Object>) o;
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(',');
                write(l.get(i), sb);
            }
            sb.append(']');
        } else sb.append("null");
    }
}
