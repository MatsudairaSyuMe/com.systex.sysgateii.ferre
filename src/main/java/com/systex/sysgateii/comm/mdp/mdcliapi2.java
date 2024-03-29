package com.systex.sysgateii.comm.mdp;

//import java.util.Formatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.*;

/**
 * Majordomo Protocol Client API, asynchronous Java version. Implements the
 * MDP/Worker spec at http://rfc.zeromq.org/spec:7.
 */
public class mdcliapi2 {
	private static Logger log = LoggerFactory.getLogger(mdcliapi2.class);
	private String broker;
	private ZContext ctx;
	private ZMQ.Socket client;
	private long timeout = 3000; //20220718 MatsudairaSyuMe change from 3000 to 30000
	private boolean verbose;
	//private Formatter log = new Formatter(System.out);

	public long getTimeout() {
		return timeout;
	}

	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}

	public mdcliapi2(String broker, boolean verbose) {
		this.broker = broker;
		this.verbose = verbose;
		ctx = new ZContext();
		reconnectToBroker();
	}

	/**
	 * Connect or reconnect to broker
	 */
	void reconnectToBroker() {
		if (client != null) {
			client.close();
			;
		}
		client = ctx.createSocket(SocketType.DEALER);
		client.connect(broker);
		if (verbose)
			log.debug("I: connecting to broker at [{}]...", broker);
	}

	/**
	 * Returns the reply message or NULL if there was no reply. Does not attempt to
	 * recover from a broker failure, this is not possible without storing all
	 * unanswered requests and resending them all…
	 */
	public ZMsg recv() {
		ZMsg reply = null;

		// Poll socket for a reply, with timeout
		ZMQ.Poller items = ctx.createPoller(1);
		items.register(client, ZMQ.Poller.POLLIN);
		if (items.poll(timeout) == -1)//20220718 change from timeout * 1000 to timeout 
			return null; // Interrupted
		if (verbose) {
			log.debug("I: set timeout=[{}]", timeout);
			//msg.dump(log.out());
		}

		if (items.pollin(0)) {

			ZMsg msg = ZMsg.recvMsg(client);
			// Don't try to handle errors, just assert noisily
			//assert (msg.size() >= 4);

			ZFrame empty = msg.pop();
			assert (empty.getData().length == 0);
			empty.destroy();

			//ZFrame header = msg.pop();
			//assert (MDP.C_CLIENT.equals(header.toString()));
			//header.destroy();

			//ZFrame replyService = msg.pop();
			//replyService.destroy();

			reply = msg;
		}
		items.close();
		return reply;
	}

	/**
	 * Send request to broker and get reply by hook or crook Takes ownership of
	 * request message and destroys it when sent.
	 */
	public boolean send(String service, ZMsg request) {
		assert (request != null);

		// Prefix request with protocol frames
		// Frame 0: empty (REQ emulation)
		// Frame 1: "MDPCxy" (six bytes, MDP/Client x.y)
		// Frame 2: Service name (printable string)
		//request.addFirst(service);
		//request.addFirst(MDP.C_CLIENT.newFrame());
		//request.addFirst("");
		if (verbose) {
			log.debug("I: send request to '{}' service:", service);
			//request.dump(log.out());
			log.debug("ZMsg:{}", request.toString());
		}
		return request.send(client, true); //20220727 equest.send(client); change to equest.send(client, true);
	}

	public void destroy() {
		ctx.destroy();
	}
}
