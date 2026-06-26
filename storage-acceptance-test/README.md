### Storage acceptance tests

End-to-end tests for the OSDU Storage service. Tests use **[os-core-test](https://github.com/opengroup/osdu/os-core-test) `0.1.6`** for HTTP clients, authentication, and shared acceptance-test infrastructure.

### Prerequisites

Export service URLs and credentials (see tables below), or place them in a `.env` file loaded by `os-core-test` (see the `os-core-test` README).

| name                  | value                                           | description              | required |
|-----------------------|-------------------------------------------------|--------------------------|----------|
| `STORAGE_URL`         | ex `http://localhost:8080/api/storage/v2/`      | Storage service URL      | yes      |
| `LEGAL_URL`           | ex `http://localhost:8080/api/legal/v1/`        | Legal service URL        | yes      |
| `ENTITLEMENTS_URL`    | ex `http://localhost:8080/api/entitlements/v2/` | Entitlements service URL | no       |
| `DATA_PARTITION_ID`   | ex `opendes`                                    | Data partition for tests | yes      |
| `ENTITLEMENTS_DOMAIN` | ex `contoso.com`                                | Entitlements domain      | yes      |

Authentication can be provided as OIDC config:

| name                                            | value                                      | description                                 | sensitive? |
|-------------------------------------------------|--------------------------------------------|---------------------------------------------|------------|
| `PRIVILEGED_USER_OPENID_PROVIDER_CLIENT_ID`     | `********`                                 | Privileged User Client Id                   | yes        |
| `PRIVILEGED_USER_OPENID_PROVIDER_CLIENT_SECRET` | `********`                                 | Privileged User Client secret               | yes        |
| `TEST_OPENID_PROVIDER_URL`                      | ex `https://keycloak.com/auth/realms/osdu` | OpenID provider url                         | yes        |
| `PRIVILEGED_USER_OPENID_PROVIDER_SCOPE`         | ex `api://my-app/.default`                 | OAuth2 scope (optional, defaults to openid) | no         |
| `NO_ACCESS_USER_OPENID_PROVIDER_CLIENT_ID`      | `********`                                 | No-access User Client Id (optional)         | yes        |
| `NO_ACCESS_USER_OPENID_PROVIDER_CLIENT_SECRET`  | `********`                                 | No-access User Client secret (optional)     | yes        |
| `ROOT_USER_OPENID_PROVIDER_CLIENT_ID`           | `********`                                 | Root User Client Id (optional)              | yes        |
| `ROOT_USER_OPENID_PROVIDER_CLIENT_SECRET`       | `********`                                 | Root User Client secret (optional)          | yes        |

Or tokens can be used directly:

| name                    | value      | description           | sensitive? |
|-------------------------|------------|-----------------------|------------|
| `PRIVILEGED_USER_TOKEN` | `********` | Privileged User Token | yes        |
| `NO_DATA_ACCESS_TOKEN`  | `********` | No-access User Token  | yes        |
| `DATA_ROOT_TOKEN`       | `********` | Data Root User Token  | yes        |

`NO_ACCESS_USER` and `ROOT_USER` credentials are only required for authorization and data-root tests (see below).

### Test configuration

Feature flags and timeouts are defined in `src/test/resources/test.properties`. The file is loaded **once per test suite run** via `BaseStorageAcceptanceTest.configUtils`.

Each property can be overridden by an environment variable: dots (`.`) become underscores (`_`), and the name is uppercased (for example `schema.endpoints.enabled` → `SCHEMA_ENDPOINTS_ENABLED`).

| Property (`test.properties`)     | Default | Environment override              | Description |
|----------------------------------|---------|---------------------------------|-------------|
| `schema.endpoints.enabled`       | `true`  | `SCHEMA_ENDPOINTS_ENABLED`      | Run schema API tests (`CreateSchemaIntegrationTest`, `StorageSchemaNegativeTest`, query-kinds tests that depend on schemas) |
| `collaboration.enabled`          | `true`  | `COLLABORATION_ENABLED`         | Run collaboration feature tests |
| `test.replay.enabled`            | `true`  | `TEST_REPLAY_ENABLED`           | Run replay API tests |
| `test.replayAll.enabled`         | `false` | `TEST_REPLAY_ALL_ENABLED`       | Run long-running replay-all scenarios |
| `test.replayAll.timeout`         | `60`    | `TEST_REPLAY_ALL_TIMEOUT`       | Seconds to wait for replay-all indexing |
| `enableEncodedSpecialCharactersInURL` | `true` | `ENABLE_ENCODED_SPECIAL_CHARACTERS_IN_URL` | Run `EncodedRecordIdQueryTest` |

Additional runtime flags (environment only, not in `test.properties`):

| name                         | value             | description |
|------------------------------|-------------------|-------------|
| `EXPOSE_FEATUREFLAG_ENABLED` | `true` OR `false` | Feature flag exposure in `/info`. Must match the storage service. Cannot be changed at test time. |
| `USER_ROLES_CHECK_ENABLED`   | `true` / `false`  | Enable entitlements role verification (default: driven by `required-roles.json`) |
| `USER_REQUIRED_ROLES_CONFIG` | path              | Override path to `required-roles.json` |

Set `schema.endpoints.enabled=false` (or `SCHEMA_ENDPOINTS_ENABLED=false`) when the target environment does not expose schema endpoints.

### Entitlements roles

Integration accounts must have the roles listed below. `PRIVILEGED_USER` and `NO_ACCESS_USER` are verified via `src/test/resources/required-roles.json` when `"enabled": true`. `ROOT_USER` is required for `DataRootAccessTest` but is not included in the role-check file.

| PRIVILEGED_USER            | NO_ACCESS_USER            | ROOT_USER                 |
|----------------------------|---------------------------|---------------------------|
| users                      | users                     | users                     |
| service.entitlements.user  | service.entitlements.user | users.data.root           |
| service.entitlements.admin | service.storage.admin     | service.entitlements.user |
| service.storage.admin      |                           | service.storage.viewer    |
| service.storage.creator    |                           | service.storage.admin     |
| service.storage.viewer     |                           | service.legal.viewer      |
| service.legal.admin        |                           |                           |
| service.legal.editor       |                           |                           |
| data.test1                 |                           |                           |
| data.integration.test      |                           |                           |

`NO_ACCESS_USER` is used by `RecordAccessAuthorizationTests`. `ROOT_USER` is used by `DataRootAccessTest`.

### Test framework notes

- **Base class:** `BaseStorageAcceptanceTest` extends `os-core-test` `BaseAcceptanceTests` and provides `StorageClient`, `LegalTagsClient`, and `EntitlementsClient`.
- **HTTP client:** `org.opengroup.osdu.core.test.client.StorageClient` — non-2xx responses throw `ClientException`.
- **Cleanup:** `StorageClient` tracks records created on successful `putRecords` and deletes them in `teardown()` (via `TidyTestClient`). `LegalTagsClient` cleans up legal tags the same way.
- **Legal tags:** Created with full `LegalTag` payloads (including `dataType` `Public Domain Data`) through `createLegalTag()`.

### Run tests

```bash
# Export variables above, or use a .env file in this directory / project root.
cd storage-acceptance-test && mvn clean test
```

Run a single test class:

```bash
mvn test -Dtest=RecordsApiAcceptanceTests
```

## License

Copyright © Google LLC

Copyright © EPAM Systems

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

[http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
