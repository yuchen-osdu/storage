// Copyright 2017-2026, Schlumberger
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.opengroup.osdu.storage.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.github.fge.jsonpatch.JsonPatch;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;

/**
 * Binds the json-patch library's {@link JsonPatch} with Jackson 3. The library is built on Jackson 2 (its operation
 * values are Jackson 2 {@code JsonNode}s), so the JSON is handed over to a Jackson 2 mapper configured like the
 * Jackson 2 HTTP converter it replaces: decimals are kept exactly as sent.
 */
public final class JsonPatchJacksonBridge {

    static final ObjectMapper JSON_PATCH_MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .configure(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false);

    private JsonPatchJacksonBridge() {
    }

    public static class Deserializer extends ValueDeserializer<JsonPatch> {

        @Override
        public JsonPatch deserialize(JsonParser parser, DeserializationContext context) {
            JsonNode tree = context.readTree(parser);
            try {
                return JSON_PATCH_MAPPER.readValue(tree.toString(), JsonPatch.class);
            } catch (JsonProcessingException e) {
                return context.reportInputMismatch(JsonPatch.class, "Invalid JSON Patch: %s", e.getOriginalMessage());
            }
        }
    }

    public static class Serializer extends ValueSerializer<JsonPatch> {

        @Override
        public void serialize(JsonPatch value, JsonGenerator generator, SerializationContext context) {
            try {
                generator.writeRawValue(JSON_PATCH_MAPPER.writeValueAsString(value));
            } catch (JsonProcessingException e) {
                context.reportMappingProblem(e, "Failed to write JSON Patch");
            }
        }
    }
}
