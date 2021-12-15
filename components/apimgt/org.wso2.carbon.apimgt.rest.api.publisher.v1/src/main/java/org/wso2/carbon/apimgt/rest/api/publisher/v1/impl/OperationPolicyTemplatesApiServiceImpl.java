package org.wso2.carbon.apimgt.rest.api.publisher.v1.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.APIProvider;
import org.wso2.carbon.apimgt.api.model.APIInfo;
import org.wso2.carbon.apimgt.api.model.OperationPolicyDefinition;
import org.wso2.carbon.apimgt.api.model.OperationPolicySpecification;
import org.wso2.carbon.apimgt.impl.importexport.utils.CommonUtil;
import org.wso2.carbon.apimgt.rest.api.common.RestApiCommonUtil;
import org.wso2.carbon.apimgt.rest.api.common.RestApiConstants;
import org.wso2.carbon.apimgt.rest.api.publisher.v1.*;
import org.wso2.carbon.apimgt.rest.api.publisher.v1.dto.*;

import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.MessageContext;

import org.wso2.carbon.apimgt.rest.api.publisher.v1.dto.ErrorDTO;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import org.wso2.carbon.apimgt.rest.api.publisher.v1.dto.OperationPolicyDefinitionDTO;
import org.wso2.carbon.apimgt.rest.api.publisher.v1.dto.OperationPolicyDefinitionsListDTO;
import org.wso2.carbon.apimgt.rest.api.util.utils.RestApiUtil;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import java.io.InputStream;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;


public class OperationPolicyTemplatesApiServiceImpl implements OperationPolicyTemplatesApiService {

    private static final Log log = LogFactory.getLog(OperationPolicyTemplatesApiServiceImpl.class);

    public Response addOperationPolicyTemplate(InputStream templateSpecFileInputStream,
                                               Attachment templateSpecFileDetail,
                                               InputStream policyDefinitionFileInputStream,
                                               Attachment policyDefinitionFileDetail, String policyName, String flow,
                                               MessageContext messageContext) throws APIManagementException {
        try {
            APIProvider apiProvider = RestApiCommonUtil.getLoggedInUserProvider();
            String organization = RestApiUtil.getValidatedOrganization(messageContext);
            String policySpec = "";
            String jsonContent = "";
            String policyTemplate = "";
            OperationPolicySpecification policySpecification;
            if (templateSpecFileInputStream != null) {
                policySpec = readInputStream(templateSpecFileInputStream, templateSpecFileDetail);
                jsonContent = CommonUtil.yamlToJson(policySpec);
                policySpecification = new Gson().fromJson(jsonContent, OperationPolicySpecification.class);
            } else {
                // This flow will execute if a policy specification is not found.
                policySpecification = new OperationPolicySpecification();
                policySpecification.setPolicyName(policyName);
                List<String> policyFlow = new ArrayList<String>(Arrays.asList(flow.split(",")));
                policySpecification.setFlow(policyFlow);
            }

            if (policyDefinitionFileInputStream != null) {
                policyTemplate = readInputStream(policyDefinitionFileInputStream, policyDefinitionFileDetail);
            }

            OperationPolicyDefinition operationPolicyDefinition = new OperationPolicyDefinition();
            operationPolicyDefinition.setSpecification(policySpecification);
            operationPolicyDefinition.setDefinition(policyTemplate);
            operationPolicyDefinition.setName(policyName);
            operationPolicyDefinition.setFlow(flow);
            apiProvider.addOperationalPolicyTemplate(operationPolicyDefinition, organization);


            if (operationPolicyDefinition != null) {
                String uriString = RestApiConstants.RESOURCE_PATH_API_MEDIATION
                        .replace(RestApiConstants.APIID_PARAM, "111")  + "/" + "operational-policy";
                URI uri = new URI(uriString);
                OperationPolicyDefinitionDTO createdPolicy = new OperationPolicyDefinitionDTO();
                createdPolicy.setName(operationPolicyDefinition.getName());
                return Response.created(uri).entity(createdPolicy).build();
            }

        } catch (APIManagementException e) {
            //Auth failure occurs when cross tenant accessing APIs. Sends 404, since we don't need
            // to expose the existence of the resource
            if (RestApiUtil.isDueToResourceNotFound(e) || RestApiUtil.isDueToAuthorizationFailure(e)) {
                RestApiUtil.handleResourceNotFoundError(RestApiConstants.RESOURCE_API, e, log);
            } else {
                throw e;
            }
        } catch (Exception e) {
            RestApiUtil.handleInternalServerError("An Error has occurred while adding operational policy template", e, log);
        }
        return null;
    }

    public String readInputStream (InputStream fileInputStream, Attachment fileDetail) throws IOException {

        String content = null;
        if (fileInputStream != null) {
            String fileName = fileDetail.getDataHandler().getName();

            String fileContentType = URLConnection.guessContentTypeFromName(fileName);

            if (org.apache.commons.lang3.StringUtils.isBlank(fileContentType)) {
                fileContentType = fileDetail.getContentType().toString();
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            IOUtils.copy(fileInputStream, outputStream);
            byte[] sequenceBytes = outputStream.toByteArray();
            InputStream inSequenceStream = new ByteArrayInputStream(sequenceBytes);
            content = IOUtils.toString(inSequenceStream, StandardCharsets.UTF_8.name());
        }
        return content;
    }

    public Response getAllOperationPolicyTemplates(Integer limit, Integer offset, String query, MessageContext messageContext) {
        // remove errorObject and add implementation code!
        ErrorDTO errorObject = new ErrorDTO();
        Response.Status status = Response.Status.NOT_IMPLEMENTED;
        errorObject.setCode((long) status.getStatusCode());
        errorObject.setMessage(status.toString());
        errorObject.setDescription("The requested resource has not been implemented");
        return Response.status(status).entity(errorObject).build();
    }
}
