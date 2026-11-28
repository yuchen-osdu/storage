package org.opengroup.osdu.storage.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecordQueryResult {

    @Schema(description = "Cursor for fetching the next page of results. Null when there are no more results.", nullable = true)
    private String cursor;

    private List<RecordMetadata> results;
}
