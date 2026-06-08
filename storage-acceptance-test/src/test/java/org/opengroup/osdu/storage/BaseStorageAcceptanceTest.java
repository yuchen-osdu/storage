package org.opengroup.osdu.storage;

import org.apache.hc.core5.http.HttpStatus;
import com.google.gson.Gson;
import org.opengroup.osdu.core.test.auth.UserType;
import org.opengroup.osdu.core.test.base.BaseAcceptanceTests;
import org.opengroup.osdu.core.test.client.EntitlementsClient;
import org.opengroup.osdu.core.test.client.HttpResponse;
import org.opengroup.osdu.core.test.client.LegalTagsClient;
import org.opengroup.osdu.core.test.client.model.legal.LegalTag;
import org.opengroup.osdu.core.test.client.model.legal.LegalTagProperties;
import org.opengroup.osdu.core.test.service.ServiceType;
import org.opengroup.osdu.core.test.util.ResponseUtil;
import org.opengroup.osdu.core.test.client.StorageClient;
import org.opengroup.osdu.storage.model.search.SearchCountRequest;
import org.opengroup.osdu.storage.model.search.SearchCountResponse;
import org.opengroup.osdu.storage.util.ConfigUtils;
import org.opengroup.osdu.storage.util.TestUtils;

import static org.apache.hc.core5.http.HttpStatus.SC_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.apache.hc.core5.http.Method;
import org.junit.jupiter.api.AfterEach;

public class BaseStorageAcceptanceTest extends BaseAcceptanceTests {

  private static final String TEST_PROPERTIES = "test.properties";

  /** Loaded once per test suite run when this class is first initialized. */
  protected static final ConfigUtils configUtils = loadConfig();

  private static final Gson GSON = new Gson();

  private static ConfigUtils loadConfig() {
    try {
      return new ConfigUtils(TEST_PROPERTIES);
    } catch (Exception e) {
      throw new ExceptionInInitializerError(
          new IllegalStateException("Failed to load " + TEST_PROPERTIES + " from the classpath", e));
    }
  }

  protected static final List<UserType> DEFAULT_USER_TYPES =
      List.of(UserType.PRIVILEGED_USER);

  protected static final List<ServiceType> DEFAULT_SERVICES = List.of(
      ServiceType.STORAGE_V2,
      ServiceType.LEGAL_V1,
      ServiceType.ENTITLEMENTS_V2,
      ServiceType.SEARCH_V2,
      ServiceType.INDEXER_V2);

  protected final LegalTagsClient legalTagClient;
  protected final EntitlementsClient entitlementsClient;
  protected final StorageClient storageClient;

  protected BaseStorageAcceptanceTest() {
    this(DEFAULT_USER_TYPES);
  }

  protected BaseStorageAcceptanceTest(List<UserType> userTypes) {
    super(userTypes, DEFAULT_SERVICES);
    this.legalTagClient = new LegalTagsClient(this.stringHttpClient, getDefaultUser());
    this.entitlementsClient = new EntitlementsClient(this.stringHttpClient);
    this.storageClient = new StorageClient(this.stringHttpClient, getDefaultUser());
  }

  @Override
  protected void setup() throws Exception {
    // Shared infrastructure is initialized in the BaseAcceptanceTests constructor.
  }

  @Override
  @AfterEach
  protected void teardown() {
    this.storageClient.teardown();
    this.legalTagClient.teardown();
    this.entitlementsClient.teardown();
  }

  protected String getTenantId() {
    return servicesConfig.getDataPartitionId();
  }

  protected String getAclSuffix() {
    return String.format("%s.%s", getTenantId(), TestUtils.getGroupId());
  }

  protected String getAcl() {
    return String.format("data.test1@%s", getAclSuffix());
  }

  protected String createLegalTagName(String suffix) {
    return getTenantId() + "-storage-" + System.currentTimeMillis() + suffix;
  }

  protected LegalTag buildLegalTag(String legalTagName) {
    LegalTagProperties properties = new LegalTagProperties(
        "A1234",
        "MyCompany",
        List.of("US"),
        "Public",
        "EAR99",
        "No Personal Data",
        "2099-01-25",
        "Public Domain Data");
    return new LegalTag(null, true, legalTagName, properties, "test for " + legalTagName);
  }

  protected HttpResponse<LegalTag> createLegalTag(String legalTagName) throws Exception {
    HttpResponse<LegalTag> response = legalTagClient.create(buildLegalTag(legalTagName));
    assertEquals(HttpStatus.SC_CREATED, response.statusCode(),
        () -> "Failed to create legal tag " + legalTagName);
    Thread.sleep(100);
    return response;
  }

  protected SearchCountResponse sendSearchQuery(SearchCountRequest body) throws Exception {
    String json = GSON.toJson(body);
    HttpResponse<String> response = send(UserType.PRIVILEGED_USER, ServiceType.SEARCH_V2, "query",
        Method.POST, "",
        json, null);
    assertEquals(SC_OK, response.statusCode());
    return ResponseUtil.fromJson(GSON, response.body(), SearchCountResponse.class);
  }

  protected HttpResponse<String> sendDeleteIndex(String path) throws Exception {
    return send(UserType.PRIVILEGED_USER, ServiceType.INDEXER_V2, path, Method.DELETE, "",
        "", null);
  }
}
