package com.systex.sysgateii.comm.pool.fas;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.systex.sysgateii.autosvr.comm.Constants;
import com.systex.sysgateii.autosvr.comm.TXP;
import com.systex.sysgateii.autosvr.listener.ActorStatusListener;
import com.systex.sysgateii.autosvr.telegram.S004;
import com.systex.sysgateii.autosvr.util.CharsetCnv;
import com.systex.sysgateii.autosvr.util.dataUtil;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;

/**
 * Created by MatsudairaSyume 2020/01/15
 */

public class FASClientChannelHandler extends ChannelInboundHandlerAdapter {
	private static Logger log = LoggerFactory.getLogger(FASClientChannelHandler.class);
	private Logger faslog = LoggerFactory.getLogger("faslog");

	private static final ByteBuf HEARTBEAT_SEQUENCE = Unpooled
			.unreleasableBuffer(Unpooled.copiedBuffer("hb_request", CharsetUtil.UTF_8));
	static AtomicInteger count = new AtomicInteger(1);
	private ByteBuf clientMessageBuf = null;
	private ConcurrentHashMap<Channel, File> seqf_map = null;
	private File seqNoFile;
	private String getSeqStr = "";
	private String clientId = "";
	private String fasSendPtrn = "-->FAS len %4d :[............%s]";
	private String fasRecvPtrn = "<--FAS len %4d :[............%s]";
	private CharsetCnv charcnv = new CharsetCnv();
	// 20200921 change isConnected from static to none static
	private final AtomicBoolean isConnected = new AtomicBoolean(false);

	List<ActorStatusListener> actorStatusListeners = new ArrayList<ActorStatusListener>();

	public List<ActorStatusListener> getActorStatusListeners() {
		return actorStatusListeners;
	}

	public void setActorStatusListeners(List<ActorStatusListener> actorStatusListeners) {
		this.actorStatusListeners = actorStatusListeners;
	}

	public FASClientChannelHandler(ByteBuf rcvBuf) {
		this.clientMessageBuf = rcvBuf;
	}

	public FASClientChannelHandler(ByteBuf rcvBuf, ConcurrentHashMap<Channel, File> seqfmap) {
		this.clientMessageBuf = rcvBuf;
		this.seqf_map = seqfmap;
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		log.debug("channelActive============");
		clientMessageBuf.clear();
		Channel currConn = ctx.channel();
		InetSocketAddress localsock = (InetSocketAddress) currConn.localAddress();
		InetSocketAddress remotsock = (InetSocketAddress) currConn.remoteAddress();
		MDC.put("SERVER_ADDRESS", (String) remotsock.getAddress().toString());
		MDC.put("SERVER_PORT", String.valueOf(remotsock.getPort()));
		MDC.put("LOCAL_ADDRESS", (String) localsock.getAddress().toString());
		MDC.put("LOCAL_PORT", String.valueOf(localsock.getPort()));
		clientId = String.valueOf(localsock.getPort());
		// 20210112 MatsudairaSyume always initialize sequence no. from 0
		try {
			seqNoFile = new File("SEQNO", "SEQNO_" + clientId);
			log.debug("seqNoFile local=" + seqNoFile.getAbsolutePath());
			if (seqNoFile.exists() == false) {
				File parent = seqNoFile.getParentFile();
				if (parent.exists() == false) {
					parent.mkdirs();
				}
				seqNoFile.createNewFile();
			}
			FileUtils.writeStringToFile(seqNoFile, "0", Charset.defaultCharset());
		} catch (IOException e) {
			e.printStackTrace();
			log.error("fatal error while create seqNofile : {}", e.getMessage());
		}
		// ----20210112
		this.seqf_map.put(ctx.channel(), seqNoFile);
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		log.debug("channelInactive==========");
		clientMessageBuf.clear();
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		log.debug("ChannelRead==============");
		try {
			if (msg instanceof ByteBuf) {
				ByteBuf buf = (ByteBuf) msg;
				log.debug("capacity=" + buf.capacity() + " readableBytes=" + buf.readableBytes() + " barray="
						+ buf.hasArray() + " nio=  " + buf.nioBufferCount());
				if (clientMessageBuf.readerIndex() > (clientMessageBuf.capacity() / 2)) {
					clientMessageBuf.discardReadBytes();
					log.debug("adjustment clientMessageBuf readerindex ={}" + clientMessageBuf.readableBytes());
				}
				if (buf.isReadable()) {
					log.debug("readable");
					int size = buf.readableBytes();
					Channel currConn = ctx.channel();
					InetSocketAddress localsock = (InetSocketAddress) currConn.localAddress();
					InetSocketAddress remotsock = (InetSocketAddress) currConn.remoteAddress();
					MDC.put("SERVER_ADDRESS", (String) remotsock.getAddress().toString());
					MDC.put("SERVER_PORT", String.valueOf(remotsock.getPort()));
					MDC.put("LOCAL_ADDRESS", (String) localsock.getAddress().toString());
					MDC.put("LOCAL_PORT", String.valueOf(localsock.getPort()));

					log.debug("readableBytes={} barray={}", size, buf.hasArray());
					if (buf.isReadable() && !buf.hasArray()) {
						// it is long raw telegram
						// 20200105
						log.debug("readableBytes={} barray={}", buf.readableBytes(), buf.hasArray());
						if (clientMessageBuf.readerIndex() > (clientMessageBuf.capacity() / 2)) {
							clientMessageBuf.discardReadBytes();
							log.debug("adjustment clientMessageBuf readerindex ={}" + clientMessageBuf.readableBytes());
						}
						clientMessageBuf.writeBytes(buf);
						log.debug("clientMessageBuf.readableBytes={}", clientMessageBuf.readableBytes());
						if (clientMessageBuf.readableBytes() > 47) {
							byte[] trnidbary = new byte[5]; // 20210512 length of "trnidbary" for BOT is 5
							Arrays.fill(trnidbary, (byte) ' ');
							clientMessageBuf.getBytes(clientMessageBuf.readerIndex() + 38, trnidbary);
							log.debug("receive broadcast {} telegram", String.valueOf(trnidbary));
							byte[] resultmsg = cnvResultTelegram();
							String telegramKey = dataUtil.getTelegramKey(resultmsg);
							if (Constants.incomingTelegramMap.containsKey(telegramKey)) {
								if (Constants.incomingTelegramMap.replace(telegramKey, resultmsg) == null)
									log.error("new incoming update by telegramKey [{}] into map table error!!!!",
											telegramKey);
								else
									log.debug("new incoming already update by telegramKey [{}] into map table",
											telegramKey);
							} else {
								Constants.incomingTelegramMap.put(telegramKey, resultmsg);
								log.debug("new incoming telegram put into map table by telegramKey [{}]", telegramKey);
							}
							log.debug("new incoming telegram map table size=[{}]",
									Constants.incomingTelegramMap.size());
						}
					}
				} else // if
					log.warn("not readable ByteBuf");
			} else
				log.error("not ByteBuf message");
		} catch (Exception e) {
			e.printStackTrace();
			log.error(e.getMessage());
		}
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object obj) throws Exception {

		if (obj instanceof IdleStateEvent) {
			IdleStateEvent event = (IdleStateEvent) obj;
//			if (IdleState.WRITER_IDLE.equals(event.state())) { // 如果寫通道處於空閒狀態就發送心跳命令
			if (IdleState.READER_IDLE.equals(event.state())) { // 如果讀通道處於空閒狀態就發送心跳命令
				ctx.channel().writeAndFlush(HEARTBEAT_SEQUENCE.duplicate());
			}
		}
	}

	public void sendBytes(ChannelHandlerContext ctx, byte[] msg) throws IOException {
		if (ctx.channel() != null && ctx.channel().isActive()) {
			// ----
			Channel currConn = ctx.channel();
			InetSocketAddress localsock = (InetSocketAddress) currConn.localAddress();
			InetSocketAddress remotsock = (InetSocketAddress) currConn.remoteAddress();
			MDC.put("SERVER_ADDRESS", (String) remotsock.getAddress().toString());
			MDC.put("SERVER_PORT", String.valueOf(remotsock.getPort()));
			MDC.put("LOCAL_ADDRESS", (String) localsock.getAddress().toString());
			MDC.put("LOCAL_PORT", String.valueOf(localsock.getPort()));
			try {
				faslog.debug(String.format(fasSendPtrn, msg.length,
						charcnv.BIG5bytesUTF8str(Arrays.copyOfRange(msg, 12, msg.length))));
			} catch (Exception e) {
				e.printStackTrace();
				log.error("send message error [{}]", e.getMessage());
			}
			// ----
			ByteBuf buf = ctx.channel().alloc().buffer().writeBytes(msg);
			ctx.channel().writeAndFlush(buf);
		} else {
			throw new IOException("Can't send message to inactive connection");
		}
	}

	// 20200215
	// modify for broadcasting and F0304
	public void publishactorSendmessage(String actorId, Object eventObj) {
		log.debug(actorId + " publish message to listener");
		log.debug("-publish end-");
	}

	// ----
	public synchronized void addActorStatusListener(ActorStatusListener listener) {
		log.debug(clientId + " actor status listener add");
		actorStatusListeners.add(listener);
	}

	public synchronized void removeActorStatusListener(ActorStatusListener listener) {
		log.debug(clientId + " actor status listener remove");
		actorStatusListeners.remove(listener);
	}

	public void publishShutdownEvent() {
		log.debug(clientId + " publish shutdown event to listener");
		log.debug("-publish end-");
	}

	public void publishActiveEvent() {
		log.debug(clientId + " publish active event to listener");
		this.isConnected.set(true);
		log.debug("-publish end-");
	}

	public void publishInactiveEvent() {
		log.debug(clientId + " publish Inactive event to listener");
		this.isConnected.set(false);
		log.debug("-publish end-");
	}

	// 20200116 MatsudairaSyuMe
	private byte[] cnvResultTelegram() {
		byte[] rtn = null;
		byte[] lenbary = new byte[3];
		byte[] telmbyteary = null;
		int size = 0;
		if (this.clientMessageBuf.hasArray() && this.clientMessageBuf.readableBytes() > 0) {
			if (this.clientMessageBuf.readableBytes() >= 12) {
				this.clientMessageBuf.getBytes(this.clientMessageBuf.readerIndex() + 3, lenbary);
				size = dataUtil.fromByteArray(lenbary);
				log.debug("clientMessageBuf.readableBytes={} size={}", this.clientMessageBuf.readableBytes(), size);
				if (size > 0 && size <= this.clientMessageBuf.readableBytes()) {
					telmbyteary = new byte[size];
					this.clientMessageBuf.readBytes(telmbyteary);
					log.debug("read {} byte(s) from clientMessageBuf after {}", size,
							this.clientMessageBuf.readableBytes());
					try {
						rtn = new byte[telmbyteary.length - TXP.CONTROL_BUFFER_SIZE];
						System.arraycopy(telmbyteary, TXP.CONTROL_BUFFER_SIZE, rtn, 0,
								telmbyteary.length - TXP.CONTROL_BUFFER_SIZE);
						byte[] faslogary = new byte[rtn.length];
						System.arraycopy(rtn, 0, faslogary, 0, rtn.length);
						for (int _tmpidx = 0; _tmpidx < rtn.length; _tmpidx++)
							faslogary[_tmpidx] = (rtn[_tmpidx] == (byte) 0x0 ? (byte) ' ' : rtn[_tmpidx]);
						faslog.debug(
								String.format(fasRecvPtrn, telmbyteary.length, charcnv.BIG5bytesUTF8str(faslogary)));
						rtn = remove03(rtn);
						log.debug("get rtn len= {}", rtn.length);
					} catch (Exception e) {
						log.warn("WORNING!!! update new seq number string {} error {}", this.getSeqStr, e.getMessage());
					}
				} // else
			}
		}
		return rtn;
	}

	private byte[] remove03(byte[] source) {
		if (source[source.length - 1] == 0x03) {
			source = ArrayUtils.subarray(source, 0, source.length - 1);
			log.debug("remove03");
		}
		return source;
	}
	// ----
}
