/*
 * Copyright 2016 Midokura SARL
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

package org.midonet.cluster.services.state

import java.net.URI

import com.google.inject.Inject
import com.typesafe.scalalogging.Logger

import org.slf4j.LoggerFactory

import org.midonet.cluster._
import org.midonet.cluster.services.MidonetBackend
import org.midonet.cluster.services.discovery.{MidonetDiscoveryImpl, MidonetServiceHandler}
import org.midonet.cluster.services.state.server.StateProxyServer
import org.midonet.cluster.storage.MidonetBackendConfig
import org.midonet.minion.MinionService.TargetNode
import org.midonet.minion.{Context, Minion, MinionService}
import org.midonet.util.concurrent.Executors

/**
  * The State Proxy service.
  *
  * This service allows scalable subscription of clients to state table updates.
  */
@MinionService(name = "state-proxy", runsOn = TargetNode.CLUSTER)
class StateProxy @Inject()(context: Context,
                           config: ClusterConfig,
                           backend: MidonetBackend)
    extends Minion(context) {

    private val log = Logger(LoggerFactory.getLogger(StateProxyLog))
    private var server: StateProxyServer = null
    private var discovery: MidonetServiceHandler = null
    private var manager: StateTableManager = null

    override def isEnabled = config.stateProxy.isEnabled

    override def doStart(): Unit = {
        log info s"Stating the state proxy service"

        manager = new StateTableManager(config.stateProxy, backend)
        server = new StateProxyServer(config.stateProxy, manager)

        registerServiceDiscovery()
        notifyStarted()
    }

    override def doStop(): Unit = {
        log info s"Stopping the state proxy service"

        manager.close()
        server.close()

        manager = null
        server = null

        unregisterServiceDiscovery()
        notifyStopped()
    }

    private def registerServiceDiscovery(): Unit = {
        val discoveryService = new MidonetDiscoveryImpl(backend.curator,
                                                        executor = null,
                                                        config.backend)
        val serviceName = getClass.getAnnotation(classOf[MinionService]).name()
        val serviceAddress = config.stateProxy.serverAddress
        val servicePort = config.stateProxy.serverPort
        val serviceUri = new URI("udp://" + serviceAddress + ":" + servicePort)
        discovery = discoveryService.registerServiceInstance(serviceName,
                                                             serviceUri)
    }

    private def unregisterServiceDiscovery(): Unit = {
        discovery.unregister()
        discovery = null
    }
}
