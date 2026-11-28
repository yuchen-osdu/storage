// Copyright 2017-2019, Schlumberger
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

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.opengroup.osdu.core.test.config.EnvLoader;
import org.opengroup.osdu.core.test.client.model.storage.CopyRecordsRequest;

public class TestUtils {

    public static final String STORAGE_TEST_GROUP_ENT_V_2 = "data.storage-integration-test-acl.ent-v2";
    public static final String STORAGE_TEST_GROUP_ENT_V_2_DESCRIPTION = "EntV2 Storage test group.";

    @Getter
    private static volatile String groupId = EnvLoader.get("ENTITLEMENTS_DOMAIN");
    private static final String tenantId = EnvLoader.get("DATA_PARTITION_ID");

    /**
     * Derives the entitlements domain from a group email returned by the entitlements API.
     * The email format is {@code {name}@{dataPartitionId}.{domain}}, so the domain is
     * extracted by stripping the {@code {dataPartitionId}.} prefix from the part after {@code @}.
     *
     * <p>This is called automatically by {@link org.opengroup.osdu.storage.BaseStorageAcceptanceTest}
     * after creating the first test group, so tests no longer require the {@code ENTITLEMENTS_DOMAIN}
     * environment variable when the entitlements service is reachable.
     *
     * @param groupEmail a group email from the entitlements API, e.g. {@code data.test@opendes.contoso.com}
     * @param dataPartitionId the data partition id, e.g. {@code opendes}
     */
    public static void initGroupIdFromEmail(String groupEmail, String dataPartitionId) {
        if (StringUtils.isNotBlank(groupId)) {
            return; // already set via env var — keep it
        }
        if (StringUtils.isBlank(groupEmail) || !groupEmail.contains("@")) {
            throw new IllegalArgumentException(
                "Cannot derive ENTITLEMENTS_DOMAIN: group email is missing or has no '@': " + groupEmail);
        }
        String suffix = groupEmail.substring(groupEmail.indexOf('@') + 1);
        String prefix = dataPartitionId + ".";
        if (!suffix.startsWith(prefix)) {
            throw new IllegalArgumentException(
                "Cannot derive ENTITLEMENTS_DOMAIN: group email suffix '" + suffix
                    + "' does not start with data partition '" + prefix + "'");
        }
        groupId = suffix.substring(prefix.length());
    }

    public static String getAclSuffix() {
        if (StringUtils.isBlank(groupId)) {
            throw new IllegalStateException(
                "ENTITLEMENTS_DOMAIN is not set and has not been derived from the entitlements API. "
                    + "Either set the ENTITLEMENTS_DOMAIN environment variable or ensure "
                    + "BaseStorageAcceptanceTest.createTestGroupOnce() runs first.");
        }
        return String.format("%s.%s", tenantId, groupId);
    }

    /**
     * @deprecated Use dynamically created groups from test classes instead of hardcoded values.
     * This method returns a hardcoded group email and should not be used in new tests.
     */
    @Deprecated
    public static String getAcl() {
        return String.format("data.test1@%s", getAclSuffix());
    }

    public static String getEntV2OnlyAcl() {
        return String.format(STORAGE_TEST_GROUP_ENT_V_2 + "@%s", getAclSuffix());
    }

    public static CopyRecordsRequest getCopyRecordRequest(String target, String recordId) {
        return CopyRecordsRequest.of(target, recordId);
    }
}
