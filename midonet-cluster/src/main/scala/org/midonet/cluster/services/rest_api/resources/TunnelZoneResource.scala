/*
 * Copyright 2015 Midokura SARL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.midonet.cluster.services.rest_api.resources

import java.util.UUID
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import javax.ws.rs._
import javax.ws.rs.core.MediaType.APPLICATION_JSON

import scala.concurrent.Future

import com.google.inject.Inject
import com.google.inject.servlet.RequestScoped

import org.midonet.cluster.models.Topology
import org.midonet.cluster.rest_api.validation.MessageProperty
import org.midonet.cluster.rest_api.{ConflictHttpException, BadRequestHttpException}
import org.midonet.cluster.rest_api.annotation._
import org.midonet.cluster.rest_api.models.TunnelZone
import org.midonet.cluster.rest_api.validation.MessageProperty.{UNIQUE_TUNNEL_ZONE_NAME_TYPE, getMessage}
import org.midonet.cluster.services.rest_api.MidonetMediaTypes._
import org.midonet.cluster.services.rest_api.resources.MidonetResource.{NoOps, Ops, ResourceContext}
import org.midonet.cluster.util.UUIDUtil

@ApiResource(version = 1)
@Path("tunnel_zones")
@RequestScoped
@AllowGet(Array(APPLICATION_TUNNEL_ZONE_JSON,
                APPLICATION_JSON))
@AllowList(Array(APPLICATION_TUNNEL_ZONE_COLLECTION_JSON,
                 APPLICATION_JSON))
@AllowCreate(Array(APPLICATION_TUNNEL_ZONE_JSON,
                   APPLICATION_JSON))
@AllowUpdate(Array(APPLICATION_TUNNEL_ZONE_JSON,
                   APPLICATION_JSON))
@AllowDelete
class TunnelZoneResource @Inject()(resContext: ResourceContext)
    extends MidonetResource[TunnelZone](resContext) {

    @Path("{id}/hosts")
    def hosts(@PathParam("id") id: UUID) = {
        new TunnelZoneHostResource(id, resContext)
    }

    protected override def createFilter(tz: TunnelZone): Ops = {
        throwIfTunnelZoneNameUsed(tz)
        tz.create()
        NoOps
    }

    protected override def updateFilter(to: TunnelZone, from: TunnelZone)
    : Ops = {
        throwIfTunnelZoneNameUsed(to)
        to.update(from)
        NoOps
    }

    /** Make sure that tunnel zone host entries in this tunnel zone have the
      * tunnel zone id and base uri populated.
      */
    private def fillTzDetails(tz: TunnelZone): TunnelZone = {
        if (tz.tzHosts != null) {
            tz.tzHosts.foreach { tzh =>
                tzh.tunnelZoneId = tz.id
                tzh.setBaseUri(uriInfo.getBaseUri)
            }
        }
        tz
    }

    override protected def getFilter(tz: TunnelZone): Future[TunnelZone] = {
        fillTzDetails(tz)
        Future.successful(tz)
    }

    override protected def listFilter(list: Seq[TunnelZone])
    : Future[Seq[TunnelZone]] = {
        list.foreach(fillTzDetails)
        Future.successful(list)
    }

    protected override def deleteFilter(id: String): Ops = {
        val vteps = backend.store.getAll(classOf[Topology.Vtep]).getOrThrow
        val matchingVteps = vteps.filter { v =>
            UUIDUtil.fromProto(v.getTunnelZoneId).toString == id
        }
        if (matchingVteps.nonEmpty) {
            val ids = matchingVteps.map { v => UUIDUtil.fromProto(v.getId) }
            throw new ConflictHttpException("Tunnel Zone is used by VTEP(s), " +
                                            "please delete the following " +
                                            "VTEPs before deleting this " +
                                            "tunnel zone: " + ids)
        }
        NoOps
    }

    private def throwIfTunnelZoneNameUsed(tz: TunnelZone): Unit = {
        val store = resContext.backend.store
        val nameCollision = store.getAll(classOf[Topology.TunnelZone])
                                 .getOrThrow.find ( _.getName == tz.name )
        if (nameCollision.nonEmpty) {
            throw new ConflictHttpException(
                getMessage(UNIQUE_TUNNEL_ZONE_NAME_TYPE))
        }
    }

}