/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.apimgt.impl.gatewayartifactsynchronizer;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.ExceptionCodes;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.dao.ChoreoGatewayArtifactsMgtDAO;
import org.wso2.carbon.apimgt.impl.dao.GatewayArtifactsMgtDAO;
import org.wso2.carbon.apimgt.impl.dto.APIRuntimeArtifactDto;
import org.wso2.carbon.apimgt.impl.dto.RuntimeArtifactDto;
import org.wso2.carbon.apimgt.impl.gatewayartifactsynchronizer.dto.ApiMetadataProjectDto;
import org.wso2.carbon.apimgt.impl.gatewayartifactsynchronizer.dto.MetadataDescriptorDto;
import org.wso2.carbon.apimgt.impl.gatewayartifactsynchronizer.dto.EnvironmentDto;
import org.wso2.carbon.apimgt.impl.importexport.APIImportExportException;
import org.wso2.carbon.apimgt.impl.importexport.ExportFormat;
import org.wso2.carbon.apimgt.impl.importexport.utils.CommonUtil;
import org.wso2.carbon.apimgt.impl.internal.ServiceReferenceHolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RuntimeArtifactGeneratorUtil {

    private static final GatewayArtifactsMgtDAO gatewayArtifactsMgtDAO = GatewayArtifactsMgtDAO.getInstance();
    private static final ChoreoGatewayArtifactsMgtDAO choreoGatewayArtifactsMgtDAO
            = ChoreoGatewayArtifactsMgtDAO.getInstance();
    private static final Log log = LogFactory.getLog(RuntimeArtifactGeneratorUtil.class);

    public static RuntimeArtifactDto generateRuntimeArtifact(String apiId, String name, String version,
                                                             String gatewayLabel, String type, String tenantDomain)
            throws APIManagementException {

        GatewayArtifactGenerator gatewayArtifactGenerator =
                ServiceReferenceHolder.getInstance().getGatewayArtifactGenerator(type);
        if (gatewayArtifactGenerator != null) {
            List<APIRuntimeArtifactDto> gatewayArtifacts = getRuntimeArtifacts(apiId, gatewayLabel, tenantDomain);
            return gatewayArtifactGenerator.generateGatewayArtifact(gatewayArtifacts);
        } else {
            Set<String> gatewayArtifactGeneratorTypes =
                    ServiceReferenceHolder.getInstance().getGatewayArtifactGeneratorTypes();
            throw new APIManagementException("Couldn't find gateway Type",
                    ExceptionCodes.from(ExceptionCodes.GATEWAY_TYPE_NOT_FOUND, String.join(",",
                            gatewayArtifactGeneratorTypes)));
        }
    }

    public static RuntimeArtifactDto generateAllRuntimeArtifact(String apiId, String name, String version,
                                                                String gatewayLabel, String type)
            throws APIManagementException {

        GatewayArtifactGenerator gatewayArtifactGenerator =
                ServiceReferenceHolder.getInstance().getGatewayArtifactGenerator(type);
        if (gatewayArtifactGenerator != null) {
            List<APIRuntimeArtifactDto> gatewayArtifacts = getAllRuntimeArtifacts(apiId, gatewayLabel);
            return gatewayArtifactGenerator.generateGatewayArtifact(gatewayArtifacts);
        } else {
            Set<String> gatewayArtifactGeneratorTypes =
                    ServiceReferenceHolder.getInstance().getGatewayArtifactGeneratorTypes();
            throw new APIManagementException("Couldn't find gateway Type",
                    ExceptionCodes.from(ExceptionCodes.GATEWAY_TYPE_NOT_FOUND, String.join(",",
                            gatewayArtifactGeneratorTypes)));
        }
    }

    public static RuntimeArtifactDto generateMetadataArtifact(String tenantDomain, String apiId, String gatewayLabel)
            throws APIManagementException {

        List<APIRuntimeArtifactDto> gatewayArtifacts = getRuntimeArtifacts(apiId, gatewayLabel, tenantDomain);
        return getRuntimeArtifactDto(gatewayArtifacts);
    }

    public static RuntimeArtifactDto generateAllMetadataArtifact(String apiId, String gatewayLabel)
            throws APIManagementException {

        List<APIRuntimeArtifactDto> gatewayArtifacts = getAllRuntimeArtifacts(apiId, gatewayLabel);
        return getRuntimeArtifactDto(gatewayArtifacts);
    }

    public static RuntimeArtifactDto generateAllRuntimeMetadata(String dataPlaneId, String gatewayAccessibilityType)
            throws APIManagementException {
        List<APIRuntimeArtifactDto> gatewayArtifacts = getAllRuntimeArtifactsByDataPlaneId(dataPlaneId,
                gatewayAccessibilityType);
        return getRuntimeArtifactDto(gatewayArtifacts);
    }

    private static RuntimeArtifactDto getRuntimeArtifactDto(List<APIRuntimeArtifactDto> gatewayArtifacts)
            throws APIManagementException {
        if (gatewayArtifacts != null) {

            try {
                MetadataDescriptorDto metadataDescriptorDto = new MetadataDescriptorDto();
                Map<String, ApiMetadataProjectDto> deploymentsMap = new HashMap<>();

                // "tempDirectory" is the root artifact directory
                File tempDirectory = CommonUtil.createTempDirectory(null);
                for (APIRuntimeArtifactDto apiRuntimeArtifactDto : gatewayArtifacts) {
                    if (apiRuntimeArtifactDto.isFile()) {
                        String fileName =
                                apiRuntimeArtifactDto.getApiId().concat("-")
                                        .concat(apiRuntimeArtifactDto.getRevision());
                        ApiMetadataProjectDto apiProjectDto = deploymentsMap.get(fileName);
                        if (apiProjectDto == null) {
                            apiProjectDto = new ApiMetadataProjectDto();
                            deploymentsMap.put(fileName, apiProjectDto);
                            apiProjectDto.setApiFile(fileName);
                            apiProjectDto.setEnvironments(new HashSet<>());
                            apiProjectDto.setOrganizationId(apiRuntimeArtifactDto.getOrganization());
                            apiProjectDto.setVersion(apiRuntimeArtifactDto.getVersion());
                            apiProjectDto.setApiContext(apiRuntimeArtifactDto.getContext());
                        }

                        EnvironmentDto environment = new EnvironmentDto();
                        environment.setId(apiRuntimeArtifactDto.getEnvUUID());
                        environment.setName(apiRuntimeArtifactDto.getLabel());
                        environment.setVhost(apiRuntimeArtifactDto.getVhost());
                        environment.setDeployedTimeStamp(apiRuntimeArtifactDto.getDeployedTimeStamp());
                        apiProjectDto.getEnvironments().add(environment);
                    }
                }
                metadataDescriptorDto.setMetadataDescriptor(new HashSet<>(deploymentsMap.values()));
                String descriptorFile = Paths.get(tempDirectory.getAbsolutePath(),
                        APIConstants.GatewayArtifactConstants.DEPLOYMENT_DESCRIPTOR_FILE).toString();
                CommonUtil.writeDtoToFile(descriptorFile, ExportFormat.JSON,
                        APIConstants.GatewayArtifactConstants.DEPLOYMENT_DESCRIPTOR_FILE_TYPE, metadataDescriptorDto);

                RuntimeArtifactDto runtimeArtifactDto = new RuntimeArtifactDto();
                runtimeArtifactDto.setArtifact(new File(descriptorFile.concat(APIConstants.JSON_FILE_EXTENSION)));
                runtimeArtifactDto.setFile(true);
                return runtimeArtifactDto;
            } catch (APIImportExportException | IOException e) {
                throw new APIManagementException("Error while Generating API artifact", e);
            }
        } else {
            throw new APIManagementException("No API Artifacts", ExceptionCodes.NO_API_ARTIFACT_FOUND);
        }
    }

    public static RuntimeArtifactDto generateAllRuntimeMetadata(String organization, String dataPlaneId,
                                                                String gatewayAccessibilityType)
            throws APIManagementException {
        List<APIRuntimeArtifactDto> gatewayArtifacts =
                getAllRuntimeArtifactsByDataPlaneId(organization, dataPlaneId, gatewayAccessibilityType);
        return getRuntimeArtifactDto(gatewayArtifacts);
    }

    public static RuntimeArtifactDto generateAllRuntimeArtifact(String dataPlaneId, String gatewayAccessibilityType,
                                                                String type)
            throws APIManagementException {

        GatewayArtifactGenerator gatewayArtifactGenerator =
                ServiceReferenceHolder.getInstance().getGatewayArtifactGenerator(type);
        if (gatewayArtifactGenerator != null) {
            List<APIRuntimeArtifactDto> gatewayArtifacts;
            if (gatewayAccessibilityType == null) {
                gatewayArtifacts = choreoGatewayArtifactsMgtDAO.retrieveAllGatewayArtifactsByDataPlaneId(dataPlaneId);
            } else {
                gatewayArtifacts = choreoGatewayArtifactsMgtDAO.retrieveAllGatewayArtifactsByDataPlaneId(dataPlaneId,
                        gatewayAccessibilityType);
            }
            if (gatewayArtifacts != null) {
                if (gatewayArtifacts.isEmpty()) {
                    throw new APIManagementException("No API Artifacts", ExceptionCodes.NO_API_ARTIFACT_FOUND);
                }
                Iterator<APIRuntimeArtifactDto> iterator = gatewayArtifacts.iterator();
                while (iterator.hasNext()) {
                    APIRuntimeArtifactDto apiRuntimeArtifactDto = iterator.next();
                    try {
                        ArtifactSynchronizerUtil.setArtifactProperties(apiRuntimeArtifactDto);
                    } catch (APIManagementException e) {
                        log.warn("Error while setting artifact properties in api: "
                                + apiRuntimeArtifactDto.getApiId() + ". The API will not be deployed.", e);
                        iterator.remove();
                    }
                }
            }
            if (gatewayArtifacts == null || gatewayArtifacts.isEmpty()) {
                return null;
            }
            return gatewayArtifactGenerator.generateGatewayArtifact(gatewayArtifacts);
        } else {
            Set<String> gatewayArtifactGeneratorTypes =
                    ServiceReferenceHolder.getInstance().getGatewayArtifactGeneratorTypes();
            throw new APIManagementException("Couldn't find gateway Type",
                    ExceptionCodes.from(ExceptionCodes.GATEWAY_TYPE_NOT_FOUND, String.join(",",
                            gatewayArtifactGeneratorTypes)));
        }
    }

    public static RuntimeArtifactDto generateAllRuntimeArtifact(String organization, String dataPlaneId,
                                                                String gatewayAccessibilityType, String type)
            throws APIManagementException {

        GatewayArtifactGenerator gatewayArtifactGenerator =
                ServiceReferenceHolder.getInstance().getGatewayArtifactGenerator(type);
        if (gatewayArtifactGenerator != null) {
            List<APIRuntimeArtifactDto> gatewayArtifacts;
            if (gatewayAccessibilityType == null) {
                gatewayArtifacts = choreoGatewayArtifactsMgtDAO
                        .retrieveAllGatewayArtifactsByOrganizationAndDataPlaneId(organization, dataPlaneId);
            } else {
                gatewayArtifacts = choreoGatewayArtifactsMgtDAO
                        .retrieveAllGatewayArtifactsByOrganizationAndDataPlaneId(organization, dataPlaneId,
                                gatewayAccessibilityType);
            }
            if (gatewayArtifacts != null) {
                if (gatewayArtifacts.isEmpty()) {
                    throw new APIManagementException("No API Artifacts", ExceptionCodes.NO_API_ARTIFACT_FOUND);
                }
                Iterator<APIRuntimeArtifactDto> iterator = gatewayArtifacts.iterator();
                while (iterator.hasNext()) {
                    APIRuntimeArtifactDto apiRuntimeArtifactDto = iterator.next();
                    try {
                        ArtifactSynchronizerUtil.setArtifactProperties(apiRuntimeArtifactDto);
                    } catch (APIManagementException e) {
                        log.warn("Error while setting artifact properties in api: "
                                + apiRuntimeArtifactDto.getApiId() + ". The API will not be deployed.", e);
                        iterator.remove();
                    }
                }
            }
            if (gatewayArtifacts == null || gatewayArtifacts.isEmpty()) {
                return null;
            }
            return gatewayArtifactGenerator.generateGatewayArtifact(gatewayArtifacts);
        } else {
            Set<String> gatewayArtifactGeneratorTypes =
                    ServiceReferenceHolder.getInstance().getGatewayArtifactGeneratorTypes();
            throw new APIManagementException("Couldn't find gateway Type",
                    ExceptionCodes.from(ExceptionCodes.GATEWAY_TYPE_NOT_FOUND, String.join(",",
                            gatewayArtifactGeneratorTypes)));
        }
    }

    private static List<APIRuntimeArtifactDto> getRuntimeArtifacts(String apiId, String gatewayLabel,
                                                                   String tenantDomain) throws APIManagementException {
        List<APIRuntimeArtifactDto> gatewayArtifacts;
        if (StringUtils.isNotEmpty(gatewayLabel)) {
            byte[] decodedValue = Base64.decodeBase64(gatewayLabel.getBytes());
            String[] gatewayLabels = new String(decodedValue).split("\\|");
            if (StringUtils.isNotEmpty(apiId)) {
                gatewayArtifacts = gatewayArtifactsMgtDAO
                        .retrieveGatewayArtifactsByAPIIDAndLabel(apiId, gatewayLabels, tenantDomain);
            } else {
                gatewayArtifacts =
                        gatewayArtifactsMgtDAO.retrieveGatewayArtifactsByLabel(gatewayLabels, tenantDomain);
            }
        } else {
            gatewayArtifacts = gatewayArtifactsMgtDAO.retrieveGatewayArtifacts(tenantDomain);
        }
        if (gatewayArtifacts != null) {
            if (gatewayArtifacts.isEmpty()) {
                throw new APIManagementException("No API Artifacts", ExceptionCodes.NO_API_ARTIFACT_FOUND);
            }
            for (APIRuntimeArtifactDto apiRuntimeArtifactDto: gatewayArtifacts) {
                ArtifactSynchronizerUtil.setArtifactProperties(apiRuntimeArtifactDto);
            }
        }
        if (gatewayArtifacts == null || gatewayArtifacts.isEmpty()) {
            return null;
        }
        return gatewayArtifacts;
    }

    private static List<APIRuntimeArtifactDto> getAllRuntimeArtifacts(String apiId, String gatewayLabel)
            throws APIManagementException {
        List<APIRuntimeArtifactDto> gatewayArtifacts;
        if (StringUtils.isNotEmpty(gatewayLabel)) {
            byte[] decodedValue = Base64.decodeBase64(gatewayLabel.getBytes());
            String[] gatewayLabels = new String(decodedValue).split("\\|");
            if (StringUtils.isNotEmpty(apiId)) {
                gatewayArtifacts = gatewayArtifactsMgtDAO
                        .retrieveAllGatewayArtifactsByAPIIDAndLabel(apiId, gatewayLabels);
            } else {
                gatewayArtifacts =
                        gatewayArtifactsMgtDAO.retrieveAllGatewayArtifactsByLabel(gatewayLabels);
            }
        } else {
            gatewayArtifacts = gatewayArtifactsMgtDAO.retrieveAllGatewayArtifacts();
        }
        if (gatewayArtifacts != null) {
            if (gatewayArtifacts.isEmpty()) {
                throw new APIManagementException("No API Artifacts", ExceptionCodes.NO_API_ARTIFACT_FOUND);
            }
            for (APIRuntimeArtifactDto apiRuntimeArtifactDto: gatewayArtifacts) {
                ArtifactSynchronizerUtil.setArtifactProperties(apiRuntimeArtifactDto);
            }
        }
        if (gatewayArtifacts == null || gatewayArtifacts.isEmpty()) {
            return null;
        }
        return gatewayArtifacts;
    }

    private static List<APIRuntimeArtifactDto> getAllRuntimeArtifactsByDataPlaneId(String dataPlaneId,
                                                                                   String gatewayAccessibilityType)
            throws APIManagementException {
        List<APIRuntimeArtifactDto> gatewayArtifacts;

        if (StringUtils.isNotEmpty(dataPlaneId)) {
            byte[] decodedValue = Base64.decodeBase64(dataPlaneId.getBytes());
            String[] dataPlaneIds = new String(decodedValue).split("\\|");
            if (gatewayAccessibilityType == null) {
                gatewayArtifacts = choreoGatewayArtifactsMgtDAO.retrieveAllGatewayArtifactsByDataPlaneId(dataPlaneIds);
            } else {
                gatewayArtifacts = choreoGatewayArtifactsMgtDAO.retrieveAllGatewayArtifactsByDataPlaneId(dataPlaneIds,
                        gatewayAccessibilityType);
            }
        } else {
            throw new APIManagementException("No Data Plane Id",
                    ExceptionCodes.CANNOT_RETRIEVE_RUNTIME_METADATA_NO_DATAPLANE_ID_ERROR);
        }
        if (gatewayArtifacts != null) {
            if (gatewayArtifacts.isEmpty()) {
                throw new APIManagementException("No API Artifacts", ExceptionCodes.NO_API_ARTIFACT_FOUND);
            }
            for (APIRuntimeArtifactDto apiRuntimeArtifactDto: gatewayArtifacts) {
                ArtifactSynchronizerUtil.setArtifactProperties(apiRuntimeArtifactDto);
            }
        }

        return gatewayArtifacts;
    }

    private static List<APIRuntimeArtifactDto> getAllRuntimeArtifactsByDataPlaneId(String organization,
                                                                                   String dataPlaneId,
                                                                                   String gatewayAccessibilityType)
            throws APIManagementException {
        List<APIRuntimeArtifactDto> gatewayArtifacts;

        if (StringUtils.isNotEmpty(dataPlaneId)) {
            byte[] decodedValue = Base64.decodeBase64(dataPlaneId.getBytes());
            String[] dataPlaneIds = new String(decodedValue).split("\\|");
            if (gatewayAccessibilityType == null) {
                gatewayArtifacts = choreoGatewayArtifactsMgtDAO
                        .retrieveAllGatewayArtifactsByDataPlaneId(organization, dataPlaneIds);
            } else {
                gatewayArtifacts = choreoGatewayArtifactsMgtDAO.retrieveAllGatewayArtifactsByDataPlaneId(organization,
                        dataPlaneIds, gatewayAccessibilityType);
            }

        } else {
            throw new APIManagementException("No Data Plane Id",
                    ExceptionCodes.CANNOT_RETRIEVE_RUNTIME_METADATA_NO_DATAPLANE_ID_ERROR);
        }
        if (gatewayArtifacts != null) {
            if (gatewayArtifacts.isEmpty()) {
                throw new APIManagementException("No API Artifacts", ExceptionCodes.NO_API_ARTIFACT_FOUND);
            }
            for (APIRuntimeArtifactDto apiRuntimeArtifactDto: gatewayArtifacts) {
                ArtifactSynchronizerUtil.setArtifactProperties(apiRuntimeArtifactDto);
            }
        }

        return gatewayArtifacts;
    }

}
