package org.wso2.carbon.apimgt.rest.api.admin.v1;


import org.apache.cxf.jaxrs.ext.MessageContext;

import org.wso2.carbon.apimgt.api.APIManagementException;


import javax.ws.rs.core.Response;


public interface TenantConfigSchemaApiService {
      public Response exportTenantConfigSchema(MessageContext messageContext) throws APIManagementException;
}
