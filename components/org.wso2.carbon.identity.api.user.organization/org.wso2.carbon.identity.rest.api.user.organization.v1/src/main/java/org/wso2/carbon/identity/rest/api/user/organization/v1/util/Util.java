/*
 * Copyright (c) 2023, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.rest.api.user.organization.v1.util;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.identity.api.user.common.error.APIError;
import org.wso2.carbon.identity.api.user.common.error.ErrorResponse;
import org.wso2.carbon.identity.core.ServiceURLBuilder;
import org.wso2.carbon.identity.core.URLBuilderException;
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.organization.management.service.constant.OrganizationManagementConstants;
import org.wso2.carbon.identity.organization.management.service.exception.OrganizationManagementClientException;
import org.wso2.carbon.identity.organization.management.service.exception.OrganizationManagementException;
import org.wso2.carbon.identity.rest.api.user.organization.v1.model.Error;

import java.net.URI;

import javax.ws.rs.core.Response;

import static org.wso2.carbon.identity.api.user.common.Constants.ORGANIZATION_CONTEXT_PATH_COMPONENT;
import static org.wso2.carbon.identity.api.user.common.Constants.TENANT_CONTEXT_PATH_COMPONENT;
import static org.wso2.carbon.identity.api.user.common.Constants.USER_API_PATH_COMPONENT;
import static org.wso2.carbon.identity.application.common.util.IdentityApplicationConstants.Error.UNEXPECTED_SERVER_ERROR;
import static org.wso2.carbon.identity.organization.management.service.constant.OrganizationManagementConstants.ORGANIZATION_PATH;
import static org.wso2.carbon.identity.organization.management.service.constant.OrganizationManagementConstants.PATH_SEPARATOR;
import static org.wso2.carbon.identity.organization.management.service.constant.OrganizationManagementConstants.SERVER_API_PATH_COMPONENT;
import static org.wso2.carbon.identity.organization.management.service.constant.OrganizationManagementConstants.V1_API_PATH_COMPONENT;

/**
 * This class provides util functions to the user organization management endpoint.
 */
public class Util {

    private static final Log LOG = LogFactory.getLog(Util.class);

    /**
     * Returns a generic error object.
     *
     * @param errorMessages The error enum.
     * @param data          The error message data.
     * @return A generic error with the specified details.
     */
    public static Error getError(OrganizationManagementConstants.ErrorMessages errorMessages, String... data) {

        Error error = new Error();
        error.setCode(errorMessages.getCode());
        error.setMessage(errorMessages.getMessage());
        String description = errorMessages.getDescription();
        if (ArrayUtils.isNotEmpty(data)) {
            description = String.format(description, data);
        }
        error.setDescription(description);
        return error;
    }

    /**
     * Returns a generic error object.
     *
     * @param exception OrganizationManagementException.
     * @return A generic error with the specified details.
     */
    public static Error getError(OrganizationManagementException exception) {

        Error error = new Error();
        error.setCode(exception.getErrorCode());
        error.setMessage(exception.getMessage());
        error.setDescription(exception.getDescription());
        return error;
    }

    /**
     * Handle organization management exceptions.
     *
     * @param e The organization management exception.
     * @return APIError object.
     */
    public static APIError handleOrganizationManagementException(OrganizationManagementException e) {

        if (e instanceof OrganizationManagementClientException) {
            ErrorResponse errorResponse = getErrorBuilder(e).build(LOG, e.getDescription());
            return new APIError(Response.Status.BAD_REQUEST, errorResponse);
        }
        ErrorResponse errorResponse = getErrorBuilder(e).build(LOG, e, e.getDescription());
        return new APIError(Response.Status.INTERNAL_SERVER_ERROR, errorResponse);
    }

    /**
     * Handle errors.
     *
     * @param status The response status.
     * @param error  The error message enum.
     * @return APIError object.
     */
    public static APIError handleError(Response.Status status,
                                       OrganizationManagementConstants.ErrorMessages error) {

        ErrorResponse errorResponse = getErrorBuilder(error).build();
        return new APIError(status, errorResponse);
    }

    private static ErrorResponse.Builder getErrorBuilder(OrganizationManagementException e) {

        return new ErrorResponse.Builder().withCode(e.getErrorCode()).withMessage(e.getMessage())
                .withDescription(e.getDescription());
    }

    private static ErrorResponse.Builder getErrorBuilder(OrganizationManagementConstants.ErrorMessages errorEnum,
                                                         String... data) {

        return new ErrorResponse.Builder().withCode(errorEnum.getCode()).withMessage(errorEnum.getMessage())
                .withDescription(buildErrorDescription(errorEnum, data));
    }

    private static String buildErrorDescription(OrganizationManagementConstants.ErrorMessages errorEnum,
                                                String... data) {

        String errorDescription;
        if (ArrayUtils.isNotEmpty(data)) {
            errorDescription = String.format(errorEnum.getDescription(), data);
        } else {
            errorDescription = errorEnum.getDescription();
        }
        return errorDescription;
    }

    /**
     * The relative URL to get the organization.
     *
     * @param organizationId The unique identifier of the organization.
     * @return URI
     */
    public static URI organizationGetURL(String organizationId) {

        return buildURIForBody(V1_API_PATH_COMPONENT + PATH_SEPARATOR + ORGANIZATION_PATH +
                PATH_SEPARATOR + organizationId);
    }

    private static URI buildURIForBody(String endpoint) {

        String url;
        String context = getContext(endpoint);

        try {
            url = ServiceURLBuilder.create().addPath(context).build().getRelativePublicURL();
        } catch (URLBuilderException e) {
            String errorDescription = "Server encountered an error while building URL for response body.";
            throw buildInternalServerError(e, errorDescription);
        }
        return URI.create(url);
    }

    private static String getContext(String endpoint) {

        String context;
        String organizationId = PrivilegedCarbonContext.getThreadLocalCarbonContext().getOrganizationId();
        if (IdentityTenantUtil.isTenantQualifiedUrlsEnabled()) {
            context = SERVER_API_PATH_COMPONENT + endpoint;
            if (StringUtils.isNotEmpty(organizationId)) {
                String tenantDomain = (String) IdentityUtil.threadLocalProperties.get()
                        .get(OrganizationManagementConstants.ROOT_TENANT_DOMAIN);
                context = String.format(TENANT_CONTEXT_PATH_COMPONENT, tenantDomain) +
                        ORGANIZATION_CONTEXT_PATH_COMPONENT + context;
            }
        } else {
            context = String.format(TENANT_CONTEXT_PATH_COMPONENT, IdentityTenantUtil.resolveTenantDomain()) +
                    USER_API_PATH_COMPONENT + endpoint;
        }
        return context;
    }

    private static APIError buildInternalServerError(Exception e, String errorDescription) {

        String errorCode = UNEXPECTED_SERVER_ERROR.getCode();
        String errorMessage = "Error while building response.";

        ErrorResponse errorResponse = new ErrorResponse.Builder().
                withCode(errorCode)
                .withMessage(errorMessage)
                .withDescription(errorDescription)
                .build(LOG, e, errorDescription);

        Response.Status status = Response.Status.INTERNAL_SERVER_ERROR;
        return new APIError(status, errorResponse);
    }
}
