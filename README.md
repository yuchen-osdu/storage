## Documentation
Official documentation [https://osdu.pages.opengroup.org/platform/system/storage/](https://osdu.pages.opengroup.org/platform/system/storage/)

## Prerequisites

- [JDK 25](https://adoptium.net/)
- [Maven 3.9.16+](https://maven.apache.org/download.cgi)

Community / core-plus modules compile and run on Java 25. For core-plus local setup, see [storage-core-plus/README.md](./storage-core-plus/README.md).

## Running the Storage Service locally
The Storage Service is a Maven multi-module project with each cloud implemention placed in its submodule.

## Google Cloud Implementation

The Google Cloud implementation has been moved to [gc-osdu-services](https://community.opengroup.org/osdu/platform/deployment-and-operations/base-containers-gcp/gc-osdu-services).

## AWS

The AWS provider has been removed from this repository.

### Open API 3.0 - Swagger
- Swagger UI : https://host/context-path/swagger (will redirect to https://host/context-path/swagger-ui/index.html)
- api-docs (JSON) : https://host/context-path/api-docs
- api-docs (YAML) : https://host/context-path/api-docs.yaml

All the Swagger and OpenAPI related common properties are managed here [swagger.properties](./storage-core/src/main/resources/swagger.properties)

_Note: For Collaboration Filter exclusion, refer 'excluded paths' section in  [docs/tutorial/CollaborationContext.md](./docs/tutorial/CollaborationContext.md#excluded-paths-a-nameexcluded-pathsa)_


#### Server Url(full path vs relative path) configuration
- `api.server.fullUrl.enabled=true` It will generate full server url in the OpenAPI swagger
- `api.server.fullUrl.enabled=false` It will generate only the contextPath only
- default value is false
[Reference]:(https://springdoc.org/faq.html#_how_is_server_url_generated) 

### Other platforms

1. Navigate to the module of the cloud of interest, for example, ```storage-core-plus```. Configure ```application.properties``` and optionally ```logback-spring.xml```. Intead of changing these files in the source, you can also provide external files at run time. 

2. Navigate to the root of the storage project, build and run unit tests in command line:
    ```bash
    mvn clean package
    ```

3. Install Redis

    > Redis is currently not required when running BYOC

    You can follow the [tutorial to install Redis locally](https://koukia.ca/installing-redis-on-windows-using-docker-containers-7737d2ebc25e) or install [docker for windows](https://docs.docker.com/docker-for-windows/install/). Make sure you are running docker with linux containers as Redis does not have a Windows image.

    ```bash
    #Pull redis image on docker
    docker pull redis
    #Run redis on docker
    docker run --name some-redis -d redis
    ```

    Install windows Redis client by following the instructions [here](https://github.com/MicrosoftArchive/redis/releases). Use default port 6379.

4. Set environment variables:
    
The AWS provider has been removed from this repository.

5. Run storage service in command line:
    ```bash
    # Running BYOC (Bring Your Own Cloud): 
    java -jar storage-service-byoc\target\storage-byoc-0.0.1-SNAPSHOT-spring-boot.jar
    ```

6. Access the service:

    The port and path for the service endpoint can be configured in ```application.properties``` in the provider folder as following. If not specified, then  the web container (ex. Tomcat) default is used: 
    ```bash
    server.servlet.contextPath=/api/storage/v2/
    server.port=8080
    ```

7. Build and test in IntelliJ:
    1. Import the maven project from the root of this project. 
    2. Create a ```JAR Application``` in ```Run/Debug Configurations``` with the ```Path to JAR``` set to the target jar file. 
    3. To run unit tests, creat a ```JUnit``` configuration in ```Run/Debug Configurations```, specify, for example:

    ```text
    Test kind: All in a package
    Search for tests: In whole project
    ```
   
## Cloud Deployment
This section describes the deployment process for each cloud provider.

### AWS

The AWS provider has been removed from this repository.


## Running integration tests
Integration tests are located in a separate project for each cloud in the ```testing``` directory under the project root directory. 

### Google Cloud

The Google Cloud implementation has been moved to [gc-osdu-services](https://community.opengroup.org/osdu/platform/deployment-and-operations/base-containers-gcp/gc-osdu-services).

### AWS

The AWS provider has been removed from this repository.

## License
Copyright 2017-2019, Schlumberger

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at 

[http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
