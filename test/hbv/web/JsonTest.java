package hbv.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void escapesSpecialCharacters() {
        String json = Json.stringify(Json.obj("t", "Er sagte \"Hallo\"\n\\ <script>"));
        assertEquals("{\"t\":\"Er sagte \\\"Hallo\\\"\\n\\\\ \\u003cscript>\"}", json);
    }

    @Test
    void roundTripKeepsTrickyStrings() {
        // Genau diese Zeichen haben den alten JsonHelper kaputtgemacht
        String title = "Arrays [] und {Objekte}, \"Zitate\" & Backslash \\ – Umlaute äöü";
        String json = Json.stringify(Json.obj("items", List.of(Json.obj("title", title, "n", 42L))));

        Map<String, Object> parsed = Json.parseObject(json);
        List<Map<String, Object>> items = Json.getObjectList(parsed, "items");

        assertEquals(1, items.size());
        assertEquals(title, Json.getString(items.get(0), "title"));
        assertEquals(42L, Json.getLong(items.get(0), "n"));
    }

    @Test
    void parsesAllValueTypes() {
        Map<String, Object> obj = Json.parseObject(
            " { \"a\" : true , \"b\": false, \"c\": null, \"d\": -12, \"e\": 1.5, \"f\": [1, \"x\"], \"g\": \"\\u00e4\" } ");
        assertTrue(Json.getBoolean(obj, "a"));
        assertEquals(Boolean.FALSE, obj.get("b"));
        assertEquals(null, obj.get("c"));
        assertEquals(-12L, obj.get("d"));
        assertEquals(1.5, obj.get("e"));
        assertEquals(List.of(1L, "x"), obj.get("f"));
        assertEquals("ä", obj.get("g"));
    }

    @Test
    void rejectsInvalidJson() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":1} extra"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("\"offen"));
        assertThrows(IllegalArgumentException.class, () -> Json.parseObject("[1,2]"));
    }
}
