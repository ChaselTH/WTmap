package com.codex.thorwarthunder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class MapObject {
    String type;
    String icon;
    String color;
    int blink;
    int[] colorArray;

    boolean hasPoint;
    float x;
    float y;
    float dx;
    float dy;

    boolean hasLine;
    float sx;
    float sy;
    float ex;
    float ey;

    boolean isGroundUnit() {
        return type.contains("ground")
                || icon.contains("Tank")
                || "SPAA".equals(icon)
                || "SAM".equals(icon)
                || "Artillery".equals(icon)
                || "Truck".equals(icon);
    }

    static List<MapObject> fromArray(JSONArray array) {
        ArrayList<MapObject> objects = new ArrayList<>();
        if (array == null) {
            return objects;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            if (json == null) {
                continue;
            }
            MapObject object = new MapObject();
            object.type = json.optString("type", "");
            object.icon = json.optString("icon", "");
            object.color = json.optString("color", "#ffffff");
            object.blink = json.optInt("blink", 0);
            object.colorArray = readColorArray(json.optJSONArray("color[]"));

            object.hasPoint = json.has("x") && json.has("y");
            object.x = (float) json.optDouble("x", 0.0);
            object.y = (float) json.optDouble("y", 0.0);
            object.dx = (float) json.optDouble("dx", 0.0);
            object.dy = (float) json.optDouble("dy", -1.0);

            object.hasLine = json.has("sx") && json.has("sy") && json.has("ex") && json.has("ey");
            object.sx = (float) json.optDouble("sx", 0.0);
            object.sy = (float) json.optDouble("sy", 0.0);
            object.ex = (float) json.optDouble("ex", 0.0);
            object.ey = (float) json.optDouble("ey", 0.0);
            objects.add(object);
        }
        return objects;
    }

    private static int[] readColorArray(JSONArray array) {
        if (array == null || array.length() < 3) {
            return null;
        }
        return new int[]{
                clamp(array.optInt(0, 255)),
                clamp(array.optInt(1, 255)),
                clamp(array.optInt(2, 255))
        };
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
