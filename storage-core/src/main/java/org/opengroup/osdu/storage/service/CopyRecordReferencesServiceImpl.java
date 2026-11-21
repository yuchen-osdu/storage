/*
 *    Copyright (c) 2024. EPAM Systems, Inc
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.opengroup.osdu.storage.service;

import static org.opengroup.osdu.core.common.model.collaboration.validation.CollaborationContextValidationDoc.DIRECTIVE_FORMAT;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.http.CollaborationContextFactory;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.storage.MultiRecordIds;
import org.opengroup.osdu.core.common.model.storage.MultiRecordInfo;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.storage.model.CopyRecordReferencesModel;
import org.opengroup.osdu.storage.model.RecordVersionModel;
import org.opengroup.osdu.storage.util.CollaborationContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
public class CopyRecordReferencesServiceImpl implements CopyRecordReferencesService {

  private static final String ID = "id";
  private static final String APPLICATION = "application";

  @Autowired
  private BatchService batchService;
  @Autowired
  private CollaborationContextFactory collaborationContextFactory;
  @Autowired
  private IngestionService ingestionService;

  @Override
  public CopyRecordReferencesModel copyRecordReferences(CopyRecordReferencesModel request,
      String collaborationDirectives) {
    if (CollectionUtils.isEmpty(request.getRecords())) {
      throw new AppException(HttpStatus.SC_BAD_REQUEST, "Validation error.",
          "The list of record IDs cannot be empty");
    }
    Map<String, String> collaborationHeader = parseCollaborationHeader(collaborationDirectives);
    if (!collaborationHeader.containsKey(ID) && !StringUtils.hasLength(
        request.getTarget())) {
      throw new AppException(HttpStatus.SC_CONFLICT, "Can't copy from SOR to SOR",
          "Source and target id is absent. You cant copy from System of Record to System of Record.");
    }
    Optional<CollaborationContext> collaborationContextHeader = getCollaborationContext(
        collaborationHeader, collaborationDirectives);
    Optional<CollaborationContext> collaborationContextBody = getCollaborationContext(request);
    if (!collaborationHeader.containsKey(APPLICATION)) {
      throw new AppException(HttpStatus.SC_BAD_REQUEST, "Validation error.",
          "Missing 'application' property in x-collaboration header.");
    }
    List<Record> records = getValidRecords(request, collaborationContextHeader,
        collaborationContextBody);
    ingestionService.createUpdateRecords(false, records, collaborationHeader.get(APPLICATION),
        collaborationContextBody);
    return request;
  }

  /**
   * Parses {@code x-collaboration} without requiring {@code @ValidateCollaborationContext}
   * (copy allows SOR {@code application=}-only headers). Malformed values such as {@code "0"}
   * must not reach {@link org.opengroup.osdu.core.common.util.CollaborationContextUtil}'s
   * unchecked {@code keyValue[1]} access (ArrayIndexOutOfBoundsException → 500).
   */
  private Map<String, String> parseCollaborationHeader(String collaborationDirectives) {
    if (!StringUtils.hasLength(collaborationDirectives)) {
      return Collections.emptyMap();
    }

    Map<String, String> properties = new HashMap<>();
    for (String directive : collaborationDirectives.split(",")) {
      int separator = directive.indexOf('=');
      if (separator <= 0 || separator == directive.length() - 1) {
        throw new AppException(HttpStatus.SC_BAD_REQUEST, "Validation error.", DIRECTIVE_FORMAT);
      }
      String key = directive.substring(0, separator).trim();
      String value = directive.substring(separator + 1).trim();
      if (!StringUtils.hasLength(key) || !StringUtils.hasLength(value)) {
        throw new AppException(HttpStatus.SC_BAD_REQUEST, "Validation error.", DIRECTIVE_FORMAT);
      }
      properties.put(key.toLowerCase(), value);
    }
    return properties;
  }

  private List<Record> getValidRecords(CopyRecordReferencesModel request,
      Optional<CollaborationContext> collaborationContextHeader,
      Optional<CollaborationContext> collaborationContextBody) {
    MultiRecordInfo sourceRecordInfo = batchService.getMultipleRecords(getRecordIds(request),
        collaborationContextHeader);
    if (!CollectionUtils.isEmpty(sourceRecordInfo.getInvalidRecords())) {
      throw new AppException(HttpStatus.SC_NOT_FOUND, "Records not found",
          "Source records not found: " + sourceRecordInfo.getInvalidRecords().toString());
    }
    MultiRecordInfo targetRecordInfo = batchService.getMultipleRecords(getRecordIds(request),
        collaborationContextBody);
    if (!CollectionUtils.isEmpty(targetRecordInfo.getRecords())) {
      throw new AppException(HttpStatus.SC_CONFLICT, "Records already exists",
          "One or more references already exist in the target namespace: "
              + sourceRecordInfo.getRecords().toString());
    }
    return sourceRecordInfo.getRecords();
  }

  private Optional<CollaborationContext> getCollaborationContext(
      CopyRecordReferencesModel recordReferences) {
    if (StringUtils.hasLength(recordReferences.getTarget())) {
      String collaborationDirectives = String.format("id=%s,application=pws",
          recordReferences.getTarget());
      return CollaborationContextHelper.create(collaborationContextFactory, collaborationDirectives);
    } else {
      return Optional.empty();
    }
  }

  private Optional<CollaborationContext> getCollaborationContext(
      Map<String, String> collaborationHeader, String collaborationDirectives) {
    if (collaborationHeader.containsKey(ID)) {
      return CollaborationContextHelper.create(collaborationContextFactory, collaborationDirectives);
    } else {
      return Optional.empty();
    }
  }

  private MultiRecordIds getRecordIds(CopyRecordReferencesModel recordReferences) {
    return new MultiRecordIds(
        recordReferences.getRecords().stream().map(RecordVersionModel::getId)
            .toList(), new String[]{});
  }
}
