// Copyright 2017-2023, Schlumberger
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

package org.opengroup.osdu.storage.api;

import tools.jackson.databind.ObjectMapper;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsFactory;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsService;
import org.opengroup.osdu.core.common.http.CollaborationContextFactory;
import org.opengroup.osdu.core.common.model.entitlements.EntitlementsException;
import org.opengroup.osdu.core.common.model.entitlements.GroupInfo;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.multitenancy.ITenantInfoService;
import org.opengroup.osdu.storage.conversion.CrsConversionService;
import org.opengroup.osdu.storage.logging.ReadAuditLogsConsumer;
import org.opengroup.osdu.storage.policy.service.PartitionPolicyStatusService;
import org.opengroup.osdu.storage.service.*;
import org.opengroup.osdu.storage.util.RecordBlocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.mockito.Mockito.lenient;

public abstract class ApiTest<T> {
    @MockitoBean
    protected ICache cache;

    @MockitoBean
    protected IEntitlementsFactory iEntitlementsFactory;

    @MockitoBean
    protected CrsConversionService crsConversionService;

    @MockitoBean
    protected BatchService batchService;

    @MockitoBean
    protected ReadAuditLogsConsumer readAuditLogsConsumer;

    @MockitoBean
    protected PartitionPolicyStatusService partitionPolicyStatusService;

    @MockitoBean
    protected BulkUpdateRecordServiceImpl bulkUpdateRecordService;

    @MockitoBean
    protected DataAuthorizationService dataAuthorizationService;

    @MockitoBean
    protected IngestionServiceImpl ingestionService;

    @MockitoBean
    protected LegalServiceImpl legalService;

    @MockitoBean
    protected PartitionServiceImpl partitionService;

    @MockitoBean
    protected PatchRecordsServiceImpl patchRecordsService;

    @MockitoBean
    protected PersistenceServiceImpl persistenceService;

    @MockitoBean
    protected QueryServiceImpl queryService;

    @MockitoBean
    protected RecordServiceImpl recordService;

    @MockitoBean
    protected SchemaServiceImpl schemaService;

    @MockitoBean
    protected RecordBlocks recordBlocks;

    @MockitoBean
    protected ITenantInfoService tenantInfoService;

    @MockitoBean
    protected DpsHeaders dpsHeaders;

    @MockitoBean
    private CollaborationContextFactory collaborationContextFactory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected abstract HttpMethod getHttpMethod();

    protected abstract String getUriTemplate();

    protected void setupAuthorization(String role) throws EntitlementsException {
        IEntitlementsService iEntitlementsService = Mockito.mock(IEntitlementsService.class);
        GroupInfo groupInfo = new GroupInfo();
        groupInfo.setName(role);
        Groups groups = new Groups();
        groups.setGroups(Collections.singletonList(groupInfo));
        groups.setMemberEmail("a@b");
        lenient().when(iEntitlementsService.getGroups()).thenReturn(groups);
        lenient().when(iEntitlementsFactory.create(ArgumentMatchers.any())).thenReturn(iEntitlementsService);
    }

    protected ResultActions sendRequest(T dto, Object... uriVars) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.request(getHttpMethod(), getUriTemplate(), uriVars)
                .contentType("application/json-patch+json")
                .characterEncoding(StandardCharsets.UTF_8)
                .header(DpsHeaders.AUTHORIZATION, "Bearer token")
                .header(DpsHeaders.DATA_PARTITION_ID, "opendes")
                .header(DpsHeaders.USER_EMAIL, "a@b.com")
                .content(objectMapper.writeValueAsString(dto)));
    }

}
