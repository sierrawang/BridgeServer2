package org.sagebionetworks.bridge.models.substudies;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class SubstudyIdTest {
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(SubstudyId.class).allFieldsShouldBeUsed()
            .suppress(Warning.NONFINAL_FIELDS).verify();
    }
    
    @Test
    public void test() {
        SubstudyId substudyId = new SubstudyId(TEST_APP_ID, "id");
        
        assertEquals(substudyId.getAppId(), TEST_APP_ID);
        assertEquals(substudyId.getId(), "id");
    }    
}
