package org.wso2.carbon.apimgt.impl.keymgt;

import com.google.gson.Gson;
import org.apache.commons.codec.binary.Base64;
import org.wso2.carbon.apimgt.api.dto.KeyManagerConfigurationDTO;
import org.wso2.carbon.apimgt.api.model.KeyManagerConfiguration;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.dto.EventHubConfigurationDto;
import org.wso2.carbon.apimgt.impl.internal.ServiceReferenceHolder;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.databridge.commons.Event;

import java.util.Collections;

public class KeyMgtNotificationSender {

    public void notify(KeyManagerConfigurationDTO keyManagerConfigurationDTO,String action) {
        String encodedString = "";
        if (keyManagerConfigurationDTO.getAdditionalProperties() != null){
            String additionalProperties = new Gson().toJson(keyManagerConfigurationDTO.getAdditionalProperties());
            encodedString = new String(Base64.encodeBase64(additionalProperties.getBytes()));
        }
        Object[] objects = new Object[]{APIConstants.KeyManager.KeyManagerEvent.KEY_MANAGER_CONFIGURATION, action,
                keyManagerConfigurationDTO.getName(), keyManagerConfigurationDTO.getType(),
                keyManagerConfigurationDTO.isEnabled(), encodedString,
                keyManagerConfigurationDTO.getOrganization(), keyManagerConfigurationDTO.getTokenType()};
            Event keyManagerEvent = new Event(APIConstants.KeyManager.KeyManagerEvent.KEY_MANAGER_STREAM_ID,
                System.currentTimeMillis(),
                null, null, objects);
        EventHubConfigurationDto eventHubConfigurationDto =
                ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().getAPIManagerConfiguration()
                        .getEventHubConfigurationDto();

        // if the Token Type is EXCHANGED, there is no need to send the key manager event to the gateway
        if (eventHubConfigurationDto.isEnabled()
                && !KeyManagerConfiguration.TokenType.EXCHANGED.toString().equals(
                        keyManagerConfigurationDTO.getTokenType())) {
            APIUtil.publishEventToTrafficManager(Collections.EMPTY_MAP, keyManagerEvent);
        }
    }
}
