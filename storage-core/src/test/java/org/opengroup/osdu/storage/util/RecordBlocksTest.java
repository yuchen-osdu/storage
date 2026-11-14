package org.opengroup.osdu.storage.util;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import tools.jackson.databind.ObjectMapper;
import org.opengroup.osdu.storage.di.TestJsonMappers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.entitlements.GroupInfo;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.*;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
public class RecordBlocksTest {

    @Mock
    private ICloudStorage cloudStorage;

    @Spy
    CrcHashGenerator crcHashGenerator;

    @Spy
    ObjectMapper objectMapper = TestJsonMappers.objectMapper();

    @InjectMocks
    private RecordBlocks recordBlocks;
    private static final String RECORD_ID1 = "tenant1:kind:record1";
    private static final String RECORD_ID2 = "tenant1:crazy:record2";
    private static final String KIND_1 = "tenant1:test:kind:1.0.0";
    private static final String KIND_2 = "tenant1:test:crazy:2.0.2";
    private static final String USER = "testuser@gmail.com";
    private static final String NEW_USER = "newuser@gmail.com";
    private static final String[] VALID_ACL = new String[]{"data.email1@tenant1.gmail.com", "data.test@tenant1.gmail.com"};

    private Record record1;
    private Record record2;

    private List<Record> records;
    private Acl acl = new Acl();

    @BeforeEach
    public void setup() {
        List<String> userHeaders = new ArrayList<>();
        userHeaders.add(USER);

        Legal legal = new Legal();
        legal.setOtherRelevantDataCountries(Sets.newHashSet("FRA"));

        Groups groups = new Groups();
        List<GroupInfo> groupsInfo = new ArrayList<>();
        GroupInfo groupInfo = new GroupInfo();
        groupInfo.setEmail("test.group@mydomain.com");
        groupsInfo.add(groupInfo);
        groups.setGroups(groupsInfo);

        this.record1 = new Record();
        this.record1.setKind(KIND_1);
        this.record1.setId(RECORD_ID1);
        this.record1.setLegal(legal);
        // set up empty ancestry for record1
        RecordAncestry ancestry = new RecordAncestry();
        this.record1.setAncestry(ancestry);

        this.record2 = new Record();
        this.record2.setKind(KIND_2);
        this.record2.setId(RECORD_ID2);
        this.record2.setLegal(legal);

        this.records = new ArrayList<>();
        this.records.add(this.record1);
        this.records.add(this.record2);

        this.record1.setAcl(this.acl);
        this.record2.setAcl(this.acl);

    }


    @Test
    public void populateRecordBlocksMetadataEmptyWhenNoChange() {
        this.acl.setViewers(VALID_ACL);
        this.acl.setOwners(VALID_ACL);
        Map<String, Object> data = new HashMap<>();
        data.put("country", "USA");
        data.put("state", "TX");

        RecordData recordData = new RecordData();
        recordData.setData(data);
        this.record1.setData(data);
        Map<String, String> tags = new HashMap<>();
        tags.put("tag1", "value1");
        RecordMetadata existingRecordMetadata = new RecordMetadata();
        existingRecordMetadata.setUser(NEW_USER);
        existingRecordMetadata.setKind(KIND_1);
        existingRecordMetadata.setId(RECORD_ID1);
        existingRecordMetadata.setStatus(RecordState.active);
        existingRecordMetadata.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));
        existingRecordMetadata.setTags(tags);

        RecordMetadata newRecordMetadata = new RecordMetadata();
        newRecordMetadata.setUser(NEW_USER);
        newRecordMetadata.setKind(KIND_1);
        newRecordMetadata.setId(RECORD_ID1);
        newRecordMetadata.setStatus(RecordState.active);
        newRecordMetadata.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));
        newRecordMetadata.setTags(tags);
        newRecordMetadata.setHash(recordBlocks.hashForRecordData(recordData));


        Map<String, RecordMetadata> existingRecord = new HashMap<>();
        existingRecord.put(RECORD_ID1, existingRecordMetadata);

        RecordProcessing recordProcessing = new RecordProcessing();
        recordProcessing.setRecordMetadata(newRecordMetadata);
        recordProcessing.setRecordData(recordData);
        recordProcessing.setOperationType(OperationType.update);
        List<RecordProcessing> recordsToProcess = new ArrayList<>();
        recordsToProcess.add(recordProcessing);

        Mockito.when(cloudStorage.read(any(), anyLong(), anyBoolean())).thenReturn(new Gson().toJson(this.record1));
        recordBlocks.populateRecordBlocksMetadata(existingRecord, recordsToProcess, Optional.empty());
        assertEquals("", recordsToProcess.get(0).getRecordBlocks());
    }

    @Test
    public void populateRecordBlocksDetectsAclChangeWhenAclHashCodeCollides() {
        // "Aa" and "BB" have the same String.hashCode(), so both ACLs have the same Acl.hashCode()
        Acl existingAcl = new Acl(new String[]{"data.Aa.viewers@tenant1.gmail.com"}, VALID_ACL);
        Acl newAcl = new Acl(new String[]{"data.BB.viewers@tenant1.gmail.com"}, VALID_ACL);
        assertEquals(existingAcl.hashCode(), newAcl.hashCode());

        List<RecordProcessing> recordsToProcess = populateRecordBlocksForAclUpdate(existingAcl, newAcl);

        assertEquals("metadata", recordsToProcess.get(0).getRecordBlocks());
    }

    @Test
    public void populateRecordBlocksEmptyWhenAclValuesUnchanged() {
        Acl existingAcl = new Acl(new String[]{"data.Aa.viewers@tenant1.gmail.com"}, VALID_ACL);
        Acl newAcl = new Acl(new String[]{"data.Aa.viewers@tenant1.gmail.com"}, VALID_ACL);

        List<RecordProcessing> recordsToProcess = populateRecordBlocksForAclUpdate(existingAcl, newAcl);

        assertEquals("", recordsToProcess.get(0).getRecordBlocks());
    }

    @Test
    public void populateRecordBlocksWhenAclAdded() {
        Acl newAcl = new Acl(VALID_ACL, VALID_ACL);

        assertEquals("metadata+", populateRecordBlocksForAclUpdate(null, newAcl).get(0).getRecordBlocks());
    }

    @Test
    public void populateRecordBlocksWhenAclRemoved() {
        Acl existingAcl = new Acl(VALID_ACL, VALID_ACL);

        assertEquals("metadata-", populateRecordBlocksForAclUpdate(existingAcl, null).get(0).getRecordBlocks());
    }

    @Test
    public void populateRecordBlocksEmptyWhenAclAbsent() {
        assertEquals("", populateRecordBlocksForAclUpdate(null, null).get(0).getRecordBlocks());
    }

    @Test
    public void populateRecordBlocksWhenDataAndAclChange() {
        Map<String, Object> existingData = new HashMap<>();
        existingData.put("country", "USA");
        RecordData existingRecordData = new RecordData();
        existingRecordData.setData(existingData);
        Map<String, Object> newData = new HashMap<>();
        newData.put("country", "CAN");
        RecordData newRecordData = new RecordData();
        newRecordData.setData(newData);

        RecordMetadata existingMetadata = new RecordMetadata();
        existingMetadata.setAcl(new Acl(new String[]{"data.Aa.viewers@tenant1.gmail.com"}, VALID_ACL));
        existingMetadata.setHash(recordBlocks.hashForRecordData(existingRecordData));
        RecordMetadata newMetadata = new RecordMetadata();
        newMetadata.setAcl(new Acl(new String[]{"data.BB.viewers@tenant1.gmail.com"}, VALID_ACL));
        newMetadata.setHash(recordBlocks.hashForRecordData(newRecordData));

        assertEquals("data metadata", populateRecordBlocksForMetadataUpdate(existingMetadata, newMetadata).get(0).getRecordBlocks());
    }

    private List<RecordProcessing> populateRecordBlocksForAclUpdate(Acl existingAcl, Acl newAcl) {
        RecordMetadata existingRecordMetadata = new RecordMetadata();
        existingRecordMetadata.setAcl(existingAcl);
        RecordMetadata newRecordMetadata = new RecordMetadata();
        newRecordMetadata.setAcl(newAcl);
        return populateRecordBlocksForMetadataUpdate(existingRecordMetadata, newRecordMetadata);
    }

    @Test
    public void populateRecordBlocksDetectsLegalChangeWhenHashCodeCollides() {
        Legal existingLegal = new Legal();
        existingLegal.setLegaltags(Sets.newHashSet("legal-Aa"));
        Legal newLegal = new Legal();
        newLegal.setLegaltags(Sets.newHashSet("legal-BB"));
        assertNotEquals(existingLegal, newLegal);
        assertEquals(existingLegal.hashCode(), newLegal.hashCode());

        RecordMetadata existingMetadata = new RecordMetadata();
        existingMetadata.setLegal(existingLegal);
        RecordMetadata newMetadata = new RecordMetadata();
        newMetadata.setLegal(newLegal);

        assertEquals("metadata", populateRecordBlocksForMetadataUpdate(existingMetadata, newMetadata).get(0).getRecordBlocks());
    }

    @Test
    public void populateRecordBlocksDetectsTagsChangeWhenHashCodeCollides() {
        Map<String, String> existingTags = new HashMap<>();
        existingTags.put("tag", "Aa");
        Map<String, String> newTags = new HashMap<>();
        newTags.put("tag", "BB");
        assertNotEquals(existingTags, newTags);
        assertEquals(existingTags.hashCode(), newTags.hashCode());

        RecordMetadata existingMetadata = new RecordMetadata();
        existingMetadata.setTags(existingTags);
        RecordMetadata newMetadata = new RecordMetadata();
        newMetadata.setTags(newTags);

        assertEquals("metadata", populateRecordBlocksForMetadataUpdate(existingMetadata, newMetadata).get(0).getRecordBlocks());
    }

    @Test
    public void populateRecordBlocksDetectsAncestryChangeWhenHashCodeCollides() {
        RecordAncestry existingAncestry = new RecordAncestry();
        existingAncestry.setParents(Sets.newHashSet("tenant1:kind:Aa:1"));
        RecordAncestry newAncestry = new RecordAncestry();
        newAncestry.setParents(Sets.newHashSet("tenant1:kind:BB:1"));
        assertNotEquals(existingAncestry, newAncestry);
        assertEquals(existingAncestry.hashCode(), newAncestry.hashCode());

        RecordMetadata existingMetadata = new RecordMetadata();
        existingMetadata.setAncestry(existingAncestry);
        RecordMetadata newMetadata = new RecordMetadata();
        newMetadata.setAncestry(newAncestry);

        assertEquals("metadata", populateRecordBlocksForMetadataUpdate(existingMetadata, newMetadata).get(0).getRecordBlocks());
    }

    @Test
    public void populateRecordBlocksEmptyWhenMetadataValuesUnchanged() {
        Legal existingLegal = new Legal();
        existingLegal.setLegaltags(Sets.newHashSet("legal1", "legal2"));
        Legal newLegal = new Legal();
        newLegal.setLegaltags(Sets.newHashSet("legal2", "legal1"));
        RecordAncestry existingAncestry = new RecordAncestry();
        existingAncestry.setParents(Sets.newHashSet("tenant1:kind:parent:1"));
        RecordAncestry newAncestry = new RecordAncestry();
        newAncestry.setParents(Sets.newHashSet("tenant1:kind:parent:1"));

        RecordMetadata existingMetadata = new RecordMetadata();
        existingMetadata.setLegal(existingLegal);
        existingMetadata.setAncestry(existingAncestry);
        existingMetadata.setTags(new HashMap<>(Collections.singletonMap("tag", "value")));
        RecordMetadata newMetadata = new RecordMetadata();
        newMetadata.setLegal(newLegal);
        newMetadata.setAncestry(newAncestry);
        newMetadata.setTags(new HashMap<>(Collections.singletonMap("tag", "value")));

        assertEquals("", populateRecordBlocksForMetadataUpdate(existingMetadata, newMetadata).get(0).getRecordBlocks());
    }

    @Test
    public void populateRecordBlocksDetectsMetaChangeWhenCrcStringHashCodeCollides() {
        String existingMetaHash = "AaAAAA==";
        String newMetaHash = "BBAAAA==";
        assertNotEquals(existingMetaHash, newMetaHash);
        assertEquals(existingMetaHash.hashCode(), newMetaHash.hashCode());

        RecordMetadata existingMetadata = new RecordMetadata();
        existingMetadata.setHash(Collections.singletonMap("meta", existingMetaHash));
        RecordMetadata newMetadata = new RecordMetadata();
        newMetadata.setHash(Collections.singletonMap("meta", newMetaHash));

        assertEquals("metadata", populateRecordBlocksForMetadataUpdate(existingMetadata, newMetadata).get(0).getRecordBlocks());
    }

    private List<RecordProcessing> populateRecordBlocksForMetadataUpdate(RecordMetadata existingRecordMetadata, RecordMetadata newRecordMetadata) {
        Map<String, Object> data = new HashMap<>();
        data.put("country", "USA");
        RecordData recordData = new RecordData();
        recordData.setData(data);

        existingRecordMetadata.setKind(KIND_1);
        existingRecordMetadata.setId(RECORD_ID1);
        if (existingRecordMetadata.getHash() == null) {
            existingRecordMetadata.setHash(recordBlocks.hashForRecordData(recordData));
        }

        newRecordMetadata.setKind(KIND_1);
        newRecordMetadata.setId(RECORD_ID1);
        if (newRecordMetadata.getHash() == null) {
            newRecordMetadata.setHash(recordBlocks.hashForRecordData(recordData));
        }

        Map<String, RecordMetadata> existingRecord = new HashMap<>();
        existingRecord.put(RECORD_ID1, existingRecordMetadata);

        RecordProcessing recordProcessing = new RecordProcessing();
        recordProcessing.setRecordMetadata(newRecordMetadata);
        recordProcessing.setRecordData(recordData);
        recordProcessing.setOperationType(OperationType.update);
        List<RecordProcessing> recordsToProcess = new ArrayList<>();
        recordsToProcess.add(recordProcessing);

        recordBlocks.populateRecordBlocksMetadata(existingRecord, recordsToProcess, Optional.empty());
        return recordsToProcess;
    }


    @Test
    public void populateRecordBlocksWhenChangeInDataOnly() {
        this.acl.setViewers(VALID_ACL);
        this.acl.setOwners(VALID_ACL);
        Map<String, Object> data = new HashMap<>();
        data.put("country", "USA");
        data.put("state", "TX");

        RecordData existingRecordData = new RecordData();
        existingRecordData.setData(data);
        this.record1.setData(data);
        Map<String, String> tags = new HashMap<>();
        tags.put("tag1", "value1");
        RecordMetadata existingRecordMetadata = new RecordMetadata();
        existingRecordMetadata.setUser(NEW_USER);
        existingRecordMetadata.setKind(KIND_1);
        existingRecordMetadata.setId(RECORD_ID1);
        existingRecordMetadata.setStatus(RecordState.active);
        existingRecordMetadata.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));
        existingRecordMetadata.setTags(tags);
        existingRecordMetadata.setHash(recordBlocks.hashForRecordData(existingRecordData));

        Map<String, Object> newData = new HashMap<>();
        newData.put("country", "USA");
        newData.put("state", "TX");
        newData.put("some key", "some value");
        RecordData newRecordData = new RecordData();
        newRecordData.setData(newData);
        RecordMetadata newRecordMetadata = new RecordMetadata();
        newRecordMetadata.setUser(NEW_USER);
        newRecordMetadata.setKind(KIND_1);
        newRecordMetadata.setId(RECORD_ID1);
        newRecordMetadata.setStatus(RecordState.active);
        newRecordMetadata.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));
        newRecordMetadata.setTags(tags);
        newRecordMetadata.setHash(recordBlocks.hashForRecordData(newRecordData));


        Map<String, RecordMetadata> existingRecord = new HashMap<>();
        existingRecord.put(RECORD_ID1, existingRecordMetadata);

        RecordProcessing recordProcessing = new RecordProcessing();
        recordProcessing.setRecordMetadata(newRecordMetadata);
        recordProcessing.setRecordData(newRecordData);
        recordProcessing.setOperationType(OperationType.update);
        List<RecordProcessing> recordsToProcess = new ArrayList<>();
        recordsToProcess.add(recordProcessing);

        Mockito.lenient().when(cloudStorage.read(any(), anyLong(), anyBoolean())).thenReturn(new Gson().toJson(this.record1));
        recordBlocks.populateRecordBlocksMetadata(existingRecord, recordsToProcess, Optional.empty());
        assertEquals("data", recordsToProcess.get(0).getRecordBlocks());
    }

    @Test
    public void populateRecordBlocksWhenChangeInMetaDataOnly() {
        this.acl.setViewers(VALID_ACL);
        this.acl.setOwners(VALID_ACL);
        Map<String, Object> data = new HashMap<>();
        data.put("country", "USA");
        data.put("state", "TX");

        RecordData existingRecordData = new RecordData();
        existingRecordData.setData(data);
        this.record1.setData(data);
        Map<String, String> tags = new HashMap<>();
        tags.put("tag1", "value1");
        RecordMetadata existingRecordMetadata = new RecordMetadata();
        existingRecordMetadata.setUser(NEW_USER);
        existingRecordMetadata.setKind(KIND_1);
        existingRecordMetadata.setId(RECORD_ID1);
        existingRecordMetadata.setStatus(RecordState.active);
        existingRecordMetadata.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));
        existingRecordMetadata.setTags(tags);
        existingRecordMetadata.setHash(recordBlocks.hashForRecordData(existingRecordData));

        Map<String, Object> newData = new HashMap<>();
        newData.put("country", "USA");
        newData.put("state", "TX");

        Map<String, String> updatedTags = new HashMap<>();
        updatedTags.put("tag1", "new value1");
        RecordData newRecordData = new RecordData();
        newRecordData.setData(newData);
        RecordMetadata newRecordMetadata = new RecordMetadata();
        newRecordMetadata.setUser(NEW_USER);
        newRecordMetadata.setKind(KIND_1);
        newRecordMetadata.setId(RECORD_ID1);
        newRecordMetadata.setStatus(RecordState.active);
        newRecordMetadata.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));
        newRecordMetadata.setTags(updatedTags);
        newRecordMetadata.setHash(recordBlocks.hashForRecordData(newRecordData));


        Map<String, RecordMetadata> existingRecord = new HashMap<>();
        existingRecord.put(RECORD_ID1, existingRecordMetadata);

        RecordProcessing recordProcessing = new RecordProcessing();
        recordProcessing.setRecordMetadata(newRecordMetadata);
        recordProcessing.setRecordData(newRecordData);
        recordProcessing.setOperationType(OperationType.update);
        List<RecordProcessing> recordsToProcess = new ArrayList<>();
        recordsToProcess.add(recordProcessing);

        Mockito.lenient().when(cloudStorage.read(any(), anyLong(), anyBoolean())).thenReturn(new Gson().toJson(this.record1));
        recordBlocks.populateRecordBlocksMetadata(existingRecord, recordsToProcess, Optional.empty());
        assertEquals("metadata", recordsToProcess.get(0).getRecordBlocks());
    }

    @Test
    public void populateRecordBlocksWhenNewMetaFieldAdded() {
        // meta added
        this.acl.setViewers(VALID_ACL);
        this.acl.setOwners(VALID_ACL);
        Map<String, Object> data = new HashMap<>();
        data.put("country", "USA");
        data.put("state", "TX");

        RecordData existingRecordData = new RecordData();
        existingRecordData.setData(data);
        this.record1.setData(data);
        RecordMetadata existingRecordMetadata = new RecordMetadata();
        existingRecordMetadata.setUser(NEW_USER);
        existingRecordMetadata.setKind(KIND_1);
        existingRecordMetadata.setId(RECORD_ID1);
        existingRecordMetadata.setStatus(RecordState.active);
        existingRecordMetadata.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));

        existingRecordMetadata.setHash(recordBlocks.hashForRecordData(existingRecordData));

        Map<String, Object> newData = new HashMap<>();
        newData.put("country", "USA");
        newData.put("state", "TX");

        Map<String, String> newMeta = new HashMap<>();
        newMeta.put("some meta key", "some meta value");
        RecordData newRecordData = new RecordData();
        newRecordData.setData(newData);
        newRecordData.setMeta(new Map[]{newMeta});
        RecordMetadata newRecordMetadata = new RecordMetadata();
        newRecordMetadata.setUser(NEW_USER);
        newRecordMetadata.setKind(KIND_1);
        newRecordMetadata.setId(RECORD_ID1);
        newRecordMetadata.setStatus(RecordState.active);
        newRecordMetadata.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));
        newRecordMetadata.setHash(recordBlocks.hashForRecordData(newRecordData));


        Map<String, RecordMetadata> existingRecord = new HashMap<>();
        existingRecord.put(RECORD_ID1, existingRecordMetadata);

        RecordProcessing recordProcessing = new RecordProcessing();
        recordProcessing.setRecordMetadata(newRecordMetadata);
        recordProcessing.setRecordData(newRecordData);
        recordProcessing.setOperationType(OperationType.update);
        List<RecordProcessing> recordsToProcess = new ArrayList<>();
        recordsToProcess.add(recordProcessing);

        Mockito.lenient().when(cloudStorage.read(any(), anyLong(), anyBoolean())).thenReturn(new Gson().toJson(this.record1));
        recordBlocks.populateRecordBlocksMetadata(existingRecord, recordsToProcess, Optional.empty());
        assertEquals("metadata+", recordsToProcess.get(0).getRecordBlocks());
    }

    @Test
    public void populateRecordBlocksWhenNewMetaFieldRemoved() {
        // meta Removed
        this.acl.setViewers(VALID_ACL);
        this.acl.setOwners(VALID_ACL);
        Map<String, Object> data = new HashMap<>();
        data.put("country", "USA");
        data.put("state", "TX");
        Map<String, String> existingMeta = new HashMap<>();
        existingMeta.put("some meta key", "some meta value");

        RecordData existingRecordData = new RecordData();
        existingRecordData.setData(data);
        existingRecordData.setMeta(new Map[]{existingMeta});
//        this.record1.setData(data);
//        this.record1.setMeta(new Map[]{existingMeta});
        RecordMetadata existingRecordMetadata = new RecordMetadata();
        existingRecordMetadata.setUser(NEW_USER);
        existingRecordMetadata.setKind(KIND_1);
        existingRecordMetadata.setId(RECORD_ID1);
        existingRecordMetadata.setStatus(RecordState.active);
        existingRecordMetadata.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));
        existingRecordMetadata.setHash(recordBlocks.hashForRecordData(existingRecordData));

        Map<String, Object> newData = new HashMap<>();
        newData.put("country", "USA");
        newData.put("state", "TX");


        RecordData newRecordData = new RecordData();
        newRecordData.setData(newData);
        RecordMetadata newRecordMetadata = new RecordMetadata();
        newRecordMetadata.setUser(NEW_USER);
        newRecordMetadata.setKind(KIND_1);
        newRecordMetadata.setId(RECORD_ID1);
        newRecordMetadata.setStatus(RecordState.active);
        newRecordMetadata.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));
        newRecordMetadata.setHash(recordBlocks.hashForRecordData(newRecordData));


        Map<String, RecordMetadata> existingRecord = new HashMap<>();
        existingRecord.put(RECORD_ID1, existingRecordMetadata);

        RecordProcessing recordProcessing = new RecordProcessing();
        recordProcessing.setRecordMetadata(newRecordMetadata);
        recordProcessing.setRecordData(newRecordData);
        recordProcessing.setOperationType(OperationType.update);
        List<RecordProcessing> recordsToProcess = new ArrayList<>();
        recordsToProcess.add(recordProcessing);


        recordBlocks.populateRecordBlocksMetadata(existingRecord, recordsToProcess, Optional.empty());
        assertEquals("metadata-", recordsToProcess.get(0).getRecordBlocks());
    }

    @Test
    public void populateRecordBlocksWhenChangeInBothDataAndMetadata() {
        this.acl.setViewers(VALID_ACL);
        this.acl.setOwners(VALID_ACL);
        Map<String, Object> data = new HashMap<>();
        data.put("country", "USA");
        data.put("state", "TX");

        RecordData existingRecordData = new RecordData();
        existingRecordData.setData(data);
        this.record1.setData(data);
        Map<String, String> tags = new HashMap<>();
        tags.put("tag1", "value1");
        RecordMetadata existingRecordMetadata = new RecordMetadata();
        existingRecordMetadata.setUser(NEW_USER);
        existingRecordMetadata.setKind(KIND_1);
        existingRecordMetadata.setId(RECORD_ID1);
        existingRecordMetadata.setStatus(RecordState.active);
        existingRecordMetadata.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));
        existingRecordMetadata.setTags(tags);
        existingRecordMetadata.setHash(recordBlocks.hashForRecordData(existingRecordData));

        Map<String, Object> newData = new HashMap<>();
        newData.put("country", "USA");
        newData.put("state", "TX2");

        Map<String, String> updatedTags = new HashMap<>();
        updatedTags.put("tag1", "new value1");
        RecordData newRecordData = new RecordData();
        newRecordData.setData(newData);
        RecordMetadata newRecordMetadata = new RecordMetadata();
        newRecordMetadata.setUser(NEW_USER);
        newRecordMetadata.setKind(KIND_1);
        newRecordMetadata.setId(RECORD_ID1);
        newRecordMetadata.setStatus(RecordState.active);
        newRecordMetadata.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));
        newRecordMetadata.setTags(updatedTags);
        newRecordMetadata.setHash(recordBlocks.hashForRecordData(newRecordData));


        Map<String, RecordMetadata> existingRecord = new HashMap<>();
        existingRecord.put(RECORD_ID1, existingRecordMetadata);

        RecordProcessing recordProcessing = new RecordProcessing();
        recordProcessing.setRecordMetadata(newRecordMetadata);
        recordProcessing.setRecordData(newRecordData);
        recordProcessing.setOperationType(OperationType.update);
        List<RecordProcessing> recordsToProcess = new ArrayList<>();
        recordsToProcess.add(recordProcessing);

        Mockito.lenient().when(cloudStorage.read(any(), anyLong(), anyBoolean())).thenReturn(new Gson().toJson(this.record1));
        recordBlocks.populateRecordBlocksMetadata(existingRecord, recordsToProcess, Optional.empty());
        assertEquals("data metadata", recordsToProcess.get(0).getRecordBlocks());
    }

}
