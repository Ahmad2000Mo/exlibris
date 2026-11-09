package hbv.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimaler, aber vollstaendiger JSON-Baustein (Serialisieren + Parsen).
 *
 * Unterstuetzte Java-Typen beim Schreiben: Map, List, String, Number, Boolean, null.
 * Beim Parsen entstehen: LinkedHashMap, ArrayList, String, Long/Double, Boolean, null.
 *
 * Bewusst ohne externe Bibliothek, damit das Projekt ohne Build-Tool auskommt.
 */
public final class Json {

    private Json() {}

    // ------------------------------------------------------------------
    // Schreiben
    // ------------------------------------------------------------------

    /** Baut ein JSON-Objekt aus Schluessel/Wert-Paaren: obj("a", 1, "b", "x"). */
    public static Map<String, Object> obj(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("obj() braucht eine gerade Anzahl an Argumenten");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(sb, s);
        } else if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            sb.append(value);
        } else if (value instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("NaN/Infinity ist in JSON nicht erlaubt");
            }
            sb.append(n);
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (value instanceof Iterable<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(',');
                first = false;
                write(sb, item);
            }
            sb.append(']');
        } else {
            writeString(sb, value.toString());
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                // '<' wird escaped, damit JSON gefahrlos in HTML eingebettet werden kann
                case '<' -> sb.append("\\u003c");
                default -> {
                    if (c < 0x20 || c == ' ' || c == ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------
    // Parsen (rekursiver Abstieg)
    // ------------------------------------------------------------------

    public static Object parse(String json) {
        Parser p = new Parser(json);
        p.skipWhitespace();
        Object value = p.readValue();
        p.skipWhitespace();
        if (p.pos != json.length()) {
            throw p.error("Unerwartete Zeichen nach dem JSON-Wert");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object value = parse(json);
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("JSON-Objekt erwartet");
        }
        return (Map<String, Object>) value;
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        IllegalArgumentException error(String msg) {
            return new IllegalArgumentException(msg + " (Position " + pos + ")");
        }

        void skipWhitespace() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        char peek() {
            if (pos >= s.length()) throw error("Unerwartetes Ende");
            return s.charAt(pos);
        }

        void expect(char c) {
            if (peek() != c) throw error("'" + c + "' erwartet");
            pos++;
        }

        Object readValue() {
            char c = peek();
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) yield readNumber();
                    throw error("Unerwartetes Zeichen '" + c + "'");
                }
            };
        }

        Object readLiteral(String word, Object value) {
            if (!s.startsWith(word, pos)) throw error(word + " erwartet");
            pos += word.length();
            return value;
        }

        Map<String, Object> readObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                map.put(key, readValue());
                skipWhitespace();
                if (peek() == ',') {
                    pos++;
                    continue;
                }
                expect('}');
                return map;
            }
        }

        List<Object> readArray() {
            List<Object> list = new ArrayList<>();
            expect('[');
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                skipWhitespace();
                list.add(readValue());
                skipWhitespace();
                if (peek() == ',') {
                    pos++;
                    continue;
                }
                expect(']');
                return list;
            }
        }

        String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = peek();
                pos++;
                if (c == '"') return sb.toString();
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char esc = peek();
                pos++;
                switch (esc) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 > s.length()) throw error("Unvollstaendige \\u-Sequenz");
                        try {
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException e) {
                            throw error("Ungueltige \\u-Sequenz");
                        }
                        pos += 4;
                    }
                    default -> throw error("Ungueltige Escape-Sequenz \\" + esc);
                }
            }
        }

        Number readNumber() {
            int start = pos;
            if (peek() == '-') pos++;
            while (pos < s.length() && "0123456789.eE+-".indexOf(s.charAt(pos)) >= 0) pos++;
            String num = s.substring(start, pos);
            try {
                if (num.contains(".") || num.contains("e") || num.contains("E")) {
                    return Double.parseDouble(num);
                }
                return Long.parseLong(num);
            } catch (NumberFormatException e) {
                throw error("Ungueltige Zahl '" + num + "'");
            }
        }
    }

    // ------------------------------------------------------------------
    // Hilfen zum Auslesen geparster Objekte
    // ------------------------------------------------------------------

    public static String getString(Map<String, Object> obj, String key) {
        Object v = obj.get(key);
        return v == null ? null : v.toString();
    }

    public static long getLong(Map<String, Object> obj, String key) {
        Object v = obj.get(key);
        return v instanceof Number n ? n.longValue() : 0L;
    }

    public static boolean getBoolean(Map<String, Object> obj, String key) {
        return Boolean.TRUE.equals(obj.get(key));
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> getObjectList(Map<String, Object> obj, String key) {
        Object v = obj.get(key);
        List<Map<String, Object>> result = new ArrayList<>();
        if (v instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map) result.add((Map<String, Object>) item);
            }
        }
        return result;
    }
}
