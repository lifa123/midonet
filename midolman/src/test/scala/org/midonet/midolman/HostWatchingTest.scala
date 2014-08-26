/*
* Copyright 2014 Midokura SARL
*/
package org.midonet.midolman

import java.util.UUID
import scala.concurrent.duration._

import akka.util.Timeout
import org.junit.runner.RunWith
import org.scalatest.concurrent.Eventually._
import org.scalatest.junit.JUnitRunner

import org.midonet.midolman.host.state.HostZkManager
import org.midonet.midolman.topology.rcu.Host
import org.midonet.midolman.topology.{VirtualToPhysicalMapper => VTPM}
import org.midonet.midolman.topology.VirtualToPhysicalMapper.HostRequest
import org.midonet.midolman.util.MidolmanSpec
import org.midonet.midolman.util.mock.MessageAccumulator

@RunWith(classOf[JUnitRunner])
class HostWatchingTest extends MidolmanSpec {
    implicit val askTimeout: Timeout = 1 second

    registerActors(VTPM -> (() => new VTPM with MessageAccumulator))

    var hostMgr: HostZkManager = _
    val hostToWatch = UUID.randomUUID()
    val hostName = "foo"

    override def beforeTest() {
        hostMgr = injector.getInstance(classOf[HostZkManager])
        newHost(hostName, hostToWatch)
    }

    private def interceptHost(): Host = {
        val h = VTPM.getAndClear().filter(_.isInstanceOf[Host]).head
        h.asInstanceOf[Host]
    }

    feature("midolman tracks hosts in the cluster correctly") {
        scenario("VTPM gets a host and receives updates from its manager") {
            VTPM ! HostRequest(hostToWatch)

            val host = interceptHost()
            host.id should equal (hostToWatch)
            host should not be ('alive)

            hostMgr.makeAlive(hostToWatch)
            eventually { interceptHost() should be ('alive) }
        }
    }
}