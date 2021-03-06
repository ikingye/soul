/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dromara.soul.register.client.zookeeper;

import lombok.extern.slf4j.Slf4j;
import org.I0Itec.zkclient.ZkClient;
import org.dromara.soul.common.enums.RpcTypeEnum;
import org.dromara.soul.common.utils.GsonUtils;
import org.dromara.soul.register.client.api.SoulClientRegisterRepository;
import org.dromara.soul.register.common.config.SoulRegisterCenterConfig;
import org.dromara.soul.register.common.dto.MetaDataRegisterDTO;
import org.dromara.soul.register.common.dto.URIRegisterDTO;
import org.dromara.soul.register.common.path.ZkRegisterPathConstants;
import org.dromara.soul.spi.Join;

import java.util.Properties;

/**
 * The type Zookeeper client register repository.
 *
 * @author xiaoyu
 * @author lw1243925457
 */
@Join
@Slf4j
public class ZookeeperClientRegisterRepository implements SoulClientRegisterRepository {

    private ZkClient zkClient;

    @Override
    public void init(final SoulRegisterCenterConfig config) {
        Properties props = config.getProps();
        int zookeeperSessionTimeout = Integer.parseInt(props.getProperty("zookeeperSessionTimeout", "3000"));
        int zookeeperConnectionTimeout = Integer.parseInt(props.getProperty("zookeeperConnectionTimeout", "3000"));
        this.zkClient = new ZkClient(config.getServerLists(), zookeeperSessionTimeout, zookeeperConnectionTimeout);
    }

    @Override
    public void persistInterface(final MetaDataRegisterDTO metadata) {
        String rpcType = metadata.getRpcType();
        String contextPath = metadata.getContextPath().substring(1);
        registerMetadata(rpcType, contextPath, metadata);
        if (RpcTypeEnum.HTTP.getName().equals(rpcType) || RpcTypeEnum.TARS.getName().equals(rpcType) || RpcTypeEnum.GRPC.getName().equals(rpcType)) {
            registerURI(rpcType, contextPath, metadata);
        }
        log.info("{} zookeeper client register success: {}", rpcType, metadata.toString());
    }

    @Override
    public void close() {
        zkClient.close();
    }

    private void registerMetadata(final String rpcType, final String contextPath, final MetaDataRegisterDTO metadata) {
        String metadataNodeName = buildMetadataNodeName(metadata);
        String metaDataPath = ZkRegisterPathConstants.buildMetaDataParentPath(rpcType, contextPath);
        if (!zkClient.exists(metaDataPath)) {
            zkClient.createPersistent(metaDataPath, true);
        }
        String realNode = ZkRegisterPathConstants.buildRealNode(metaDataPath, metadataNodeName);
        if (zkClient.exists(realNode)) {
            zkClient.writeData(realNode, GsonUtils.getInstance().toJson(metadata));
        } else {
            zkClient.createPersistent(realNode, GsonUtils.getInstance().toJson(metadata));
        }
    }

    private synchronized void registerURI(final String rpcType, final String contextPath, final MetaDataRegisterDTO metadata) {
        String uriNodeName = buildURINodeName(metadata);
        String uriPath = ZkRegisterPathConstants.buildURIParentPath(rpcType, contextPath);
        if (!zkClient.exists(uriPath)) {
            zkClient.createPersistent(uriPath, true);
        }
        String realNode = ZkRegisterPathConstants.buildRealNode(uriPath, uriNodeName);
        if (!zkClient.exists(realNode)) {
            zkClient.createEphemeral(realNode, GsonUtils.getInstance().toJson(URIRegisterDTO.transForm(metadata)));
        }
    }

    private String buildURINodeName(final MetaDataRegisterDTO metadata) {
        String host = metadata.getHost();
        int port = metadata.getPort();
        return String.join(":", host, Integer.toString(port));
    }

    private String buildMetadataNodeName(final MetaDataRegisterDTO metadata) {
        String nodeName;
        String rpcType = metadata.getRpcType();
        if (RpcTypeEnum.HTTP.getName().equals(rpcType) || RpcTypeEnum.SPRING_CLOUD.getName().equals(rpcType)) {
            nodeName = String.join("-", metadata.getContextPath(), metadata.getRuleName().replace("/", "-"));
        } else {
            nodeName = buildNodeName(metadata.getServiceName(), metadata.getMethodName());
        }
        return nodeName.substring(1);
    }

    private String buildNodeName(final String serviceName, final String methodName) {
        return String.join(DOT_SEPARATOR, serviceName, methodName);
    }
}
