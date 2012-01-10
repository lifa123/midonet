/*
 * @(#)AdRouteResource        1.6 11/09/05
 *
 * Copyright 2011 Midokura KK
 */
package com.midokura.midolman.mgmt.rest_api.resources;

import java.util.List;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.midokura.midolman.mgmt.auth.AuthManager;
import com.midokura.midolman.mgmt.auth.UnauthorizedException;
import com.midokura.midolman.mgmt.data.DaoFactory;
import com.midokura.midolman.mgmt.data.dao.AdRouteDao;
import com.midokura.midolman.mgmt.data.dao.BgpDao;
import com.midokura.midolman.mgmt.data.dao.OwnerQueryable;
import com.midokura.midolman.mgmt.data.dto.AdRoute;
import com.midokura.midolman.mgmt.data.dto.UriResource;
import com.midokura.midolman.mgmt.rest_api.core.UriManager;
import com.midokura.midolman.mgmt.rest_api.core.VendorMediaType;
import com.midokura.midolman.mgmt.rest_api.jaxrs.UnknownRestApiException;
import com.midokura.midolman.state.NoStatePathException;
import com.midokura.midolman.state.StateAccessException;

/**
 * Root resource class for advertising routes.
 *
 * @version 1.6 11 Sept 2011
 * @author Yoshi Tamura
 */
public class AdRouteResource {
    /*
     * Implements REST API end points for ad_routes.
     */

    private final static Logger log = LoggerFactory
            .getLogger(AdRouteResource.class);

    /**
     * Handler to getting BGP advertised route.
     *
     * @param id
     *            Ad route ID from the request.
     * @param context
     *            Object that holds the security data.
     * @param uriInfo
     *            Object that holds the request URI data.
     * @param daoFactory
     *            Data access factory object.
     * @throws StateAccessException
     *             Data access error.
     * @throws UnauthorizedException
     *             Authentication/authorization error.
     * @return An AdRoute object.
     */
    @GET
    @Path("{id}")
    @Produces({ VendorMediaType.APPLICATION_AD_ROUTE_JSON,
            MediaType.APPLICATION_JSON })
    public AdRoute get(@PathParam("id") UUID id,
            @Context SecurityContext context, @Context UriInfo uriInfo,
            @Context DaoFactory daoFactory) throws StateAccessException,
            UnauthorizedException {
        AdRouteDao dao = daoFactory.getAdRouteDao();
        if (!AuthManager.isOwner(context, (OwnerQueryable) dao, id)) {
            throw new UnauthorizedException(
                    "Can only see your own advertised route.");
        }

        AdRoute adRoute = null;
        try {
            adRoute = dao.get(id);
        } catch (StateAccessException e) {
            log.error("Error accessing data", e);
            throw e;
        } catch (Exception e) {
            log.error("Unhandled error", e);
            throw new UnknownRestApiException(e);
        }
        adRoute.setBaseUri(uriInfo.getBaseUri());
        return adRoute;
    }

    /**
     * Handler to deleting an advertised route.
     *
     * @param id
     *            AdRoute ID from the request.
     * @param context
     *            Object that holds the security data.
     * @param daoFactory
     *            Data access factory object.
     * @throws StateAccessException
     *             Data access error.
     * @throws UnauthorizedException
     *             Authentication/authorization error.
     */
    @DELETE
    @Path("{id}")
    public void delete(@PathParam("id") UUID id,
            @Context SecurityContext context, @Context DaoFactory daoFactory)
            throws StateAccessException, UnauthorizedException {
        AdRouteDao dao = daoFactory.getAdRouteDao();
        if (!AuthManager.isOwner(context, (OwnerQueryable) dao, id)) {
            throw new UnauthorizedException(
                    "Can only delete your own advertised route.");
        }

        try {
            dao.delete(id);
        } catch (NoStatePathException e) {
            // Deleting a non-existing record is OK.
            log.warn("The resource does not exist", e);
        } catch (StateAccessException e) {
            log.error("Error accessing data", e);
            throw e;
        } catch (Exception e) {
            log.error("Unhandled error", e);
            throw new UnknownRestApiException(e);
        }
    }

    /**
     * Sub-resource class for bgp's advertising route.
     */
    public static class BgpAdRouteResource {

        private UUID bgpId = null;

        /**
         * Constructor
         *
         * @param bgpId
         *            ID of a BGP configuration record.
         */
        public BgpAdRouteResource(UUID bgpId) {
            this.bgpId = bgpId;
        }

        private boolean isBgpOwner(SecurityContext context,
                DaoFactory daoFactory) throws StateAccessException {
            BgpDao q = daoFactory.getBgpDao();
            return AuthManager.isOwner(context, (OwnerQueryable) q, bgpId);
        }

        /**
         * Handler to getting a list of BGP advertised routes.
         *
         * @param context
         *            Object that holds the security data.
         * @param uriInfo
         *            Object that holds the request URI data.
         * @param daoFactory
         *            Data access factory object.
         * @throws StateAccessException
         *             Data access error.
         * @throws UnauthorizedException
         *             Authentication/authorization error.
         * @return A list of AdRoute objects.
         */
        @GET
        @Produces({ VendorMediaType.APPLICATION_AD_ROUTE_COLLECTION_JSON,
                MediaType.APPLICATION_JSON })
        public List<AdRoute> list(@Context SecurityContext context,
                @Context UriInfo uriInfo, @Context DaoFactory daoFactory)
                throws StateAccessException, UnauthorizedException {

            if (!isBgpOwner(context, daoFactory)) {
                throw new UnauthorizedException(
                        "Can only see your own advertised route.");
            }

            AdRouteDao dao = daoFactory.getAdRouteDao();
            List<AdRoute> adRoutes = null;
            try {
                adRoutes = dao.list(bgpId);
            } catch (StateAccessException e) {
                log.error("Error accessing data", e);
                throw e;
            } catch (Exception e) {
                log.error("Unhandled error", e);
                throw new UnknownRestApiException(e);
            }
            for (UriResource resource : adRoutes) {
                resource.setBaseUri(uriInfo.getBaseUri());
            }
            return adRoutes;
        }

        /**
         * Handler for creating BGP advertised route.
         *
         * @param chain
         *            AdRoute object.
         * @param uriInfo
         *            Object that holds the request URI data.
         * @param context
         *            Object that holds the security data.
         * @param daoFactory
         *            Data access factory object.
         * @throws StateAccessException
         *             Data access error.
         * @throws UnauthorizedException
         *             Authentication/authorization error.
         * @returns Response object with 201 status code set if successful.
         */
        @POST
        @Consumes({ VendorMediaType.APPLICATION_AD_ROUTE_JSON,
                MediaType.APPLICATION_JSON })
        public Response create(AdRoute adRoute, @Context UriInfo uriInfo,
                @Context SecurityContext context, @Context DaoFactory daoFactory)
                throws StateAccessException, UnauthorizedException {

            if (!isBgpOwner(context, daoFactory)) {
                throw new UnauthorizedException(
                        "Can only create for your own BGP.");
            }

            AdRouteDao dao = daoFactory.getAdRouteDao();
            adRoute.setBgpId(bgpId);

            UUID id = null;
            try {
                id = dao.create(adRoute);
            } catch (StateAccessException e) {
                log.error("Error accessing data", e);
                throw e;
            } catch (Exception e) {
                log.error("Unhandled error", e);
                throw new UnknownRestApiException(e);
            }

            return Response.created(
                    UriManager.getAdRoute(uriInfo.getBaseUri(), id)).build();
        }
    }

}
