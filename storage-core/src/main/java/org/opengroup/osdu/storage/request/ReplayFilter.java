// Copyright © Microsoft Corporation
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

package org.opengroup.osdu.storage.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Filter for restricting replay operations to specific record kinds. "
        + "Required when present: omit the entire filter (or null) for replay-all; "
        + "do not send an empty object (kinds is required).")
public class ReplayFilter {

    @NotNull(message = "filter.kinds is required when filter is provided.")
    @Size(min = 1, max = 1, message = "Currently restricted to a single valid kind.")
    @ArraySchema(minItems = 1, maxItems = 1, arraySchema = @Schema(description = "List of kinds to replay. Currently restricted to a single kind. Required when filter is provided."),
            schema = @Schema(implementation = String.class, minLength = 1))
    private List<String> kinds;
}
