package com.project.drawguess.websocket;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Binary codec for canvas WebSocket messages.
 *
 * All client → server messages begin with a msg_type byte:
 *   MSG_STROKE        (0x01) - draw stroke
 *   MSG_CLEAR         (0x02) - clear canvas
 *   MSG_REQUEST_STATE (0x03) - request full canvas state
 *
 * All server → client messages begin with a msg_type byte:
 *   MSG_STROKE (0x01) - draw stroke broadcast
 *   MSG_CLEAR  (0x02) - canvas cleared broadcast
 *   MSG_STATE  (0x03) - full canvas state response
 *
 * Client → server STROKE layout (after the 0x01 type byte):
 *   [1] tool (0=pen, 1=eraser)
 *   [1] R, [1] G, [1] B
 *   [1] lineWidth
 *   [2] pointCount (uint16 BE)
 *   [4 per point] x uint16 BE, y uint16 BE
 *
 * Server → client STROKE layout:
 *   [1] 0x01 | [1] tool | [3] RGB | [1] lineWidth
 *   [2] pointCount | [4*N] x,y pairs
 *   [1] usernameLen | [N] username UTF-8
 *
 * Server → client CLEAR:   [1] 0x02
 * Server → client STATE:   [1] 0x03 | [2] strokeCount | per-stroke data
 */
public class BinaryCanvasCodec {

    public static final byte MSG_STROKE        = 0x01;
    public static final byte MSG_CLEAR         = 0x02;
    public static final byte MSG_REQUEST_STATE = 0x03; // client → server
    public static final byte MSG_STATE         = 0x03; // server → client

    /**
     * Decode a stroke sent by the client.
     * data[0] must be MSG_STROKE (0x01); the rest is the stroke payload.
     */
    public static Map<String, Object> decodeClientStroke(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.get(); // skip MSG_STROKE type byte

        int toolByte = buf.get() & 0xFF;
        String tool = toolByte == 0x01 ? "eraser" : "pen";

        int r = buf.get() & 0xFF;
        int g = buf.get() & 0xFF;
        int b = buf.get() & 0xFF;
        String color = String.format("#%02X%02X%02X", r, g, b);

        int lineWidth = buf.get() & 0xFF;
        int pointCount = buf.getShort() & 0xFFFF;

        List<Map<String, Object>> points = new ArrayList<>(pointCount);
        for (int i = 0; i < pointCount; i++) {
            int x = buf.getShort() & 0xFFFF;
            int y = buf.getShort() & 0xFFFF;
            Map<String, Object> pt = new HashMap<>();
            pt.put("x", x);
            pt.put("y", y);
            points.add(pt);
        }

        Map<String, Object> stroke = new HashMap<>();
        stroke.put("type", "STROKE");
        stroke.put("tool", tool);
        stroke.put("color", color);
        stroke.put("lineWidth", lineWidth);
        stroke.put("points", points);
        return stroke;
    }

    /**
     * Encode a STROKE message for broadcast (includes msg_type prefix and sender username).
     */
    public static byte[] encodeStroke(Map<String, Object> stroke, String senderUsername) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> points = (List<Map<String, Object>>) stroke.get("points");
        int pointCount = points == null ? 0 : points.size();

        int[] rgb = hexToRgb((String) stroke.get("color"));
        String tool = (String) stroke.get("tool");
        int lineWidth = ((Number) stroke.get("lineWidth")).intValue();
        byte[] usernameBytes = senderUsername != null
                ? senderUsername.getBytes(StandardCharsets.UTF_8)
                : new byte[0];

        // 1(type) + 1(tool) + 3(rgb) + 1(lineWidth) + 2(pointCount) + 4*N + 1(usernameLen) + usernameBytes
        ByteBuffer buf = ByteBuffer.allocate(1 + 1 + 3 + 1 + 2 + 4 * pointCount + 1 + usernameBytes.length);

        buf.put(MSG_STROKE);
        buf.put((byte) ("eraser".equals(tool) ? 0x01 : 0x00));
        buf.put((byte) rgb[0]);
        buf.put((byte) rgb[1]);
        buf.put((byte) rgb[2]);
        buf.put((byte) lineWidth);
        buf.putShort((short) pointCount);
        if (points != null) {
            for (Map<String, Object> pt : points) {
                buf.putShort((short) ((Number) pt.get("x")).intValue());
                buf.putShort((short) ((Number) pt.get("y")).intValue());
            }
        }
        buf.put((byte) usernameBytes.length);
        buf.put(usernameBytes);

        return buf.array();
    }

    /**
     * Encode a CANVAS_CLEAR broadcast message.
     */
    public static byte[] encodeClear() {
        return new byte[]{MSG_CLEAR};
    }

    /**
     * Encode a full CANVAS_STATE message to send to a specific user.
     */
    public static byte[] encodeCanvasState(List<Map<String, Object>> strokes) {
        int totalSize = 1 + 2; // MSG_STATE + strokeCount
        for (Map<String, Object> stroke : strokes) {
            @SuppressWarnings("unchecked")
            List<?> pts = (List<?>) stroke.get("points");
            int pc = pts == null ? 0 : pts.size();
            totalSize += 1 + 3 + 1 + 2 + 4 * pc; // tool + rgb + lineWidth + pointCount + points
        }

        ByteBuffer buf = ByteBuffer.allocate(totalSize);
        buf.put(MSG_STATE);
        buf.putShort((short) strokes.size());

        for (Map<String, Object> stroke : strokes) {
            String tool = (String) stroke.get("tool");
            int[] rgb = hexToRgb((String) stroke.get("color"));
            int lineWidth = ((Number) stroke.get("lineWidth")).intValue();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> pts = (List<Map<String, Object>>) stroke.get("points");
            int pointCount = pts == null ? 0 : pts.size();

            buf.put((byte) ("eraser".equals(tool) ? 0x01 : 0x00));
            buf.put((byte) rgb[0]);
            buf.put((byte) rgb[1]);
            buf.put((byte) rgb[2]);
            buf.put((byte) lineWidth);
            buf.putShort((short) pointCount);
            if (pts != null) {
                for (Map<String, Object> pt : pts) {
                    buf.putShort((short) ((Number) pt.get("x")).intValue());
                    buf.putShort((short) ((Number) pt.get("y")).intValue());
                }
            }
        }

        return buf.array();
    }

    private static int[] hexToRgb(String hex) {
        if (hex == null || hex.length() < 7) return new int[]{0, 0, 0};
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        return new int[]{
            Integer.parseInt(h.substring(0, 2), 16),
            Integer.parseInt(h.substring(2, 4), 16),
            Integer.parseInt(h.substring(4, 6), 16)
        };
    }
}
