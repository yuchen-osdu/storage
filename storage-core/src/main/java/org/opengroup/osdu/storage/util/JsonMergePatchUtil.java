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

import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * JSON Merge Patch (RFC 7396) on Jackson 3 trees. Kept apart from {@link JsonPatchUtil}, which works on the
 * Jackson 2 types of the json-patch library.
 */
public final class JsonMergePatchUtil {

    private JsonMergePatchUtil() {
    }

    public static JsonNode applyMergePatch(JsonNode target, JsonNode patch) {
        if (patch == null || !patch.isObject()) {
            return patch == null ? null : patch.deepCopy();
        }

        ObjectNode result = (target != null && target.isObject())
                ? ((ObjectNode) target).deepCopy()
                : JsonNodeFactory.instance.objectNode();

        for (Map.Entry<String, JsonNode> field : patch.properties()) {
            String key = field.getKey();
            JsonNode value = field.getValue();
            if (value.isNull()) {
                result.remove(key);
            } else {
                result.set(key, applyMergePatch(result.get(key), value));
            }
        }

        return result;
    }
}
