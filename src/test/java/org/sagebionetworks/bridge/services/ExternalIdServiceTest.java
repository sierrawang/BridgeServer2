package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import java.util.Optional;
import java.util.Set;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.dao.ExternalIdDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.substudies.Substudy;

import com.google.common.collect.ImmutableSet;

public class ExternalIdServiceTest {

    private static final String ID = "AAA";
    private static final String SUBSTUDY_ID = "substudyId";
    private static final Set<String> SUBSTUDIES = ImmutableSet.of(SUBSTUDY_ID);
    private static final String HEALTH_CODE = "healthCode";
    
    private App app;
    private ExternalIdentifier extId;
    
    @Mock
    private ExternalIdDao externalIdDao;
    
    @Mock
    private SubstudyService substudyService;
    
    private ExternalIdService externalIdService;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID).build());
        app = App.create();
        app.setIdentifier(TEST_APP_ID);
        extId = ExternalIdentifier.create(TEST_APP_ID, ID);
        extId.setSubstudyId(SUBSTUDY_ID);
        externalIdService = new ExternalIdService();
        externalIdService.setExternalIdDao(externalIdDao);
        externalIdService.setSubstudyService(substudyService);
    }
    
    @AfterMethod
    public void after() {
        BridgeUtils.setRequestContext(null);
    }
    
    @Test
    public void getExternalId() {
        when(externalIdDao.getExternalId(TEST_APP_ID, ID)).thenReturn(Optional.of(extId));
        
        Optional<ExternalIdentifier> retrieved = externalIdService.getExternalId(TEST_APP_ID, ID);
        assertEquals(retrieved.get(), extId);
    }
    
    @Test
    public void getExternalIdNoExtIdReturnsEmptyOptional() {
        when(externalIdDao.getExternalId(TEST_APP_ID, ID)).thenReturn(Optional.empty());
        
        Optional<ExternalIdentifier> optionalId = externalIdService.getExternalId(TEST_APP_ID, ID);
        assertFalse(optionalId.isPresent());
    }

    @Test
    public void getExternalIdNullExtIdReturnsEmptyOptional() {
        Optional<ExternalIdentifier> optionalId = externalIdService.getExternalId(TEST_APP_ID, null);
        assertFalse(optionalId.isPresent());
    }
    
    @Test
    public void getExternalIds() {
        externalIdService.getExternalIds("offsetKey", 10, "idFilter", true);
        
        verify(externalIdDao).getExternalIds(TEST_APP_ID, "offsetKey", 10, "idFilter", true);
    }
    
    @Test
    public void getExternalIdsDefaultsPageSize() {
        externalIdService.getExternalIds(null, null, null, null);
        
        verify(externalIdDao).getExternalIds(TEST_APP_ID, null, BridgeConstants.API_DEFAULT_PAGE_SIZE,
                null, null);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getExternalIdsUnderMinPageSizeThrows() {
        externalIdService.getExternalIds(null, 0, null, null);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getExternalIdsOverMaxPageSizeThrows() {
        externalIdService.getExternalIds(null, 10000, null, null);
    }
    
    @Test
    public void createExternalId() {
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false))
            .thenReturn(Substudy.create());
        when(externalIdDao.getExternalId(TEST_APP_ID, ID)).thenReturn(Optional.empty());
        
        externalIdService.createExternalId(extId, false);
        
        verify(externalIdDao).createExternalId(extId);
    }
    
    @Test
    public void createExternalIdEnforcesAppId() {
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false))
            .thenReturn(Substudy.create());
        when(externalIdDao.getExternalId(TEST_APP_ID, ID)).thenReturn(Optional.empty());
        
        ExternalIdentifier newExtId = ExternalIdentifier.create("some-dumb-id", ID);
        newExtId.setSubstudyId(SUBSTUDY_ID);
        externalIdService.createExternalId(newExtId, false);
        
        // still matches and verifies
        verify(externalIdDao).createExternalId(extId);        
    }
    
    @Test
    public void createExternalIdSetsSubstudyIdIfMissingAndUnambiguous() {
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false))
            .thenReturn(Substudy.create());
        when(externalIdDao.getExternalId(TEST_APP_ID, ID)).thenReturn(Optional.empty());
        
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID)
                .withCallerSubstudies(SUBSTUDIES).build());
        
        ExternalIdentifier newExtId = ExternalIdentifier.create(TEST_APP_ID,
                extId.getIdentifier());
        externalIdService.createExternalId(newExtId, false);
        
        // still matches and verifies
        verify(externalIdDao).createExternalId(extId);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void createExternalIdDoesNotSetSubstudyIdAmbiguous() {
        extId.setSubstudyId(null); // not set by caller
        
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID)
                .withCallerSubstudies(ImmutableSet.of(SUBSTUDY_ID, "anotherSubstudy")).build());
        
        externalIdService.createExternalId(extId, false);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void createExternalIdValidates() {
        externalIdService.createExternalId(ExternalIdentifier.create("nonsense", "nonsense"), false);
    }
    
    @Test(expectedExceptions = EntityAlreadyExistsException.class)
    public void createExternalIdAlreadyExistsThrows() {
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false))
            .thenReturn(Substudy.create());
        when(externalIdDao.getExternalId(TEST_APP_ID, ID)).thenReturn(Optional.of(extId));
        extId.setSubstudyId(SUBSTUDY_ID);
        
        externalIdService.createExternalId(extId, false);
    }
    
    @Test
    public void deleteExternalIdPermanently() {
        when(externalIdDao.getExternalId(TEST_APP_ID, ID)).thenReturn(Optional.of(extId));
        
        externalIdService.deleteExternalIdPermanently(app, extId);
        
        verify(externalIdDao).deleteExternalId(extId);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteExternalIdPermanentlyMissingThrows() {
        when(externalIdDao.getExternalId(TEST_APP_ID, extId.getIdentifier())).thenReturn(Optional.empty());
        
        externalIdService.deleteExternalIdPermanently(app, extId);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteExternalIdPermanentlyOutsideSubstudiesThrows() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID)
                .withCallerSubstudies(SUBSTUDIES).build());        
        extId.setSubstudyId("someOtherId");
        when(externalIdDao.getExternalId(TEST_APP_ID, ID)).thenReturn(Optional.of(extId));
        
        externalIdService.deleteExternalIdPermanently(app, extId);
    }
    
    @Test
    public void commitAssignExternalId() {
        ExternalIdentifier externalId = ExternalIdentifier.create(TEST_APP_ID, ID);
        
        externalIdService.commitAssignExternalId(externalId);
        
        verify(externalIdDao).commitAssignExternalId(externalId);
    }

    @Test
    public void commitAssignExternalIdNullId() {
        externalIdService.commitAssignExternalId(null);
        
        verify(externalIdDao, never()).commitAssignExternalId(any());
    }

    @Test
    public void unassignExternalId() {
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setHealthCode(HEALTH_CODE);
        
        externalIdService.unassignExternalId(account, ID);
        
        verify(externalIdDao).unassignExternalId(account, ID);
    }

    @Test
    public void unassignExternalIdNullDoesNothing() {
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setHealthCode(HEALTH_CODE);
        
        externalIdService.unassignExternalId(account, null);
        
        verify(externalIdDao, never()).unassignExternalId(account, ID);
    }
}
