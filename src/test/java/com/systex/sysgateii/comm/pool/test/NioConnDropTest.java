package com.systex.sysgateii.comm.pool.test;

import com.systex.sysgateii.comm.pool.MultiNodeConnPoolImpl;
import com.systex.sysgateii.comm.pool.NonBlockingConnPool;
import com.systex.sysgateii.comm.pool.moke.DummyChannelPoolHandler;
import com.systex.sysgateii.comm.pool.moke.DummyClientChannelHandler;
import com.systex.sysgateii.comm.pool.test.util.NioConnDroppingServer;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import java.io.Closeable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * Created by andrey on 16.11.17.
 * Modified by MatshdairaSyume 2019/11/5
 */
public class NioConnDropTest {
	private static final int CONCURRENCY = 10;
	private static final String[] NODES = new String[] { "127.0.0.1" };
	private static final ChannelPoolHandler CPH = new DummyChannelPoolHandler();
	private static final int DEFAULT_PORT = 12_345;
	private static final long TEST_TIME_SECONDS = 100;
	private static final int CONN_ATTEMPTS = 10_000;
	private static final int FAIL_EVERY_CONN_ATTEMPT = 10;
	private static final ByteBuf PAYLOAD = Unpooled.directBuffer(0x1000).writeZero(0x1000);

	Closeable serverMock;
	NonBlockingConnPool connPool;
	EventLoopGroup group;

	@Before
	public void setUp() throws Exception {

		serverMock = new NioConnDroppingServer(DEFAULT_PORT, FAIL_EVERY_CONN_ATTEMPT);

		group = new NioEventLoopGroup();
		final Bootstrap bootstrap = new Bootstrap().group(group).channel(NioSocketChannel.class)
				.handler(new ChannelInitializer<SocketChannel>() {
					@Override
					protected final void initChannel(final SocketChannel conn) throws Exception {
						conn.pipeline().addLast(new DummyClientChannelHandler());
					}
				}).option(ChannelOption.SO_KEEPALIVE, true).option(ChannelOption.SO_REUSEADDR, true)
				.option(ChannelOption.TCP_NODELAY, true);
		connPool = new MultiNodeConnPoolImpl(NODES, bootstrap, CPH, DEFAULT_PORT, 0, 1, TimeUnit.SECONDS, -1);
		connPool.preConnect(CONCURRENCY);
	}

	@After
	public void tearDown() throws Exception {
		connPool.close();
		group.shutdownGracefully();
		serverMock.close();
	}

	@Test
	public void test() throws Exception {
		final LongAdder connCounter = new LongAdder();
		final ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
		for (int i = 0; i < CONCURRENCY; i++) {
			executor.submit(() -> {
				Channel conn;
				for (int j = 0; j < CONN_ATTEMPTS; j++) {
					try {
						while (null == (conn = connPool.lease())) {
							Thread.sleep(1);
						}
						conn.writeAndFlush(PAYLOAD.retain()).sync();
						connPool.release(conn);
						connCounter.increment();
					} catch (final InterruptedException e) {
						break;
					} catch (final Throwable cause) {
						cause.printStackTrace(System.err);
					}
				}
			});
		}
		TimeUnit.SECONDS.sleep(TEST_TIME_SECONDS);
		executor.shutdownNow();
		assertEquals(CONCURRENCY * CONN_ATTEMPTS, connCounter.sum(),
				2 * CONCURRENCY * CONN_ATTEMPTS / FAIL_EVERY_CONN_ATTEMPT);
	}

}
