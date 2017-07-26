/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.webadmin.routes;

import static org.apache.james.webadmin.Constants.SEPARATOR;

import java.util.List;
import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.JsonTransformer;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import spark.Request;
import spark.Response;
import spark.Service;

@Api(tags = "Domains")
@Path(DomainRoutes.DOMAINS)
@Produces("application/json")
public class DomainRoutes implements Routes {

    private static final String DOMAIN_NAME = ":domainName";
    private static final Logger LOGGER = LoggerFactory.getLogger(DomainRoutes.class);

    public static final String DOMAINS = "/domains";
    public static final String SPECIFIC_DOMAIN = DOMAINS + SEPARATOR + DOMAIN_NAME;
    public static final int MAXIMUM_DOMAIN_SIZE = 256;


    private final DomainList domainList;
    private final JsonTransformer jsonTransformer;
    private Service service;

    @Inject
    public DomainRoutes(DomainList domainList, JsonTransformer jsonTransformer) {
        this.domainList = domainList;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public void define(Service service) {
        this.service = service;

        defineGetDomains();

        defineDomainExists();

        defineAddDomain();

        defineDeleteDomain();
    }

    @DELETE
    @Path("/{domainName}")
    @ApiOperation(value = "Deleting a domain")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "string", name = "domainName", paramType = "path")
    })
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "OK. Domain is removed."),
            @ApiResponse(code = 500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineDeleteDomain() {
        service.delete(SPECIFIC_DOMAIN, this::removeDomain);
    }

    @PUT
    @Path("/{domainName}")
    @ApiOperation(value = "Creating new domain")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "string", name = "domainName", paramType = "path")
    })
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "OK. New domain is created."),
            @ApiResponse(code = 400, message = "Invalid request for domain creation"),
            @ApiResponse(code = 500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineAddDomain() {
        service.put(SPECIFIC_DOMAIN, this::addDomain);
    }

    @GET
    @Path("/{domainName}")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "string", name = "domainName", paramType = "path")
    })
    @ApiOperation(value = "Testing existence of a domain.")
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "The domain exists", response = String.class),
            @ApiResponse(code = 404, message = "The domain does not exist."),
            @ApiResponse(code = 500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineDomainExists() {
        service.get(SPECIFIC_DOMAIN, this::exists);
    }

    @GET
    @ApiOperation(value = "Getting all domains")
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "OK", response = List.class),
            @ApiResponse(code = 500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineGetDomains() {
        service.get(DOMAINS,
            (request, response) -> domainList.getDomains(),
            jsonTransformer);
    }

    private String removeDomain(Request request, Response response) {
        try {
            String domain = request.params(DOMAIN_NAME);
            removeDomain(domain);
        } catch (DomainListException e) {
            LOGGER.info("{} did not exists", request.params(DOMAIN_NAME));
        }
        response.status(204);
        return Constants.EMPTY_BODY;
    }

    private void removeDomain(String domain) throws DomainListException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(domain));
        domainList.removeDomain(domain);
    }

    private String addDomain(Request request, Response response) {
        try {
            addDomain(request.params(DOMAIN_NAME));
            response.status(204);
        } catch (DomainListException e) {
            LOGGER.info("{} already exists", request.params(DOMAIN_NAME));
            response.status(204);
        } catch (IllegalArgumentException e) {
            LOGGER.info("Invalid request for domain creation");
            response.status(400);
        }
        return Constants.EMPTY_BODY;
    }

    private void addDomain(String domain) throws DomainListException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(domain));
        Preconditions.checkArgument(!domain.contains("@"));
        Preconditions.checkArgument(domain.length() < MAXIMUM_DOMAIN_SIZE);
        domainList.addDomain(domain);
    }

    private String exists(Request request, Response response) throws DomainListException {
        if (!domainList.containsDomain(request.params(DOMAIN_NAME))) {
            response.status(404);
        } else {
            response.status(204);
        }
        return Constants.EMPTY_BODY;
    }
}
