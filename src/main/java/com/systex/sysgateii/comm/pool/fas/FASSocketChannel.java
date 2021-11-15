package com.systex.sysgateii.comm.pool.fas;

import com.systex.sysgateii.autosvr.ratesvr.Server.ServerProducer;
import com.systex.sysgateii.comm.pool.MultiNodeConnPoolImpl;
import com.systex.sysgateii.comm.pool.NonBlockingConnPool;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.io.Closeable;
import java.io.File;
import java.net.ConnectException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/*
 * FASSocketChannel
 * SocketChannel pool handler for FAS
 *  MatsudairaSyuMe
 *  Ver 1.5
 *  20200115
 */
public class FASSocketChannel {
	private static Logger log = LoggerFactory.getLogger(FASSocketChannel.class);
	private ByteBuf rcvBuf = Unpooled.buffer(16384);
	private ConcurrentHashMap<Channel, File> seqf_map = new ConcurrentHashMap<Channel, File>();
	private final ChannelPoolHandler CPH = new FASChannelPoolHandler(rcvBuf, seqf_map);
	private static final int DEFAULT_PORT = 15_000;
	Closeable serverMock;
	private NonBlockingConnPool connPool;
	EventLoopGroup group;

	public FASSocketChannel(String nodes[]) throws ConnectException, IllegalArgumentException, InterruptedException {
		group = new NioEventLoopGroup();
		final Bootstrap bootstrap = new Bootstrap().group(group).channel(NioSocketChannel.class);
		connPool = new MultiNodeConnPoolImpl(nodes, bootstrap, CPH, DEFAULT_PORT, 0, 1, TimeUnit.SECONDS, 3000);
		connPool.preConnect(nodes.length);
		if (log.isDebugEnabled())
			log.debug("preConnect [{}]", Arrays.toString(nodes));
	}

	public NonBlockingConnPool getConnPool() {
		return connPool;
	}

	public void setConnPool(NonBlockingConnPool connPool) {
		this.connPool = connPool;
	}
	public ByteBuf getrcvBuf() {
		return this.rcvBuf;
	}
	public ConcurrentHashMap<Channel, File> getseqfMap() {
		return this.seqf_map;
	}
}
