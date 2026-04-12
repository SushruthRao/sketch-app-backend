package com.project.drawguess.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Round-trip tests for {@link BinaryCanvasCodec}. The binary wire format
 * is a public contract with the frontend canvas client, so these tests
 * catch any accidental layout drift.
 */
class BinaryCanvasCodecTest {

	@Test
	void clientStrokeDecodesToExpectedMap() {
		// Build a stroke in the client -> server layout:
		//   0x01 | tool=pen(0) | RGB=FF0000 | lineWidth=5 | count=2 | (10,20) (30,40)
		ByteBuffer buf = ByteBuffer.allocate(1 + 1 + 3 + 1 + 2 + 4 * 2);
		buf.put(BinaryCanvasCodec.MSG_STROKE);
		buf.put((byte) 0); // pen
		buf.put((byte) 0xFF).put((byte) 0x00).put((byte) 0x00); // red
		buf.put((byte) 5); // lineWidth
		buf.putShort((short) 2); // 2 points
		buf.putShort((short) 10).putShort((short) 20);
		buf.putShort((short) 30).putShort((short) 40);

		Map<String, Object> stroke = BinaryCanvasCodec.decodeClientStroke(buf.array());

		assertEquals("STROKE", stroke.get("type"));
		assertEquals("pen", stroke.get("tool"));
		assertEquals("#FF0000", stroke.get("color"));
		assertEquals(5, stroke.get("lineWidth"));

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> points = (List<Map<String, Object>>) stroke.get("points");
		assertEquals(2, points.size());
		assertEquals(10, points.get(0).get("x"));
		assertEquals(20, points.get(0).get("y"));
		assertEquals(30, points.get(1).get("x"));
		assertEquals(40, points.get(1).get("y"));
	}

	@Test
	void encodeStrokeEmitsCorrectHeaderAndUsernameSuffix() {
		Map<String, Object> stroke = new HashMap<>();
		stroke.put("tool", "eraser");
		stroke.put("color", "#10203F");
		stroke.put("lineWidth", 7);
		stroke.put("points", List.of(Map.of("x", 1, "y", 2)));

		byte[] encoded = BinaryCanvasCodec.encodeStroke(stroke, "alice");

		assertEquals(BinaryCanvasCodec.MSG_STROKE, encoded[0]);
		assertEquals(0x01, encoded[1] & 0xFF);              // eraser
		assertEquals(0x10, encoded[2] & 0xFF);              // R
		assertEquals(0x20, encoded[3] & 0xFF);              // G
		assertEquals(0x3F, encoded[4] & 0xFF);              // B
		assertEquals(7, encoded[5] & 0xFF);                 // lineWidth

		// Username trailer: the last byte before the name is its length.
		byte[] expectedName = "alice".getBytes(StandardCharsets.UTF_8);
		int nameLen = encoded[encoded.length - expectedName.length - 1] & 0xFF;
		assertEquals(expectedName.length, nameLen);
	}

	@Test
	void encodeClearIsSingleByte() {
		byte[] clear = BinaryCanvasCodec.encodeClear();
		assertEquals(1, clear.length);
		assertEquals(BinaryCanvasCodec.MSG_CLEAR, clear[0]);
	}

	@Test
	void encodeCanvasStateHandlesEmptyStrokeList() {
		byte[] state = BinaryCanvasCodec.encodeCanvasState(List.of());
		assertNotNull(state);
		assertEquals(3, state.length); // type + 2-byte count
		assertEquals(BinaryCanvasCodec.MSG_STATE, state[0]);
		assertEquals(0, ByteBuffer.wrap(state, 1, 2).getShort());
	}

	@Test
	void encodeStrokeTolerantOfNullUsername() {
		Map<String, Object> stroke = new HashMap<>();
		stroke.put("tool", "pen");
		stroke.put("color", "#000000");
		stroke.put("lineWidth", 1);
		stroke.put("points", List.of());

		byte[] encoded = BinaryCanvasCodec.encodeStroke(stroke, null);
		assertNotNull(encoded);
		// Trailer is a single 0x00 length byte with no username payload.
		assertEquals(0, encoded[encoded.length - 1] & 0xFF);
	}
}
