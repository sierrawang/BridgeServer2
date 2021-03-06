package org.sagebionetworks.bridge.models.substudies;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class AccountSubstudyTest {

    @Test
    public void create() {
        AccountSubstudy substudy = AccountSubstudy.create(TEST_APP_ID, "substudyId", "accountId");
        assertEquals(substudy.getAppId(), TEST_APP_ID);
        assertEquals(substudy.getSubstudyId(), "substudyId");
        assertEquals(substudy.getAccountId(), "accountId");
    }
    
}
