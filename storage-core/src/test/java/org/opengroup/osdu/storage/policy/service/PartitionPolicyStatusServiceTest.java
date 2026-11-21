package org.opengroup.osdu.storage.policy.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.storage.service.IPartitionService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartitionPolicyStatusServiceTest {

    @Mock
    private IPartitionService partitionService;

    @Mock
    private JaxRsDpsLog logger;

    @InjectMocks
    private PartitionPolicyStatusService service;

    @Test
    void shouldReturnDisabledWhenOptionalCacheIsUnavailable() {
        when(partitionService.getPartition("tenant")).thenThrow(new IllegalStateException("Partition unavailable"));

        assertFalse(service.policyEnabled("tenant"));
    }
}
