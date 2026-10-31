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

package org.opengroup.osdu.storage.conversion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.crs.CrsConversionServiceErrorMessages;
import org.opengroup.osdu.core.common.crs.CrsConverterFactory;
import org.opengroup.osdu.core.common.crs.CrsConverterService;
import org.opengroup.osdu.core.common.http.HttpResponse;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.crs.*;
import org.opengroup.osdu.core.common.model.crs.GeoJson.GeoJsonBase;
import org.opengroup.osdu.core.common.model.crs.GeoJson.GeoJsonFeature;
import org.opengroup.osdu.core.common.model.crs.GeoJson.GeoJsonFeatureCollection;
import org.opengroup.osdu.core.common.model.crs.GeoJson.GeoJsonGeometryCollection;
import org.opengroup.osdu.core.common.model.crs.GeoJson.GeoJsonLineString;
import org.opengroup.osdu.core.common.model.crs.GeoJson.GeoJsonMultiLineString;
import org.opengroup.osdu.core.common.model.crs.GeoJson.GeoJsonMultiPoint;
import org.opengroup.osdu.core.common.model.crs.GeoJson.GeoJsonMultiPolygon;
import org.opengroup.osdu.core.common.model.crs.GeoJson.GeoJsonPoint;
import org.opengroup.osdu.core.common.model.crs.GeoJson.GeoJsonPolygon;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.ConversionStatus;
import org.opengroup.osdu.core.common.util.IServiceAccountJwtClient;
import org.opengroup.osdu.storage.di.CrsConversionConfig;
import org.opengroup.osdu.storage.di.SpringConfig;
import org.opengroup.osdu.storage.util.ConversionJsonUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CrsConversionServiceTest {

    @Mock
    private CrsConverterFactory crsConverterFactory;

    @Mock
    private DpsHeaders dpsHeaders;

    @Mock
    private CrsConverterService crsConverterService;
   
    @Mock
    private CrsPropertySet crsPropertySet;

    @Mock
    private JaxRsDpsLog logger;

    @Mock
    private IServiceAccountJwtClient jwtClient;

    @InjectMocks
    private CrsConversionService sut;
    
    @Mock
    private SpringConfig springConfig;

    @Mock
    private CrsConversionConfig crsConversionConfig;

    @Mock
    private DpsConversionService dpsConversionService;

    private ConversionJsonUtils conversionJsonUtils = new ConversionJsonUtils();

    private List<JsonObject> originalRecords = new ArrayList<>();
    private List<ConversionStatus.ConversionStatusBuilder> conversionStatuses = new ArrayList<>();
    private List<Point> convertedPoints = new ArrayList<>();
    private ConvertPointsResponse convertPointsResponse = new ConvertPointsResponse();
    private JsonParser jsonParser = new JsonParser();
    private Set<String> nestedPropertyNames = new HashSet<>();
    private Map<String, String> pairProperty = new HashMap<>();
   
    private static final String RECORD_1 = "{\"id\":\"unit-test-1\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":16.00,\"Y\":10.00,\"Z\":0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_2 = "{\"id\":\"unit-test-2\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":16.00,\"Y\":10.00,\"Z\":0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_3 = "{\"id\":\"unit-test-3\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":16.00,\"Y\":10.00,\"Z\":0},\"meta\":[{\"path\":\"\",\"kind\":\"unit\",\"persistableReference\":\"reference\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_4 = "{\"id\":\"unit-test-4\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"Y\":10.00,\"Z\":0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_5 = "{\"id\":\"unit-test-5\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":16.00,\"Y\":10.00,\"Z\":0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_6 = "{\"id\":\"unit-test-6\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":16.00,\"Y\":10.00,\"Z\":0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"X\",\"Y\",\"Z\",\"T\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_7 = "{\"id\":\"unit-test-7\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":16.00,\"Y\":10.00},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"X\",\"Y\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_8 = "{\"id\":\"unit-test-8\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"LON\":16.00,\"LAT\":10.00,\"Z\":0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"LON\",\"LAT\",\"Z\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_9 = "{\"id\":\"unit-test-9\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"nested\":0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"nestedProperty\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_10 = "{\"id\":\"unit-test-10\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\"},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"validNestedProperty\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_11 = "{\"id\":\"unit-test-11\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"validNestedProperty\":{\"crsKey\":\"Native\"}},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"validNestedProperty\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_12 = "{\"id\":\"unit-test-12\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"validNestedProperty\":{\"crsKey\":\"Native\",\"points\":[[16.00,10.00],[16.00,10.00]]}},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"validNestedProperty\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_13 = "{\"id\":\"unit-test-13\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":16.45,\"Y\":10.07,\"Z\":0.0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_14 = "{\"id\":\"unit-test-14\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":null,\"Y\":10.00,\"Z\":0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_15 = "{\"id\":\"unit-test-15\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":\"yes\",\"Y\":10.00,\"Z\":0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_16 = "{\"id\":\"unit-test-16\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_17 = "{\"id\":\"unit-test-17\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"validNestedProperty\":[[16.00,10.00],[16.00,10.00]]},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"validNestedProperty\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_18 = "{\"id\":\"unit-test-1\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"x\":16.00,\"y\":10.00,\"Z\":0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"x\",\"y\",\"Z\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_19 = "{\"id\":\"unit-test-3\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":16.00,\"Y\":10.00,\"Z\":0},\"meta\":{\"path\":\"\",\"kind\":\"unit\",\"persistableReference\":\"reference\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\"}}";
    private static final String RECORD_20 = "{\"id\":\"unit-test-20\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"Nested\":{\"X\":10.0,\"Y\":10.00}},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"Nested.X\",\"Nested.Y\"],\"name\":\"GCS_WGS_1984\"}]}";
    private static final String RECORD_21 = "{\"id\":\"unit-test-21\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"Nested\":{\"X\":10.0,\"Y\":10.00}},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\", \"persistableReference\": { \"scaleOffset\": {\"scale\": 0.3048, \"offset\": 0 }, \"symbol\": \"ft/s\", \"baseMeasurement\": { \"type\": \"UM\", \"ancestry\": \"Velocity\" }, \"type\": \"USO\" } ,\"propertyNames\":[\"Nested.X\",\"Nested.Y\"],\"name\":\"GCS_WGS_1984\"}]}";


    private static final String CONVERTED_RECORD_1 = "{\"id\":\"unit-test-1\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":15788.036,\"Y\":9567.4,\"Z\":0.0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\",\"persistableReference\":\"%s\"}]}";
    private static final String CONVERTED_RECORD_2 = "{\"id\":\"unit-test-2\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":15788.036,\"Y\":9567.4,\"Z\":0.0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\",\"persistableReference\":\"%s\"}]}";
    private static final String CONVERTED_RECORD_3 = "{\"id\":\"unit-test-7\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":15788.036,\"Y\":9567.4,\"Z\":0.0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"propertyNames\":[\"X\",\"Y\"],\"name\":\"GCS_WGS_1984\",\"persistableReference\":\"%s\"}]}";
    private static final String CONVERTED_RECORD_4 = "{\"id\":\"unit-test-8\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"LON\":15788.036,\"LAT\":9567.4,\"Z\":0.0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"propertyNames\":[\"LON\",\"LAT\",\"Z\"],\"name\":\"GCS_WGS_1984\",\"persistableReference\":\"%s\"}]}";
    private static final String CONVERTED_RECORD_5 = "{\"id\":\"unit-test-12\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"validNestedProperty\":{\"crsKey\":\"Native\",\"points\":[[15788.036,9567.4,0.0],[15788.036,9567.4,0.0]]}},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"propertyNames\":[\"validNestedProperty\"],\"name\":\"GCS_WGS_1984\",\"persistableReference\":\"%s\"}]}";
    private static final String TO_CRS = "{\\\"wkt\\\":\\\"GEOGCS[\\\\\\\"GCS_WGS_1984\\\\\\\",DATUM[\\\\\\\"D_WGS_1984\\\\\\\",SPHEROID[\\\\\\\"WGS_1984\\\\\\\",6378137.0,298.257223563]],PRIMEM[\\\\\\\"Greenwich\\\\\\\",0.0],UNIT[\\\\\\\"Degree\\\\\\\",0.0174532925199433],AUTHORITY[\\\\\\\"EPSG\\\\\\\",4326]]\\\",\\\"ver\\\":\\\"PE_10_3_1\\\",\\\"name\\\":\\\"GCS_WGS_1984\\\",\\\"authCode\\\":{\\\"auth\\\":\\\"EPSG\\\",\\\"code\\\":\\\"4326\\\"},\\\"type\\\":\\\"LBC\\\"}";
    private static final String CONVERTED_RECORD_6 = "{\"id\":\"unit-test-1\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"x\":15788.036,\"y\":9567.4,\"Z\":0.0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\",\"persistableReference\":\"%s\"}]}";
    private static final String CONVERTED_RECORD_7 = "{\"id\":\"unit-test-6\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"X\":15788.036,\"Y\":9567.4,\"Z\":0.0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"propertyNames\":[\"X\",\"Y\",\"Z\",\"T\"],\"name\":\"GCS_WGS_1984\",\"persistableReference\":\"%s\"}]}";
    private static final String CONVERTED_RECORD_8 = "{\"id\":\"unit-test-20\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"Nested\":{\"X\":15788.036,\"Y\":9567.4},\"Z\":0.0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"propertyNames\":[\"Nested.X\",\"Nested.Y\"],\"name\":\"GCS_WGS_1984\",\"persistableReference\":\"%s\"}]}";
    private static final String CONVERTED_RECORD_9 = "{\"id\":\"unit-test-21\",\"kind\":\"unit:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"msg\":\"testing record 1\",\"Nested\":{\"X\":15788.036,\"Y\":9567.4},\"Z\":0.0},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"propertyNames\":[\"Nested.X\",\"Nested.Y\"],\"name\":\"GCS_WGS_1984\",\"persistableReference\":\"%s\"}]}";

    private static final String GEO_JSON_RECORD = "{\"id\":\"geo-json-point-test\",\"kind\":\"geo-json-point:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"SpatialLocation\":{\"AsIngestedCoordinates\":{\"features\":[{\"geometry\":{\"coordinates\":[313405.9477893702,6544797.620047403,6.561679790026246],\"bbox\":null,\"type\":\"AnyCrsPoint\"},\"bbox\":null,\"properties\":{},\"type\":\"AnyCrsFeature\"}],\"bbox\":null,\"properties\":{},\"persistableReferenceCrs\":\"reference\",\"persistableReferenceUnitZ\":\"reference\",\"type\":\"CrsFeatureCollection\"},\"msg\":\"testing record 2\",\"X\":16.00,\"Y\":10.00,\"Z\":0}}}";

    private static final String FILTERED_GEO_RESPONSE = "{\"SpatialLocation\":{\"AsIngestedCoordinates\":{\"features\":[{\"geometry\":{\"type\":\"AnyCrsGeometryCollection\",\"bbox\":null,\"geometries\":[{\"type\":\"Point\",\"bbox\":null,\"coordinates\":[500000.0,7000000.0]},{\"type\":\"LineString\",\"bbox\":null,\"coordinates\":[[501000.0,7001000.0],[502000.0,7002000.0]]}]},\"bbox\":null,\"properties\":{},\"type\":\"AnyCrsFeature\"}],\"bbox\":null,\"properties\":{},\"persistableReferenceCrs\":\"reference\",\"persistableReferenceUnitZ\":\"reference\",\"type\":\"AnyCrsFeatureCollection\"},\"msg\":\"testing record 2\",\"X\":16.00,\"Y\":10.00,\"Z\":0}}";

    // Same shape as FILTERED_GEO_RESPONSE, differing only in the collection child's discriminator.
    private static final String ANY_CRS_CHILD_GEO_RESPONSE = "{\"SpatialLocation\":{\"AsIngestedCoordinates\":{\"features\":[{\"geometry\":{\"type\":\"AnyCrsGeometryCollection\",\"bbox\":null,\"geometries\":[{\"type\":\"AnyCrsMultiPoint\",\"bbox\":null,\"coordinates\":[[500000.0,7000000.0],[501000.0,7001000.0]]}]},\"bbox\":null,\"properties\":{},\"type\":\"AnyCrsFeature\"}],\"bbox\":null,\"properties\":{},\"persistableReferenceCrs\":\"reference\",\"persistableReferenceUnitZ\":\"reference\",\"type\":\"AnyCrsFeatureCollection\"},\"msg\":\"testing record 2\",\"X\":16.00,\"Y\":10.00,\"Z\":0}}";

    private static final String AS_INGESTED_RECORD = "{\"id\":\"geo-json-point-test\",\"kind\":\"geo-json-collection:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"SpatialLocation\":{\"AsIngestedCoordinates\":{\"features\":[{\"geometry\":%s,\"bbox\":null,\"properties\":{},\"type\":\"AnyCrsFeature\"}],\"bbox\":null,\"properties\":{},\"persistableReferenceCrs\":\"reference\",\"persistableReferenceUnitZ\":\"reference\",\"type\":\"AnyCrsFeatureCollection\"}}}}";
    private static final String TWO_ATTRIBUTE_AS_INGESTED_RECORD = "{\"id\":\"geo-json-point-test\",\"kind\":\"geo-json-collection:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"SpatialArea\":%s,\"LastLocation\":%s}}";
    private static final String GEO_AND_META_RECORD = "{\"id\":\"combined-test\",\"kind\":\"combined:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"X\":16.00,\"Y\":10.00,\"Z\":0,\"SpatialLocation\":%s},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"X\",\"Y\",\"Z\"],\"name\":\"GCS_WGS_1984\"}]}";
    // Same as GEO_AND_META_RECORD but with two independent coordinate pairs, to prove each skipped pair gets its own message.
    private static final String TWO_PAIR_GEO_AND_META_RECORD = "{\"id\":\"combined-test\",\"kind\":\"combined:test:1.0.0\",\"acl\":{\"viewers\":[\"viewers@unittest.com\"],\"owners\":[\"owners@unittest.com\"]},\"legal\":{\"legaltags\":[\"unit-test-legal\"],\"otherRelevantDataCountries\":[\"AA\"]},\"data\":{\"X\":16.00,\"Y\":10.00,\"LON\":15.00,\"LAT\":9.00,\"SpatialLocation\":%s},\"meta\":[{\"path\":\"\",\"kind\":\"CRS\",\"persistableReference\":\"reference\",\"propertyNames\":[\"X\",\"Y\",\"LON\",\"LAT\"],\"name\":\"GCS_WGS_1984\"}]}";

    @BeforeEach
    public void setup() throws Exception{
        Point convertedPoint1 = new Point();
        convertedPoint1.setZ(0.0);
        convertedPoint1.setY(9567.40);
        convertedPoint1.setX(15788.036);
        Point convertedPoint2 = new Point();
        convertedPoint2.setZ(0.0);
        convertedPoint2.setY(9567.40);
        convertedPoint2.setX(15788.036);
        this.convertedPoints.add(convertedPoint1);
        this.convertedPoints.add(convertedPoint2);
        this.convertPointsResponse.setPoints(this.convertedPoints);

        this.nestedPropertyNames.add("validNestedProperty");
        this.pairProperty.put("x", "y");
        this.pairProperty.put("lon", "lat");
         
        lenient().when(this.crsPropertySet.getPropertyPairing()).thenReturn(this.pairProperty);
        lenient().when(this.crsPropertySet.getNestedPropertyNames()).thenReturn(this.nestedPropertyNames);

        lenient().when(this.crsConverterFactory.create(any(), any(RequestConfig.class))).thenReturn(this.crsConverterService);
        lenient().when(this.crsConverterService.convertPoints(any())).thenReturn(this.convertPointsResponse);

        lenient().when(this.jwtClient.getIdToken(any())).thenReturn("auth-token-unit-test");
        lenient().when(this.springConfig.isCreateCrsJWTToken()).thenReturn(true);
    }

    @Test
    public void should_returnOriginalRecordAndBadValueStatus_WhenBadRequestFromCrsConverter() throws Exception{
        this.originalRecords.add(this.jsonParser.parse(RECORD_13).getAsJsonObject());
        HttpResponse response = new HttpResponse();
        response.setResponseCode(HttpStatus.SC_BAD_REQUEST);
        CrsConverterException exception = new CrsConverterException("bad persistable reference", response);
        when(this.crsConverterService.convertPoints(any())).thenThrow(exception);
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-13").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        assertEquals(1, crsResult.getRecords().size());
        assertEquals(1, crsResult.getConversionStatuses().size());
        assertEquals(2, crsResult.getConversionStatuses().get(0).getErrors().size());
        assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(RECORD_13));
    }

    @ParameterizedTest(name = "HTTP {0}")
    @ValueSource(ints = {429, HttpStatus.SC_INTERNAL_SERVER_ERROR, HttpStatus.SC_SERVICE_UNAVAILABLE, HttpStatus.SC_GATEWAY_TIMEOUT})
    public void should_recordAnErrorAndLeaveTheRecordUnchanged_whenTheCrsConverterIsUnavailableForPointConversion(int responseCode) throws Exception {
        this.originalRecords.add(this.jsonParser.parse(RECORD_13).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-13").status(ConvertStatus.SUCCESS.toString()));
        when(this.crsConverterService.convertPoints(any())).thenThrow(crsConverterFailure(responseCode));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);

        ConversionStatus status = crsResult.getConversionStatuses().get(0);
        assertEquals(ConvertStatus.ERROR.toString(), status.getStatus());
        List<String> unavailableErrors = status.getErrors().stream()
                .filter(error -> error.contains("unavailable or throttled")).toList();
        assertEquals(1, unavailableErrors.size(), status.getErrors().toString());
        String error = unavailableErrors.get(0);
        assertTrue(error.contains("X, Y"), error);
        assertTrue(error.contains(String.valueOf(responseCode)), error);
        assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(RECORD_13));
    }

    @Test
    public void should_convertOtherCrsGroupsInTheBatch_whenTheCrsConverterIsUnavailableForOneGroup() throws Exception {
        String otherReferenceRecord = RECORD_2.replace("\"persistableReference\":\"reference\"", "\"persistableReference\":\"other-reference\"");
        this.originalRecords.add(this.jsonParser.parse(RECORD_1).getAsJsonObject());
        this.originalRecords.add(this.jsonParser.parse(otherReferenceRecord).getAsJsonObject());
        ConversionStatus.ConversionStatusBuilder failingStatus = ConversionStatus.builder().id("unit-test-1").status(ConvertStatus.SUCCESS.toString());
        ConversionStatus.ConversionStatusBuilder convertedStatus = ConversionStatus.builder().id("unit-test-2").status(ConvertStatus.SUCCESS.toString());
        this.conversionStatuses.add(failingStatus);
        this.conversionStatuses.add(convertedStatus);
        when(this.crsConverterService.convertPoints(any())).thenAnswer(invocation -> {
            if ("reference".equals(invocation.<ConvertPointsRequest>getArgument(0).getFromCRS())) {
                throw crsConverterFailure(HttpStatus.SC_SERVICE_UNAVAILABLE);
            }
            return this.convertPointsResponse;
        });

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);

        verify(this.crsConverterService, times(2)).convertPoints(any());
        assertEquals(ConvertStatus.ERROR.toString(), failingStatus.getStatus());
        assertEquals(ConvertStatus.SUCCESS.toString(), convertedStatus.getStatus());
        assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(RECORD_1));
        JsonObject convertedData = crsResult.getRecords().get(1).getAsJsonObject("data");
        assertEquals(15788.036, convertedData.get("X").getAsDouble());
        assertEquals(9567.40, convertedData.get("Y").getAsDouble());
    }

    @Test
    public void should_stillFailTheRequest_whenTheCrsConverterRejectsPointConversionAsForbidden() throws Exception {
        this.originalRecords.add(this.jsonParser.parse(RECORD_13).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-13").status(ConvertStatus.SUCCESS.toString()));
        when(this.crsConverterService.convertPoints(any())).thenThrow(crsConverterFailure(HttpStatus.SC_FORBIDDEN));

        AppException thrown = assertThrows(AppException.class,
                () -> this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses));
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, thrown.getError().getCode());
    }

    @Test
    public void should_returnOriginalRecordAndEmptyStatus_whenMetaIsNotCrsType() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_3).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-3").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        assertEquals(1, crsResult.getRecords().size());
        assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(RECORD_3));
    }

    @Test
    public void should_returnOriginalRecordAndErrorMessage_whenErrorParsingMetaBlock() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_19).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-3").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        assertEquals(1, crsResult.getRecords().size());
        assertEquals(1, crsResult.getConversionStatuses().size());
        assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(RECORD_19));
    }

    @Test
    public void should_returnOriginalRecordAndConversionStatus_whenMetaIsProvidedButDataMissingInDataBlock() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_4).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-4").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        assertEquals(1, crsResult.getRecords().size());
        assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(RECORD_4));
        String message = String.format(CrsConversionServiceErrorMessages.MISSING_PROPERTY,"X");
        List<String> errorMsg = crsResult.getConversionStatuses().get(0).getErrors();
        assertEquals(2, errorMsg.size());
        assertTrue(errorMsg.contains("CRS conversion: Unknown coordinate pair 'z'."));
        assertTrue(errorMsg.contains(message));
    }

    @Test
    public void should_returnConvertedRecordAndConversionStatus_whenMetaIsProvidedWithMultiplePairOfCoordinates() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_6).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-6").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        assertEquals(1, crsResult.getRecords().size());

        String converted = String.format(CONVERTED_RECORD_7, TO_CRS);
        assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(converted));
        List<String> errorMsg = crsResult.getConversionStatuses().get(0).getErrors();
        assertEquals(2, errorMsg.size());
        assertTrue(errorMsg.contains("CRS conversion: Unknown coordinate pair 'z'."));
        assertTrue(errorMsg.contains("CRS conversion: Unknown coordinate pair 't'."));
    }

    @Test
    public void should_returnOriginalRecordAndConversionStatus_whenMetaIsProvidedButMissingMandatoryPropertiesXOrY() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_5).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-5").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        assertEquals(1, crsResult.getRecords().size());
        assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(RECORD_5));
        List<String> errorMsg = crsResult.getConversionStatuses().get(0).getErrors();
        assertEquals(2, errorMsg.size());
        assertTrue(errorMsg.contains("CRS conversion: Unknown coordinate pair 'y'."));
        assertTrue(errorMsg.contains("CRS conversion: Unknown coordinate pair 'z'."));
    }

    @Test
    public void should_returnConvertedRecordsAndSuccessConversionStatus_whenZIsNotProvided() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_7).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-7").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        assertEquals(1, crsResult.getRecords().size());
        assertEquals(1, crsResult.getConversionStatuses().size());
        String converted = String.format(CONVERTED_RECORD_3, TO_CRS);
        assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(converted));
    }

    @Test
    public void should_returnConvertedRecordsAndSuccessConversionStatus_whenValidRecordsProvided() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_1).getAsJsonObject());
        this.originalRecords.add(this.jsonParser.parse(RECORD_2).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-1").status(ConvertStatus.SUCCESS.toString()));
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-2").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        assertEquals(2, crsResult.getRecords().size());
        assertEquals(2, crsResult.getConversionStatuses().size());
        String converted = String.format(CONVERTED_RECORD_1, TO_CRS);
        assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(converted));
        String converted1 = String.format(CONVERTED_RECORD_2, TO_CRS);
        assertTrue(crsResult.getRecords().get(1).toString().equalsIgnoreCase(converted1));
    }

    @Test
    public void should_returnConvertedRecordsAndSuccessConversionStatus_whenValidRecordsProvided_PropertyLowerCase() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_18).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-1").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        assertEquals(1, crsResult.getRecords().size());
        assertEquals(1, crsResult.getConversionStatuses().size());
        String converted = String.format(CONVERTED_RECORD_6, TO_CRS);
        assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(converted));
    }

    @Test
    public void should_returnConvertedRecordsAndSuccessConversionStatus_whenRecordsHasXYFieldWithOtherNames() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_8).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-8").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        assertEquals(1, crsResult.getRecords().size());
        assertEquals(1, crsResult.getConversionStatuses().size());
        String converted = String.format(CONVERTED_RECORD_4, TO_CRS);
        assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(converted));
    }

    @Test
    public void should_returnOriginalRecordAndConversionStatus_whenInapproriateNestedPropertyNameProvided() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_9).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-9").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        assertEquals(1, crsResult.getRecords().size());
        assertEquals(1, crsResult.getConversionStatuses().size());
        String message = String.format(CrsConversionServiceErrorMessages.INVALID_NESTED_PROPERTY_NAME,"nestedProperty");
        assertTrue(crsResult.getConversionStatuses().get(0).getErrors().get(0).equalsIgnoreCase(message));
        assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(RECORD_9));
    }

    @Test
    public void should_returnOriginalRecordAndConversionStatus_whenMissingNestedPropertyInDataBlock() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_10).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-10").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        assertEquals(1, crsResult.getRecords().size());
        assertEquals(1, crsResult.getConversionStatuses().size());
        String message = String.format(CrsConversionServiceErrorMessages.MISSING_PROPERTY,"validNestedProperty");
        assertTrue(crsResult.getConversionStatuses().get(0).getErrors().get(0).equalsIgnoreCase(message));
        assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(RECORD_10));
    }

    @Test
    public void should_returnOriginalRecordAndConversionStatus_whenMetaIsProvidedButDataIsNullInDataBlock() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_14).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-14").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        assertEquals(1, crsResult.getRecords().size());
        assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(RECORD_14));
        String message = String.format(CrsConversionServiceErrorMessages.MISSING_PROPERTY,"X");
        List<String> errorMsg = crsResult.getConversionStatuses().get(0).getErrors();
        assertEquals(2, errorMsg.size());
        assertTrue(errorMsg.contains("CRS conversion: Unknown coordinate pair 'z'."));
        assertTrue(errorMsg.contains(message));
    }

    @Test
    public void should_returnOriginalRecordAndConversionStatus_whenMetaIsProvidedButDataIsIllegalInDataBlock() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_15).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-15").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        assertEquals(1, crsResult.getRecords().size());
        assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(RECORD_15));
        String message = String.format(CrsConversionServiceErrorMessages.ILLEGAL_PROPERTY_VALUE,"X", "For input string: \"yes\"");
        List<String> errorMsg = crsResult.getConversionStatuses().get(0).getErrors();
        assertEquals(2, errorMsg.size());
        assertTrue(errorMsg.contains("CRS conversion: Unknown coordinate pair 'z'."));
        assertTrue(errorMsg.contains(message));
    }

    @Test
    public void should_returnOriginalRecordAndConversionStatus_whenMetaIsProvidedButNoDataBlock() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_16).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-16").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        assertEquals(1, crsResult.getRecords().size());
        assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(RECORD_16));
        assertTrue(crsResult.getConversionStatuses().get(0).getErrors().get(0).equalsIgnoreCase(CrsConversionServiceErrorMessages.MISSING_DATA_BLOCK));
    }

    @Test
    public void should_returnOriginalRecordAndConversionStatus_whenMissingPointsListINNestedProperty() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_11).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-11").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        assertEquals(1, crsResult.getRecords().size());
        assertEquals(1, crsResult.getConversionStatuses().size());
        assertTrue(crsResult.getConversionStatuses().get(0).getErrors().get(0).equalsIgnoreCase(CrsConversionServiceErrorMessages.MISSING_POINTS_IN_NESTED_PROPERTY));
        assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(RECORD_11));
    }

    @Test
    public void should_returnConvertedRecordAndConversionStatus_whenNestedPropertyProvided() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_12).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-12").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        assertEquals(1, crsResult.getRecords().size());
        assertEquals(1, crsResult.getConversionStatuses().size());
        String converted = String.format(CONVERTED_RECORD_5, TO_CRS);
        assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(converted));
    }

    @Test
    public void should_returnOriginalRecordAndConversionStatus_whenNestedPropertyNotProvidedAsJsonObject() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_17).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-17").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        assertEquals(1, crsResult.getRecords().size());
        assertEquals(1, crsResult.getConversionStatuses().size());
        String message = String.format(CrsConversionServiceErrorMessages.ILLEGAL_DATA_IN_NESTED_PROPERTY, "validNestedProperty","Not a JSON Object: [[16.00,10.00],[16.00,10.00]]");
        assertTrue(crsResult.getConversionStatuses().get(0).getErrors().get(0).equalsIgnoreCase(message));
        assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(RECORD_17));
    }

    @Test
    public void should_returnConvertedRecordsAndSuccessConversionStatus_whenNestedDataIsNotProvided() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_20).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-20").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        assertEquals(1, crsResult.getRecords().size());
        assertEquals(1, crsResult.getConversionStatuses().size());
        String converted = String.format(CONVERTED_RECORD_8, TO_CRS);
        assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(converted));
    }

    @Test
    public void should_returnConvertedRecordsAndSuccessConversionStatus_whenPersistableReferenceIsJsonObject() {
        this.originalRecords.add(this.jsonParser.parse(RECORD_21).getAsJsonObject());
        this.conversionStatuses.add(ConversionStatus.builder().id("unit-test-21").status(ConvertStatus.SUCCESS.toString()));

        RecordsAndStatuses crsResult = this.sut.doCrsConversion(this.originalRecords, this.conversionStatuses);
        assertEquals(1, crsResult.getRecords().size());
        assertEquals(1, crsResult.getConversionStatuses().size());
        String converted = String.format(CONVERTED_RECORD_9, TO_CRS);
        assertTrue(crsResult.getRecords().get(0).toString().equalsIgnoreCase(converted));
    }

    @Test
    public void should_returnConvertedRecordsAndSuccessConversionStatus_whenGeoJsonDataIsProvided() throws CrsConverterException {
        ReflectionTestUtils.setField(sut, "conversionJsonUtils", conversionJsonUtils);
        this.originalRecords.add(this.jsonParser.parse(GEO_JSON_RECORD).getAsJsonObject());
        ConversionStatus.ConversionStatusBuilder conversionStatusBuilder = ConversionStatus.builder().id("geo-json-point-test").status(ConvertStatus.SUCCESS.toString());
        this.conversionStatuses.add(conversionStatusBuilder);

        JsonObject filteredResponse = this.jsonParser.parse(FILTERED_GEO_RESPONSE).getAsJsonObject();
        when(dpsConversionService.filterDataFields(any(), any())).thenReturn(filteredResponse);

        ConvertGeoJsonResponse convertGeoJsonResponse = mock(ConvertGeoJsonResponse.class);
        GeoJsonFeatureCollection geoJsonFeatureCollection = new GeoJsonFeatureCollection();
        when(crsConverterService.convertGeoJson(any())).thenReturn(convertGeoJsonResponse);
        when(convertGeoJsonResponse.getFeatureCollection()).thenReturn(geoJsonFeatureCollection);

        RecordsAndStatuses crsResult = this.sut.doCrsGeoJsonConversion(this.originalRecords, this.conversionStatuses);

        assertEquals(1, crsResult.getRecords().size());
        assertEquals(1, crsResult.getConversionStatuses().size());
        assertEquals("SUCCESS", crsResult.getConversionStatuses().get(0).getStatus());
    }

    @Test
    public void should_returnConvertedRecordsAndSuccessConversionStatus_whenAnyCrsGeometryCollectionHasAnyCrsNamedChild() throws CrsConverterException {
        ReflectionTestUtils.setField(sut, "conversionJsonUtils", conversionJsonUtils);
        this.originalRecords.add(this.jsonParser.parse(GEO_JSON_RECORD).getAsJsonObject());
        ConversionStatus.ConversionStatusBuilder conversionStatusBuilder = ConversionStatus.builder().id("geo-json-point-test").status(ConvertStatus.SUCCESS.toString());
        this.conversionStatuses.add(conversionStatusBuilder);

        JsonObject filteredResponse = this.jsonParser.parse(ANY_CRS_CHILD_GEO_RESPONSE).getAsJsonObject();
        when(dpsConversionService.filterDataFields(any(), any())).thenReturn(filteredResponse);

        ConvertGeoJsonResponse convertGeoJsonResponse = mock(ConvertGeoJsonResponse.class);
        when(crsConverterService.convertGeoJson(any())).thenReturn(convertGeoJsonResponse);
        when(convertGeoJsonResponse.getFeatureCollection()).thenReturn(new GeoJsonFeatureCollection());

        RecordsAndStatuses crsResult = this.sut.doCrsGeoJsonConversion(this.originalRecords, this.conversionStatuses);

        assertEquals("SUCCESS", crsResult.getConversionStatuses().get(0).getStatus());
    }

    @ParameterizedTest(name = "{0} is accepted as a collection member")
    @MethodSource("collectionMemberDiscriminators")
    public void should_acceptCollectionMember_forEveryGeometryDiscriminatorInBothSpellings(
            String ingestedType, String baseType, Class<?> expectedClass) throws CrsConverterException {
        ConversionStatus.ConversionStatusBuilder statusBuilder =
                this.arrangeGeometry(geometryCollection(geometryMember(ingestedType, coordinatesFor(baseType))));

        assertTrue(statusBuilder.getErrors().isEmpty(), "unexpected errors: " + statusBuilder.getErrors());
        GeoJsonBase[] members = this.forwardedMembers();
        assertEquals(1, members.length);
        assertEquals(expectedClass, members[0].getClass());
        assertEquals(ingestedType, members[0].getType());
    }

    @Test
    public void should_forwardEveryMemberInOrder_whenCollectionMixesLegacyAndAnyCrsNames() throws CrsConverterException {
        ConversionStatus.ConversionStatusBuilder statusBuilder = this.arrangeGeometry(geometryCollection(
                geometryMember("Point", coordinatesFor("Point")),
                geometryMember("AnyCrsMultiPoint", coordinatesFor("MultiPoint")),
                geometryMember("LineString", coordinatesFor("LineString"))));

        assertTrue(statusBuilder.getErrors().isEmpty());
        GeoJsonBase[] members = this.forwardedMembers();
        assertEquals(3, members.length);
        assertEquals("Point", members[0].getType());
        assertEquals("AnyCrsMultiPoint", members[1].getType());
        assertEquals("LineString", members[2].getType());
        assertArrayEquals(new double[][]{{10.0, 60.0}, {11.0, 61.0}},
                ((GeoJsonMultiPoint) members[1]).getCoordinates());
    }

    @ParameterizedTest(name = "invalid collection member: {0}")
    @MethodSource("invalidCollectionMembers")
    public void should_recordErrorAndSkipConversion_whenCollectionMemberDiscriminatorIsInvalid(
            String description, String memberJson, String reportedType) throws CrsConverterException {
        ConversionStatus.ConversionStatusBuilder statusBuilder =
                this.arrangeGeometry(geometryCollection(memberJson));

        assertTrue(statusBuilder.getErrors().contains(
                String.format(CrsConversionServiceErrorMessages.INVALID_GEOMETRIES, reportedType)), description);
        verify(this.crsConverterService, never()).convertGeoJson(any());
    }

    @Test
    public void should_convertLaterAttributes_whenAnEarlierAttributeRecordsAConversionError() throws CrsConverterException {
        ReflectionTestUtils.setField(sut, "conversionJsonUtils", conversionJsonUtils);
        JsonObject testRecord = this.jsonParser.parse(String.format(TWO_ATTRIBUTE_AS_INGESTED_RECORD,
                asIngestedAttribute(geometryCollection(geometryMember("Circle", coordinatesFor("Point")))),
                asIngestedAttribute(geometryCollection(geometryMember("AnyCrsPoint", coordinatesFor("Point")))))).getAsJsonObject();
        this.originalRecords.add(testRecord);
        ConversionStatus.ConversionStatusBuilder statusBuilder = ConversionStatus.builder().id("geo-json-point-test").status(ConvertStatus.SUCCESS.toString());
        this.conversionStatuses.add(statusBuilder);
        when(dpsConversionService.filterDataFields(any(), any())).thenReturn(testRecord.getAsJsonObject("data"));

        ConvertGeoJsonResponse okResponse = mock(ConvertGeoJsonResponse.class);
        when(okResponse.getFeatureCollection()).thenReturn(new GeoJsonFeatureCollection());
        when(crsConverterService.convertGeoJson(any())).thenReturn(okResponse);

        this.sut.doCrsGeoJsonConversion(this.originalRecords, this.conversionStatuses);

        assertTrue(statusBuilder.getErrors().contains(String.format(CrsConversionServiceErrorMessages.INVALID_GEOMETRIES, "Circle")));
        assertEquals("AnyCrsPoint", this.forwardedMembers()[0].getType());
    }

    @Test
    public void should_storeOnlyStandardTypeNames_whenTheConverterResponseIsWrittenIntoWgs84Coordinates() throws CrsConverterException {
        ReflectionTestUtils.setField(sut, "conversionJsonUtils", conversionJsonUtils);
        JsonObject testRecord = this.jsonParser.parse(String.format(AS_INGESTED_RECORD, geometryCollection(
                geometryMember("AnyCrsPoint", coordinatesFor("Point")),
                geometryMember("AnyCrsMultiPoint", coordinatesFor("MultiPoint"))))).getAsJsonObject();
        this.originalRecords.add(testRecord);
        this.conversionStatuses.add(ConversionStatus.builder().id("geo-json-point-test").status(ConvertStatus.SUCCESS.toString()));
        when(dpsConversionService.filterDataFields(any(), any())).thenReturn(testRecord.getAsJsonObject("data"));

        ConvertGeoJsonResponse okResponse = mock(ConvertGeoJsonResponse.class);
        when(okResponse.getFeatureCollection()).thenReturn(normalizedCollectionResponse());
        when(crsConverterService.convertGeoJson(any())).thenReturn(okResponse);

        this.sut.doCrsGeoJsonConversion(this.originalRecords, this.conversionStatuses);

        JsonObject wgs84 = testRecord.getAsJsonObject("data").getAsJsonObject("SpatialLocation").getAsJsonObject("Wgs84Coordinates");
        assertFalse(wgs84.toString().contains("AnyCrs"), wgs84.toString());
        assertEquals("FeatureCollection", wgs84.get("type").getAsString());
        JsonObject geometry = wgs84.getAsJsonArray("features").get(0).getAsJsonObject().getAsJsonObject("geometry");
        assertEquals("GeometryCollection", geometry.get("type").getAsString());
        JsonArray members = geometry.getAsJsonArray("geometries");
        assertEquals("Point", members.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("MultiPoint", members.get(1).getAsJsonObject().get("type").getAsString());
    }

    @Test
    public void should_recordErrorAndSkipConversion_whenFeatureGeometryUsesAnUnprefixedName() throws CrsConverterException {
        ConversionStatus.ConversionStatusBuilder statusBuilder =
                this.arrangeGeometry(geometryMember("Point", coordinatesFor("Point")));

        assertTrue(statusBuilder.getErrors().contains(String.format(CrsConversionServiceErrorMessages.INVALID_GEOMETRY, "Point")));
        verify(this.crsConverterService, never()).convertGeoJson(any());
    }

    @Test
    public void should_logTheRecordedError_whenCrsConverterRejectsTheGeoJsonRequest() throws CrsConverterException {
        ConversionStatus.ConversionStatusBuilder statusBuilder =
                this.arrangeConverterFailure(HttpStatus.SC_BAD_REQUEST, "invalid persistable reference");

        assertEquals(1, statusBuilder.getErrors().size());
        String message = statusBuilder.getErrors().get(0);
        assertTrue(message.contains("invalid persistable reference"), message);
        verify(this.logger).error(startsWith(message));
    }

    @Test
    public void should_logTheRecordedError_whenCrsConverterTimesOutOnTheGeoJsonRequest() throws CrsConverterException {
        ConversionStatus.ConversionStatusBuilder statusBuilder =
                this.arrangeConverterFailure(HttpStatus.SC_GATEWAY_TIMEOUT, "upstream timed out");

        assertEquals(1, statusBuilder.getErrors().size());
        String message = statusBuilder.getErrors().get(0);
        assertTrue(message.contains("upstream timed out"), message);
        verify(this.logger).error(startsWith(message));
    }

    @Test
    public void should_logRecordIdAndAffectedProperty_whenCrsConverterRejectsTheGeoJsonRequest() throws CrsConverterException {
        this.arrangeConverterFailure(HttpStatus.SC_BAD_REQUEST, "invalid persistable reference");

        ArgumentCaptor<String> logged = ArgumentCaptor.forClass(String.class);
        verify(this.logger).error(logged.capture());
        assertTrue(logged.getValue().contains("geo-json-point-test"), logged.getValue());
        assertTrue(logged.getValue().contains("SpatialLocation"), logged.getValue());
    }

    @Test
    public void should_logTheResponseCodeWithTheRecordedError_whenCrsConverterFailsWithAnUnhandledStatus() throws CrsConverterException {
        ConversionStatus.ConversionStatusBuilder statusBuilder =
                this.arrangeConverterFailure(HttpStatus.SC_SERVICE_UNAVAILABLE, "converter unavailable");

        assertEquals(1, statusBuilder.getErrors().size());
        String message = statusBuilder.getErrors().get(0);
        assertTrue(message.contains("converter unavailable"), message);
        assertTrue(message.contains("SpatialLocation"), message);
        assertTrue(message.contains(String.valueOf(HttpStatus.SC_SERVICE_UNAVAILABLE)), message);
        verify(this.logger).error(startsWith(message));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("crsConverterTransportFailures")
    public void should_stillCallTheCrsConverterForLaterAttributes_whenAnEarlierCallFailsWithATransportFailure(
            String description, Exception failure) throws CrsConverterException {
        JsonObject testRecord = this.arrangeTwoValidAttributeRecord();
        ConvertGeoJsonResponse okResponse = mock(ConvertGeoJsonResponse.class);
        when(okResponse.getFeatureCollection()).thenReturn(new GeoJsonFeatureCollection());
        when(crsConverterService.convertGeoJson(any())).thenThrow(failure).thenReturn(okResponse);

        this.sut.doCrsGeoJsonConversion(this.originalRecords, this.conversionStatuses);

        verify(this.crsConverterService, times(2)).convertGeoJson(any());
        List<String> errors = this.conversionStatuses.get(0).getErrors();
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains("SpatialArea"), errors.get(0));
        assertTrue(testRecord.getAsJsonObject("data").getAsJsonObject("LastLocation").has("Wgs84Coordinates"));
    }

    @Test
    public void should_convertLaterAttributesOfTheRecord_whenTheCrsConverterRejectsAnEarlierAttributeAsABadRequest() throws CrsConverterException {
        JsonObject testRecord = this.arrangeTwoValidAttributeRecord();
        HttpResponse badRequest = new HttpResponse();
        badRequest.setResponseCode(HttpStatus.SC_BAD_REQUEST);
        badRequest.setBody("invalid persistable reference");
        ConvertGeoJsonResponse okResponse = mock(ConvertGeoJsonResponse.class);
        when(okResponse.getFeatureCollection()).thenReturn(new GeoJsonFeatureCollection());
        when(crsConverterService.convertGeoJson(any()))
                .thenThrow(new CrsConverterException("crs failure", badRequest))
                .thenReturn(okResponse);

        this.sut.doCrsGeoJsonConversion(this.originalRecords, this.conversionStatuses);

        verify(this.crsConverterService, times(2)).convertGeoJson(any());
        List<String> errors = this.conversionStatuses.get(0).getErrors();
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains("SpatialArea"), errors.get(0));
        assertTrue(testRecord.getAsJsonObject("data").getAsJsonObject("LastLocation").has("Wgs84Coordinates"));
    }

    @Test
    public void should_keepCallingTheCrsConverterForOtherRecords_whenOneRecordHitsATransportFailure() throws CrsConverterException {
        ReflectionTestUtils.setField(sut, "conversionJsonUtils", conversionJsonUtils);
        String geometry = geometryCollection(geometryMember("AnyCrsPoint", coordinatesFor("Point")));
        JsonObject failingRecord = this.jsonParser.parse(String.format(AS_INGESTED_RECORD, geometry)).getAsJsonObject();
        JsonObject laterRecord = this.jsonParser.parse(String.format(AS_INGESTED_RECORD, geometry)).getAsJsonObject();
        laterRecord.addProperty("id", "geo-json-later-record");
        this.originalRecords.add(failingRecord);
        this.originalRecords.add(laterRecord);
        ConversionStatus.ConversionStatusBuilder failingStatus = ConversionStatus.builder().id("geo-json-point-test").status(ConvertStatus.SUCCESS.toString());
        ConversionStatus.ConversionStatusBuilder laterStatus = ConversionStatus.builder().id("geo-json-later-record").status(ConvertStatus.SUCCESS.toString());
        this.conversionStatuses.add(failingStatus);
        this.conversionStatuses.add(laterStatus);
        when(dpsConversionService.filterDataFields(any(), any()))
                .thenAnswer(invocation -> invocation.<JsonObject>getArgument(0).getAsJsonObject("data"));
        ConvertGeoJsonResponse okResponse = mock(ConvertGeoJsonResponse.class);
        when(okResponse.getFeatureCollection()).thenReturn(new GeoJsonFeatureCollection());
        when(crsConverterService.convertGeoJson(any()))
                .thenThrow(new AppException(509, "Socket time out", "Request cannot be completed in specified time"))
                .thenReturn(okResponse);

        this.sut.doCrsGeoJsonConversion(this.originalRecords, this.conversionStatuses);

        verify(this.crsConverterService, times(2)).convertGeoJson(any());
        assertEquals(ConvertStatus.ERROR.toString(), failingStatus.getStatus());
        assertEquals(ConvertStatus.SUCCESS.toString(), laterStatus.getStatus());
        assertTrue(laterRecord.getAsJsonObject("data").getAsJsonObject("SpatialLocation").has("Wgs84Coordinates"));
    }

    @Test
    public void should_logTheRecordIdAndCause_whenTheCrsConverterCallTimesOut() throws CrsConverterException {
        this.arrangeTwoValidAttributeRecord();
        AppException timeout = new AppException(509, "Socket time out", "Request cannot be completed in specified time");
        when(crsConverterService.convertGeoJson(any())).thenThrow(timeout);

        this.sut.doCrsGeoJsonConversion(this.originalRecords, this.conversionStatuses);

        List<String> errors = this.conversionStatuses.get(0).getErrors();
        assertEquals(2, errors.size(), errors.toString());
        ArgumentCaptor<String> logged = ArgumentCaptor.forClass(String.class);
        verify(this.logger, times(2)).error(logged.capture(), eq(timeout));
        for (int i = 0; i < errors.size(); i++) {
            assertTrue(errors.get(i).contains("Request cannot be completed in specified time"), errors.get(i));
            assertTrue(logged.getAllValues().get(i).startsWith(errors.get(i)), logged.getAllValues().get(i));
            assertTrue(logged.getAllValues().get(i).contains("geo-json-point-test"), logged.getAllValues().get(i));
        }
    }

    @Test
    public void should_recordAnErrorAndLogTheCause_whenTheConverterResponseCannotBeSerialized() throws CrsConverterException {
        ReflectionTestUtils.setField(sut, "conversionJsonUtils", conversionJsonUtils);
        JsonObject testRecord = this.jsonParser.parse(String.format(AS_INGESTED_RECORD,
                geometryCollection(geometryMember("AnyCrsPoint", coordinatesFor("Point"))))).getAsJsonObject();
        this.originalRecords.add(testRecord);
        ConversionStatus.ConversionStatusBuilder statusBuilder = ConversionStatus.builder().id("geo-json-point-test").status(ConvertStatus.SUCCESS.toString());
        this.conversionStatuses.add(statusBuilder);
        when(dpsConversionService.filterDataFields(any(), any())).thenReturn(testRecord.getAsJsonObject("data"));

        GeoJsonFeatureCollection unserializable = new GeoJsonFeatureCollection();
        unserializable.setFeatures(new GeoJsonFeature[]{new GeoJsonFeature()});
        ConvertGeoJsonResponse response = mock(ConvertGeoJsonResponse.class);
        when(response.getFeatureCollection()).thenReturn(unserializable);
        when(crsConverterService.convertGeoJson(any())).thenReturn(response);

        this.sut.doCrsGeoJsonConversion(this.originalRecords, this.conversionStatuses);

        ArgumentCaptor<Exception> cause = ArgumentCaptor.forClass(Exception.class);
        verify(this.logger).error(anyString(), cause.capture());
        assertTrue(cause.getValue() instanceof JsonProcessingException, String.valueOf(cause.getValue()));
        assertFalse(testRecord.getAsJsonObject("data").getAsJsonObject("SpatialLocation").has("Wgs84Coordinates"));
        assertEquals(1, statusBuilder.getErrors().size(), statusBuilder.getErrors().toString());
        assertTrue(statusBuilder.getErrors().get(0).contains("SpatialLocation"), statusBuilder.getErrors().get(0));
    }

    @Test
    public void should_skipMetaBlockPointConversion_whenTheGeoJsonPassRecordedAnErrorForTheSameRecord() throws CrsConverterException {
        ConversionStatus.ConversionStatusBuilder shared = this.runBothPassesOverSharedStatus("Circle");

        assertEquals(ConvertStatus.ERROR.toString(), shared.getStatus());
        verify(this.crsConverterService, never()).convertPoints(any());
        assertTrue(shared.getErrors().stream().noneMatch(error -> error.contains("point conversion skipped")),
                shared.getErrors().toString());
        assertTrue(shared.getErrors().stream().anyMatch(error -> error.contains("Circle")), shared.getErrors().toString());
        ArgumentCaptor<String> warning = ArgumentCaptor.forClass(String.class);
        verify(this.logger).warning(warning.capture());
        assertTrue(warning.getValue().contains("point conversion skipped"), warning.getValue());
        assertTrue(warning.getValue().contains("combined-test"), warning.getValue());
    }

    @Test
    public void should_convertMetaBlockPoints_whenTheGeoJsonPassSucceedsForTheSameRecord() throws CrsConverterException {
        ConversionStatus.ConversionStatusBuilder shared = this.runBothPassesOverSharedStatus("AnyCrsPoint");

        assertEquals(ConvertStatus.SUCCESS.toString(), shared.getStatus());
        verify(this.crsConverterService).convertPoints(any());
    }

    @Test
    public void should_recordErrorAndSkipConversion_whenCollectionMemberIsItselfANestedGeometryCollection() throws CrsConverterException {
        ConversionStatus.ConversionStatusBuilder statusBuilder = this.arrangeGeometry(
                geometryCollection(geometryCollection(geometryMember("Point", coordinatesFor("Point")))));

        assertTrue(statusBuilder.getErrors().contains(
                String.format(CrsConversionServiceErrorMessages.INVALID_GEOMETRIES, "AnyCrsGeometryCollection")));
        verify(this.crsConverterService, never()).convertGeoJson(any());
    }

    @Test
    public void should_produceAnEmptyGeometryListWithoutError_whenEveryCollectionMemberIsInvalid() {
        ReflectionTestUtils.setField(sut, "conversionJsonUtils", conversionJsonUtils);
        JsonObject featureItem = this.jsonParser.parse(String.format(
                "{\"type\":\"AnyCrsFeature\",\"bbox\":null,\"properties\":{},\"geometry\":%s}",
                geometryCollection(
                        geometryMember("Circle", coordinatesFor("Point")),
                        geometryMember("Blob", coordinatesFor("Point"))))).getAsJsonObject();
        ConversionStatus.ConversionStatusBuilder statusBuilder =
                ConversionStatus.builder().id("all-invalid-test").status(ConvertStatus.SUCCESS.toString());

        // Exercises getFeature() directly: before the fix, a fixed-size array left a null slot here and NPE'd on setType().
        GeoJsonFeature feature = ReflectionTestUtils.invokeMethod(sut, "getFeature", featureItem, statusBuilder);

        assertTrue(statusBuilder.getErrors().contains(String.format(CrsConversionServiceErrorMessages.INVALID_GEOMETRIES, "Circle")));
        assertTrue(statusBuilder.getErrors().contains(String.format(CrsConversionServiceErrorMessages.INVALID_GEOMETRIES, "Blob")));
        GeoJsonGeometryCollection collection = (GeoJsonGeometryCollection) feature.getGeometry();
        assertEquals(0, collection.getGeometries().length, "no member should survive filtering, but the collection itself must not be null");
    }

    @Test
    public void should_forwardValidMembersBeforeAndAfterAnInvalidOne_whenCollectionMixesValidAndInvalidMembers() {
        ReflectionTestUtils.setField(sut, "conversionJsonUtils", conversionJsonUtils);
        JsonObject featureItem = this.jsonParser.parse(String.format(
                "{\"type\":\"AnyCrsFeature\",\"bbox\":null,\"properties\":{},\"geometry\":%s}",
                geometryCollection(
                        geometryMember("Point", coordinatesFor("Point")),
                        geometryMember("Circle", coordinatesFor("Point")),
                        geometryMember("MultiPoint", coordinatesFor("MultiPoint"))))).getAsJsonObject();
        ConversionStatus.ConversionStatusBuilder statusBuilder =
                ConversionStatus.builder().id("mixed-members-test").status(ConvertStatus.SUCCESS.toString());

        GeoJsonFeature feature = ReflectionTestUtils.invokeMethod(sut, "getFeature", featureItem, statusBuilder);

        assertTrue(statusBuilder.getErrors().contains(String.format(CrsConversionServiceErrorMessages.INVALID_GEOMETRIES, "Circle")));
        GeoJsonBase[] members = ((GeoJsonGeometryCollection) feature.getGeometry()).getGeometries();
        assertEquals(2, members.length, "the invalid member should be dropped, not counted");
        assertEquals("Point", members[0].getType());
        assertEquals("MultiPoint", members[1].getType());
    }

    @Test
    public void should_isolateConversionStatusesAcrossRecords_whenOneRecordFailsAndAnotherSucceeds() throws CrsConverterException {
        ReflectionTestUtils.setField(sut, "conversionJsonUtils", conversionJsonUtils);
        JsonObject failingRecord = this.jsonParser.parse(String.format(AS_INGESTED_RECORD,
                geometryCollection(geometryMember("Circle", coordinatesFor("Point"))))).getAsJsonObject();
        failingRecord.addProperty("id", "failing-record");
        JsonObject succeedingRecord = this.jsonParser.parse(String.format(AS_INGESTED_RECORD,
                geometryCollection(geometryMember("AnyCrsPoint", coordinatesFor("Point"))))).getAsJsonObject();
        succeedingRecord.addProperty("id", "succeeding-record");
        this.originalRecords.add(failingRecord);
        this.originalRecords.add(succeedingRecord);

        ConversionStatus.ConversionStatusBuilder failingStatus =
                ConversionStatus.builder().id("failing-record").status(ConvertStatus.SUCCESS.toString());
        ConversionStatus.ConversionStatusBuilder succeedingStatus =
                ConversionStatus.builder().id("succeeding-record").status(ConvertStatus.SUCCESS.toString());
        this.conversionStatuses.add(failingStatus);
        this.conversionStatuses.add(succeedingStatus);

        when(dpsConversionService.filterDataFields(any(), any()))
                .thenAnswer(invocation -> ((JsonObject) invocation.getArgument(0)).getAsJsonObject("data"));

        ConvertGeoJsonResponse okResponse = mock(ConvertGeoJsonResponse.class);
        lenient().when(okResponse.getFeatureCollection()).thenReturn(new GeoJsonFeatureCollection());
        lenient().when(crsConverterService.convertGeoJson(any())).thenReturn(okResponse);

        this.sut.doCrsGeoJsonConversion(this.originalRecords, this.conversionStatuses);

        assertEquals(ConvertStatus.ERROR.toString(), failingStatus.getStatus());
        assertTrue(failingStatus.getErrors().contains(String.format(CrsConversionServiceErrorMessages.INVALID_GEOMETRIES, "Circle")));
        assertEquals(ConvertStatus.SUCCESS.toString(), succeedingStatus.getStatus());
        assertTrue(succeedingStatus.getErrors().isEmpty());
    }

    @Test
    public void should_convertLaterAttributes_whenAnEarlierAttributeFailsToSerialize() throws CrsConverterException {
        ReflectionTestUtils.setField(sut, "conversionJsonUtils", conversionJsonUtils);
        JsonObject testRecord = this.jsonParser.parse(String.format(TWO_ATTRIBUTE_AS_INGESTED_RECORD,
                asIngestedAttribute(geometryMember("AnyCrsPoint", coordinatesFor("Point"))),
                asIngestedAttribute(geometryMember("AnyCrsMultiPoint", coordinatesFor("MultiPoint"))))).getAsJsonObject();
        this.originalRecords.add(testRecord);
        ConversionStatus.ConversionStatusBuilder statusBuilder = ConversionStatus.builder().id("geo-json-point-test").status(ConvertStatus.SUCCESS.toString());
        this.conversionStatuses.add(statusBuilder);
        when(dpsConversionService.filterDataFields(any(), any())).thenReturn(testRecord.getAsJsonObject("data"));

        // GeoJsonFeature.properties defaults to a bare Object, which Jackson cannot serialize; the second attribute gets a normal response.
        GeoJsonFeatureCollection unserializable = new GeoJsonFeatureCollection();
        unserializable.setFeatures(new GeoJsonFeature[]{new GeoJsonFeature()});
        ConvertGeoJsonResponse failingResponse = mock(ConvertGeoJsonResponse.class);
        when(failingResponse.getFeatureCollection()).thenReturn(unserializable);
        ConvertGeoJsonResponse okResponse = mock(ConvertGeoJsonResponse.class);
        when(okResponse.getFeatureCollection()).thenReturn(new GeoJsonFeatureCollection());
        when(crsConverterService.convertGeoJson(any())).thenReturn(failingResponse, okResponse);

        this.sut.doCrsGeoJsonConversion(this.originalRecords, this.conversionStatuses);

        JsonObject data = testRecord.getAsJsonObject("data");
        assertFalse(data.getAsJsonObject("SpatialArea").has("Wgs84Coordinates"), "SpatialArea should have failed to serialize");
        assertTrue(data.getAsJsonObject("LastLocation").has("Wgs84Coordinates"), "LastLocation should have converted despite SpatialArea's failure");
        assertEquals(1, statusBuilder.getErrors().size(), statusBuilder.getErrors().toString());
        assertTrue(statusBuilder.getErrors().get(0).contains("SpatialArea"), statusBuilder.getErrors().get(0));
    }

    @Test
    public void should_logASkipWarningPerPairWithoutAddingToTheStatus_whenMultiplePointPairsAreSkippedForTheSameRecord() throws CrsConverterException {
        ReflectionTestUtils.setField(sut, "conversionJsonUtils", conversionJsonUtils);
        JsonObject testRecord = this.jsonParser.parse(String.format(TWO_PAIR_GEO_AND_META_RECORD,
                asIngestedAttribute(geometryCollection(geometryMember("Circle", coordinatesFor("Point")))))).getAsJsonObject();

        JsonObject filtered = new JsonObject();
        filtered.add("SpatialLocation", testRecord.getAsJsonObject("data").getAsJsonObject("SpatialLocation"));
        when(dpsConversionService.filterDataFields(any(), any())).thenReturn(filtered);

        ConvertGeoJsonResponse okResponse = mock(ConvertGeoJsonResponse.class);
        lenient().when(okResponse.getFeatureCollection()).thenReturn(new GeoJsonFeatureCollection());
        lenient().when(crsConverterService.convertGeoJson(any())).thenReturn(okResponse);

        ConversionStatus.ConversionStatusBuilder shared = ConversionStatus.builder().id("combined-test").status(ConvertStatus.SUCCESS.toString());
        this.conversionStatuses.add(shared);

        List<JsonObject> records = new ArrayList<>();
        records.add(testRecord);
        this.sut.doCrsGeoJsonConversion(records, this.conversionStatuses);
        this.sut.doCrsConversion(records, this.conversionStatuses);

        verify(this.crsConverterService, never()).convertPoints(any());
        assertTrue(shared.getErrors().stream().noneMatch(error -> error.contains("point conversion skipped")),
                shared.getErrors().toString());
        ArgumentCaptor<String> warnings = ArgumentCaptor.forClass(String.class);
        verify(this.logger, times(2)).warning(warnings.capture());
        assertTrue(warnings.getAllValues().stream().allMatch(w -> w.contains("point conversion skipped") && w.contains("combined-test")),
                warnings.getAllValues().toString());
        assertTrue(warnings.getAllValues().stream().anyMatch(w -> w.contains("X, Y")), warnings.getAllValues().toString());
        assertTrue(warnings.getAllValues().stream().anyMatch(w -> w.contains("LON, LAT")), warnings.getAllValues().toString());
    }

    private static Stream<Arguments> collectionMemberDiscriminators() {
        Map<String, Class<?>> geometryClasses = new LinkedHashMap<>();
        geometryClasses.put("Point", GeoJsonPoint.class);
        geometryClasses.put("MultiPoint", GeoJsonMultiPoint.class);
        geometryClasses.put("LineString", GeoJsonLineString.class);
        geometryClasses.put("MultiLineString", GeoJsonMultiLineString.class);
        geometryClasses.put("Polygon", GeoJsonPolygon.class);
        geometryClasses.put("MultiPolygon", GeoJsonMultiPolygon.class);
        return geometryClasses.entrySet().stream().flatMap(entry -> Stream.of(
                Arguments.of(entry.getKey(), entry.getKey(), entry.getValue()),
                Arguments.of("AnyCrs" + entry.getKey(), entry.getKey(), entry.getValue())));
    }

    private static Stream<Arguments> invalidCollectionMembers() {
        return Stream.of(
                Arguments.of("missing type", "{\"bbox\":null,\"coordinates\":[10.0,60.0]}", ""),
                Arguments.of("null type", "{\"type\":null,\"bbox\":null,\"coordinates\":[10.0,60.0]}", ""),
                Arguments.of("unsupported type", geometryMember("Circle", coordinatesFor("Point")), "Circle"),
                Arguments.of("case-mismatched type", geometryMember("anycrspoint", coordinatesFor("Point")), "anycrspoint"),
                Arguments.of("member is not a JSON object", "\"AnyCrsPoint\"", "\"AnyCrsPoint\""));
    }

    private static String coordinatesFor(String baseType) {
        switch (baseType) {
            case "Point": return "[10.0,60.0]";
            case "MultiPoint":
            case "LineString": return "[[10.0,60.0],[11.0,61.0]]";
            case "MultiLineString": return "[[[10.0,60.0],[11.0,61.0]]]";
            case "Polygon": return "[[[10.0,60.0],[11.0,60.0],[11.0,61.0],[10.0,60.0]]]";
            case "MultiPolygon": return "[[[[10.0,60.0],[11.0,60.0],[11.0,61.0],[10.0,60.0]]]]";
            default: throw new IllegalStateException("No coordinates defined for " + baseType);
        }
    }

    private static String geometryMember(String type, String coordinates) {
        return String.format("{\"type\":\"%s\",\"bbox\":null,\"coordinates\":%s}", type, coordinates);
    }

    private static String geometryCollection(String... members) {
        return String.format("{\"type\":\"AnyCrsGeometryCollection\",\"bbox\":null,\"geometries\":[%s]}", String.join(",", members));
    }

    private static String asIngestedAttribute(String geometryJson) {
        return String.format("{\"AsIngestedCoordinates\":{\"features\":[{\"geometry\":%s,\"bbox\":null,\"properties\":{},\"type\":\"AnyCrsFeature\"}],\"bbox\":null,\"properties\":{},\"persistableReferenceCrs\":\"reference\",\"persistableReferenceUnitZ\":\"reference\",\"type\":\"AnyCrsFeatureCollection\"}}", geometryJson);
    }

    private static GeoJsonFeatureCollection normalizedCollectionResponse() {
        GeoJsonPoint point = new GeoJsonPoint();
        point.setType("Point");
        GeoJsonMultiPoint multiPoint = new GeoJsonMultiPoint();
        multiPoint.setType("MultiPoint");

        GeoJsonGeometryCollection collection = new GeoJsonGeometryCollection();
        collection.setType("GeometryCollection");
        collection.setGeometries(new GeoJsonBase[]{point, multiPoint});

        GeoJsonFeature feature = new GeoJsonFeature();
        feature.setType("Feature");
        feature.setProperties(new HashMap<String, Object>());
        feature.setGeometry(collection);

        GeoJsonFeatureCollection featureCollection = new GeoJsonFeatureCollection();
        featureCollection.setType("FeatureCollection");
        featureCollection.setProperties(new HashMap<String, Object>());
        featureCollection.setFeatures(new GeoJsonFeature[]{feature});
        return featureCollection;
    }

    private ConversionStatus.ConversionStatusBuilder runBothPassesOverSharedStatus(String memberType) throws CrsConverterException {
        ReflectionTestUtils.setField(sut, "conversionJsonUtils", conversionJsonUtils);
        JsonObject testRecord = this.jsonParser.parse(String.format(GEO_AND_META_RECORD,
                asIngestedAttribute(geometryCollection(geometryMember(memberType, coordinatesFor("Point")))))).getAsJsonObject();

        JsonObject filtered = new JsonObject();
        filtered.add("SpatialLocation", testRecord.getAsJsonObject("data").getAsJsonObject("SpatialLocation"));
        when(dpsConversionService.filterDataFields(any(), any())).thenReturn(filtered);

        ConvertGeoJsonResponse okResponse = mock(ConvertGeoJsonResponse.class);
        lenient().when(okResponse.getFeatureCollection()).thenReturn(new GeoJsonFeatureCollection());
        lenient().when(crsConverterService.convertGeoJson(any())).thenReturn(okResponse);

        ConversionStatus.ConversionStatusBuilder shared = ConversionStatus.builder().id("combined-test").status(ConvertStatus.SUCCESS.toString());
        this.conversionStatuses.add(shared);

        List<JsonObject> records = new ArrayList<>();
        records.add(testRecord);
        this.sut.doCrsGeoJsonConversion(records, this.conversionStatuses);
        this.sut.doCrsConversion(records, this.conversionStatuses);
        return shared;
    }

    private ConversionStatus.ConversionStatusBuilder arrangeGeometry(String geometryJson) throws CrsConverterException {
        ReflectionTestUtils.setField(sut, "conversionJsonUtils", conversionJsonUtils);
        JsonObject testRecord = this.jsonParser.parse(String.format(AS_INGESTED_RECORD, geometryJson)).getAsJsonObject();
        this.originalRecords.add(testRecord);
        ConversionStatus.ConversionStatusBuilder statusBuilder = ConversionStatus.builder().id("geo-json-point-test").status(ConvertStatus.SUCCESS.toString());
        this.conversionStatuses.add(statusBuilder);
        when(dpsConversionService.filterDataFields(any(), any())).thenReturn(testRecord.getAsJsonObject("data"));

        ConvertGeoJsonResponse okResponse = mock(ConvertGeoJsonResponse.class);
        lenient().when(okResponse.getFeatureCollection()).thenReturn(new GeoJsonFeatureCollection());
        lenient().when(crsConverterService.convertGeoJson(any())).thenReturn(okResponse);

        this.sut.doCrsGeoJsonConversion(this.originalRecords, this.conversionStatuses);
        return statusBuilder;
    }

    private GeoJsonBase[] forwardedMembers() throws CrsConverterException {
        ArgumentCaptor<ConvertGeoJsonRequest> request = ArgumentCaptor.forClass(ConvertGeoJsonRequest.class);
        verify(this.crsConverterService).convertGeoJson(request.capture());
        GeoJsonBase geometry = request.getValue().getFeatureCollection().getFeatures()[0].getGeometry();
        return ((GeoJsonGeometryCollection) geometry).getGeometries();
    }

    private ConversionStatus.ConversionStatusBuilder arrangeConverterFailure(int responseCode, String body) throws CrsConverterException {
        ReflectionTestUtils.setField(sut, "conversionJsonUtils", conversionJsonUtils);
        JsonObject testRecord = this.jsonParser.parse(String.format(AS_INGESTED_RECORD,
                geometryCollection(geometryMember("AnyCrsPoint", coordinatesFor("Point"))))).getAsJsonObject();
        this.originalRecords.add(testRecord);
        ConversionStatus.ConversionStatusBuilder statusBuilder = ConversionStatus.builder().id("geo-json-point-test").status(ConvertStatus.SUCCESS.toString());
        this.conversionStatuses.add(statusBuilder);
        when(dpsConversionService.filterDataFields(any(), any())).thenReturn(testRecord.getAsJsonObject("data"));

        HttpResponse response = new HttpResponse();
        response.setResponseCode(responseCode);
        response.setBody(body);
        when(crsConverterService.convertGeoJson(any())).thenThrow(new CrsConverterException("crs failure", response));

        this.sut.doCrsGeoJsonConversion(this.originalRecords, this.conversionStatuses);
        return statusBuilder;
    }

    private JsonObject arrangeTwoValidAttributeRecord() {
        ReflectionTestUtils.setField(sut, "conversionJsonUtils", conversionJsonUtils);
        String attribute = asIngestedAttribute(geometryCollection(geometryMember("AnyCrsPoint", coordinatesFor("Point"))));
        JsonObject testRecord = this.jsonParser.parse(String.format(TWO_ATTRIBUTE_AS_INGESTED_RECORD, attribute, attribute)).getAsJsonObject();
        this.originalRecords.add(testRecord);
        this.conversionStatuses.add(ConversionStatus.builder().id("geo-json-point-test").status(ConvertStatus.SUCCESS.toString()));
        when(dpsConversionService.filterDataFields(any(), any())).thenReturn(testRecord.getAsJsonObject("data"));
        return testRecord;
    }

    // Mirrors how CrsConverterService reports each failure: HTTP 5xx responses as CrsConverterException, transport errors as AppException.
    private static Stream<Arguments> crsConverterTransportFailures() {
        return Stream.of(
                Arguments.of("HTTP 500 from the converter", crsConverterFailure(HttpStatus.SC_INTERNAL_SERVER_ERROR)),
                Arguments.of("HTTP 503 from the converter", crsConverterFailure(HttpStatus.SC_SERVICE_UNAVAILABLE)),
                Arguments.of("HTTP 504 from a gateway", crsConverterFailure(HttpStatus.SC_GATEWAY_TIMEOUT)),
                Arguments.of("socket timeout", new AppException(509, "Socket time out", "Request cannot be completed in specified time")),
                Arguments.of("connection failure", new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Internal communication failure", "Internal communication failure")));
    }

    private static CrsConverterException crsConverterFailure(int responseCode) {
        HttpResponse response = new HttpResponse();
        response.setResponseCode(responseCode);
        response.setBody("converter failure");
        return new CrsConverterException("crs failure", response);
    }
}

