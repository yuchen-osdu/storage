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

package org.opengroup.osdu.storage.provider.gcp.messaging.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.legal.jobs.LegalTagChanged;
import org.opengroup.osdu.core.common.model.legal.jobs.LegalTagChangedCollection;
import org.opengroup.osdu.core.common.model.storage.PubSubInfo;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class LegalComplianceChangeServiceGcpImplTest {

    private static final String TAG = "opendes-public-usa-dataset";
    private static final DpsHeaders HEADERS = new DpsHeaders();

    @Mock
    private IRecordsMetadataRepository recordsRepo;
    @Mock
    private IMessageBus messageBus;
    @Mock
    private StorageAuditLogger auditLogger;
    @Mock
    private JaxRsDpsLog logger;
    @Mock
    private ICache<String, String> legalTagCache;

    @InjectMocks
    private LegalComplianceChangeServiceGcpImpl sut;

    @Test
    void should_suspendOnlyActiveRecords_when_tagBecomesIncompliant() throws Exception {
        RecordMetadata active = record("active", RecordState.active, LegalCompliance.compliant);
        RecordMetadata userDeleted = record("user-deleted", RecordState.deleted, LegalCompliance.compliant);
        stubQuery(LegalCompliance.compliant, List.of(active, userDeleted));

        Map<String, LegalCompliance> output = this.sut.updateComplianceOnRecords(changed("incompliant"), HEADERS);

        assertEquals(RecordState.suspended, active.getStatus());
        assertEquals(RecordState.deleted, userDeleted.getStatus());
        assertEquals(LegalCompliance.incompliant, active.getLegal().getStatus());
        assertEquals(LegalCompliance.incompliant, userDeleted.getLegal().getStatus());
        assertEquals(Map.of("active", LegalCompliance.incompliant, "user-deleted", LegalCompliance.incompliant), output);
        assertPublished(List.of("active"), OperationType.delete);
        verify(this.legalTagCache).delete(TAG);
    }

    @Test
    void should_reviveOnlySuspendedRecords_when_tagBecomesCompliant() throws Exception {
        RecordMetadata suspended = record("suspended", RecordState.suspended, LegalCompliance.incompliant);
        RecordMetadata userDeleted = record("user-deleted", RecordState.deleted, LegalCompliance.incompliant);
        stubQuery(LegalCompliance.incompliant, List.of(suspended, userDeleted));

        this.sut.updateComplianceOnRecords(changed("compliant"), HEADERS);

        assertEquals(RecordState.active, suspended.getStatus());
        assertEquals(RecordState.deleted, userDeleted.getStatus());
        assertEquals(LegalCompliance.compliant, suspended.getLegal().getStatus());
        assertEquals(LegalCompliance.compliant, userDeleted.getLegal().getStatus());
        assertPublished(List.of("suspended"), OperationType.update);
    }

    @Test
    void should_persistWholePageButNotPublish_when_noRecordChangesStatus() throws Exception {
        RecordMetadata userDeleted = record("user-deleted", RecordState.deleted, LegalCompliance.incompliant);
        stubQuery(LegalCompliance.incompliant, List.of(userDeleted));

        this.sut.updateComplianceOnRecords(changed("compliant"), HEADERS);

        verify(this.recordsRepo).createOrUpdate(List.of(userDeleted), Optional.empty());
        verify(this.messageBus, never()).publishMessage(any(DpsHeaders.class), any(PubSubInfo[].class));
        assertEquals(LegalCompliance.compliant, userDeleted.getLegal().getStatus());
    }

    @Test
    void should_notReviveLegacyDeletedRecord_when_tagBecomesCompliant() throws Exception {
        // records left deleted + incompliant by the previous handler are indistinguishable from
        // user-deleted ones, so they only get a truthful legal status
        RecordMetadata legacy = record("legacy", RecordState.deleted, LegalCompliance.incompliant);
        stubQuery(LegalCompliance.incompliant, List.of(legacy));

        this.sut.updateComplianceOnRecords(changed("compliant"), HEADERS);

        assertEquals(RecordState.deleted, legacy.getStatus());
        assertEquals(LegalCompliance.compliant, legacy.getLegal().getStatus());
    }

    @Test
    void should_keepUserDeletion_when_suspendedRecordIsDeletedBeforeTagBecomesCompliant() throws Exception {
        RecordMetadata rec = record("lifecycle", RecordState.active, LegalCompliance.compliant);
        List<RecordMetadata> store = List.of(rec);
        when(this.recordsRepo.queryByLegal(eq(TAG), any(LegalCompliance.class), anyInt()))
            .thenAnswer(invocation -> new AbstractMap.SimpleEntry<>(null, store.stream()
                .filter(r -> r.getLegal().getStatus() == invocation.getArgument(1))
                .toList()));

        this.sut.updateComplianceOnRecords(changed("incompliant"), HEADERS);
        assertEquals(RecordState.suspended, rec.getStatus());

        // what RecordServiceImpl.deleteRecord does to a suspended record
        rec.setStatus(RecordState.deleted);

        this.sut.updateComplianceOnRecords(changed("compliant"), HEADERS);
        assertEquals(RecordState.deleted, rec.getStatus());
        assertEquals(LegalCompliance.compliant, rec.getLegal().getStatus());
    }

    @Test
    void should_ignoreTag_when_statusIsUnknown() throws Exception {
        Map<String, LegalCompliance> output = this.sut.updateComplianceOnRecords(changed("unknown"), HEADERS);

        assertEquals(Map.of(), output);
        verify(this.recordsRepo, never()).queryByLegal(any(), any(), anyInt());
    }

    private void stubQuery(LegalCompliance current, List<RecordMetadata> page) {
        when(this.recordsRepo.queryByLegal(eq(TAG), eq(current), anyInt()))
            .thenReturn(new AbstractMap.SimpleEntry<>(null, page))
            .thenReturn(new AbstractMap.SimpleEntry<>(null, new ArrayList<RecordMetadata>()));
    }

    private void assertPublished(List<String> expectedIds, OperationType expectedOp) {
        ArgumentCaptor<PubSubInfo> captor = ArgumentCaptor.forClass(PubSubInfo.class);
        verify(this.messageBus).publishMessage(eq(HEADERS), captor.capture());
        assertEquals(expectedIds, captor.getAllValues().stream().map(PubSubInfo::getId).toList());
        captor.getAllValues().forEach(info -> assertEquals(expectedOp, info.getOp()));
    }

    private static LegalTagChangedCollection changed(String status) {
        LegalTagChanged legalTagChanged = new LegalTagChanged();
        legalTagChanged.setChangedTagName(TAG);
        legalTagChanged.setChangedTagStatus(status);
        LegalTagChangedCollection collection = new LegalTagChangedCollection();
        collection.setStatusChangedTags(List.of(legalTagChanged));
        return collection;
    }

    private static RecordMetadata record(String id, RecordState status, LegalCompliance legalStatus) {
        Legal legal = new Legal();
        legal.setLegaltags(Set.of(TAG));
        legal.setStatus(legalStatus);
        RecordMetadata recordMetadata = new RecordMetadata();
        recordMetadata.setId(id);
        recordMetadata.setKind("opendes:wks:well:1.0.0");
        recordMetadata.setStatus(status);
        recordMetadata.setLegal(legal);
        return recordMetadata;
    }
}
