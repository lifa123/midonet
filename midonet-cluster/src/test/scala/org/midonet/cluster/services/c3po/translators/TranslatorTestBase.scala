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
package org.midonet.cluster.services.c3po.translators

import scala.concurrent.Promise
import java.util.{UUID => JUUID}

import org.mockito.Mockito.{mock, when}
import org.mockito.Matchers.any
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}

import org.midonet.cluster.data.storage.{NotFoundException, ReadOnlyStorage, StateTableStorage}
import org.midonet.cluster.models.Commons.UUID
import org.midonet.cluster.models.Topology.Chain
import org.midonet.cluster.services.c3po.C3POStorageManager.{Create, Update}
import org.midonet.cluster.services.c3po.OpType
import org.midonet.midolman.state.PathBuilder
import org.midonet.packets.{IPv4Addr, MAC}

/**
 * Abstract base class for C3PO Translator unit test classes.
 */
abstract class TranslatorTestBase  extends FlatSpec
                                   with BeforeAndAfter
                                   with Matchers
                                   with StateTableManager {
    /* Each implementing unit test class initializes the (mock) storage by
     * calling initMockStorage() below. */
    protected var storage: ReadOnlyStorage = _
    protected var stateTableStorage: StateTableStorage = _
    initMockStorage()

    // For testing CRUD on the old ZK data structure (e.g. ARP table)
    private val zkRoot = "/test"
    protected val pathBldr: PathBuilder = new PathBuilder(zkRoot)

    protected def initMockStorage() {
        storage = mock(classOf[ReadOnlyStorage])
        stateTableStorage = mock(classOf[StateTableStorage])
        when(stateTableStorage.bridgeMacTablePath(any[JUUID](),
                                                  any[Short]())).thenAnswer(
            new Answer[String] {
                override def answer(invocation: InvocationOnMock): String = {
                    val id = invocation.getArguments()(0).asInstanceOf[JUUID]
                    val vlanId = invocation.getArguments()(1).asInstanceOf[Short]
                    s"$zkRoot/0/tables/Network/$id/vlans/$vlanId/mac_port_table"
                }
            }
        )
        when(stateTableStorage.bridgeMacEntryPath(any[JUUID](),
                                                  any[Short](),
                                                  any[MAC](),
                                                  any[JUUID]())).thenAnswer(
            new Answer[String] {
                override def answer(invocation: InvocationOnMock): String = {
                    val id = invocation.getArguments()(0).asInstanceOf[JUUID]
                    val vlanId = invocation.getArguments()(1).asInstanceOf[Short]
                    val mac = invocation.getArguments()(2).asInstanceOf[MAC]
                    val portId = invocation.getArguments()(3).asInstanceOf[JUUID]
                    s"$zkRoot/0/tables/Network/$id/vlans/$vlanId/" +
                    s"mac_port_table/$mac,$portId,${Int.MaxValue}"
                }
            }
        )
        when(stateTableStorage.bridgeArpTablePath(any[JUUID]())).thenAnswer(
            new Answer[String] {
                override def answer(invocation: InvocationOnMock): String = {
                    val id = invocation.getArguments()(0).asInstanceOf[JUUID]
                    s"$zkRoot/0/tables/Network/$id/ip4_mac_table"
                }
            }
        )
        when(stateTableStorage.bridgeArpEntryPath(any[JUUID](),
                                                  any[IPv4Addr](),
                                                  any[MAC]())).thenAnswer(
            new Answer[String] {
                override def answer(invocation: InvocationOnMock): String = {
                    val id = invocation.getArguments()(0).asInstanceOf[JUUID]
                    val ip = invocation.getArguments()(1).asInstanceOf[IPv4Addr]
                    val mac = invocation.getArguments()(2).asInstanceOf[MAC]
                    s"$zkRoot/0/tables/Network/$id/mac_port_table" +
                    s"/$ip,$mac,${Int.MaxValue}"
                }
            }
        )
    }

    /* Mock exists and get on an instance of M with an ID, "id". */
    protected def bind[M](id: UUID, msg: M, clazz: Class[M] = null) {
        val exists = msg != null
        val classOfM = if (clazz != null) clazz
                       else msg.getClass.asInstanceOf[Class[M]]
        when(storage.exists(classOfM, id))
            .thenReturn(Promise.successful(exists).future)
        if (exists)
            when(storage.get(classOfM, id))
                .thenReturn(Promise.successful(msg).future)
        else
            when(storage.get(classOfM, id))
                .thenThrow(new NotFoundException(classOfM, id))
    }

    /* Mock exists and getAll on instances of M with its ID in "ids".
     * msgs should NOT be null. */
    protected def bindAll[M](
            ids: Seq[UUID], msgs: Seq[M], clazz: Class[M] = null) {
        // We may bind null to a non-null ID, but not vice versa.
        assert(ids.size >= msgs.size)
        val classOfM = if (clazz != null) clazz
                       else msgs(0).getClass.asInstanceOf[Class[M]]
        for ((id, msg) <- ids.zipAll(msgs, null, null)) {
            val exists = msg != null
            when(storage.exists(classOfM, id))
                .thenReturn(Promise.successful(exists).future)
            if (exists)
                when(storage.get(classOfM, id))
                    .thenReturn(Promise.successful(msg.asInstanceOf[M]).future)
            else
                when(storage.get(classOfM, id))
                    .thenThrow(new NotFoundException(classOfM, id))
        }
        when(storage.getAll(classOfM, ids))
                    .thenReturn(Promise.successful(msgs).future)
    }

    /* Finds an operation on Chain with the specified chain ID, and returns a
     * first one found.
     */
    protected def findChainOp(ops: OperationList,
                              op: OpType.OpType,
                              chainId: UUID) = {
        ops.collectFirst {
            case Create(c: Chain)
                if c.getId == chainId && op == OpType.Create => c
            case Update(c: Chain, _)
                if c.getId == chainId && op == OpType.Update => c
        }.orNull
    }
}