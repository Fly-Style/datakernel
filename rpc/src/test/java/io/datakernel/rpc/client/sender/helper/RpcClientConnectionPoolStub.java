package io.datakernel.rpc.client.sender.helper;

import io.datakernel.rpc.client.RpcClientConnectionPool;
import io.datakernel.rpc.client.sender.RpcSender;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

public class RpcClientConnectionPoolStub implements RpcClientConnectionPool {
	private final Map<InetSocketAddress, RpcSender> connections = new HashMap<>();

	public void put(InetSocketAddress address, RpcSender connection) {
		connections.put(address, connection);
	}

	public void remove(InetSocketAddress address) {
		connections.remove(address);
	}

	@Override
	public RpcSender get(InetSocketAddress address) {
		return connections.get(address);
	}
}
