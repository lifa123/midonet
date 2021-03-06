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

package org.midonet.cluster.storage

import java.util.UUID

import org.midonet.cluster.backend.Directory
import org.midonet.cluster.backend.zookeeper.ZkConnectionAwareWatcher
import org.midonet.cluster.data.storage.{DirectoryStateTable, StateTable}
import org.midonet.cluster.data.storage.StateTableEncoder.MacToIdEncoder
import org.midonet.midolman.state.ReplicatedMap
import org.midonet.packets.MAC

/**
  * Wraps an IPv4-UUID [[org.midonet.midolman.state.ReplicatedMap]] to a
  * [[StateTable]], where state tables are intended as backend-agnostic
  * counterparts for replicated maps. This provides an implementation using
  * ZooKeeper as backend.
  *
  * KNOWN ISSUE: The table does not support the update of a persistent entry
  * because the underlying implementation uses the same entry version number.
  * Therefore, to modify an existing persisting entry, first delete the entry
  * and then add a new one with the same IP address.
  */
final class MacIdStateTable(override val directory: Directory,
                            zkConnWatcher: ZkConnectionAwareWatcher)
    extends DirectoryStateTable[MAC, UUID]
    with ReplicatedMapStateTable[MAC, UUID]
    with MacToIdEncoder {

    protected override val nullValue = null
    protected override val map = new ReplicatedMap[MAC, UUID](directory)
                                 with MacToIdEncoder

    if (zkConnWatcher ne null)
        map.setConnectionWatcher(zkConnWatcher)

}
