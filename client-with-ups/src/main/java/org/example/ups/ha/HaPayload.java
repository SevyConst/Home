package org.example.ups.ha;

import kotlinx.serialization.json.Json;
import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JsonElementKt;
import kotlinx.serialization.json.JsonNull;
import kotlinx.serialization.json.JsonObject;
import org.example.ups.nut.UpsSnapshot;

import java.util.*;

public class HaPayload {

    private HaPayload() {
    }

    public static String toJson(UpsSnapshot snapshot) {

        Map<String, JsonElement> fields = new HashMap<>();

        fields.put("status", text(snapshot.getStatus().name()));

        fields.put("input_voltage", number(snapshot.getInputVoltage()));
        fields.put("output_voltage", number(snapshot.getOutputVoltage()));
        fields.put("input_frequency", number(snapshot.getInputFrequency()));
        fields.put("load_percent", number(snapshot.getLoadPercent()));
        fields.put("battery_charge", number(snapshot.getBatteryCharge()));
        fields.put("battery_runtime_min", number(snapshot.getBatteryRuntimeMinutes()));
        fields.put("battery_voltage", number(snapshot.getBatteryVoltage()));

        fields.put("beeper", text(snapshot.getBeeper()));
        fields.put("test_result", text(snapshot.getTestResult()));

        return Json.Default.encodeToString(
                JsonObject.Companion.serializer(),
                new JsonObject(fields)
        );
    }

    private static JsonElement text(Optional<String> value) {
        return text(value.orElse(null));
    }

    private static JsonElement text(String value) {
        return value == null
                ? JsonNull.INSTANCE
                : JsonElementKt.JsonPrimitive(value);
    }

    private static JsonElement number(OptionalDouble value) {
        return value.isPresent()
                ? JsonElementKt.JsonPrimitive(value.getAsDouble())
                : JsonNull.INSTANCE;
    }
}
