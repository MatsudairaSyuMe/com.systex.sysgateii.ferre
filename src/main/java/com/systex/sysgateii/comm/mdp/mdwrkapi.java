package com.systex.sysgateii.comm.mdp;

import org.zeromq.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Random;

/**
 * Majordomo Protocol Client API, Java version Implements the MDP/Worker spec at
 * http://rfc.zeromq.org/spec:7.
 */
public class mdwrkapi {
    private static Logger log = LoggerFactory.getLogger(mdwrkapi.class);

    private static final int HEARTBEAT_LIVENESS = 3; // 3-5 is reasonable
    private final static String WORKER_READY = "\001"; // Signals worker is ready

    private String broker;
    private ZContext ctx;
    private String service;

    private ZMQ.Socket worker;    // Socket to broker
    private long heartbeatAt;     // When to send HEARTBEAT
    private int liveness;         // How many attempts left
    private int heartbeat = 2500; // Heartbeat delay, msecs
    private int reconnect = 2500; // Reconnect delay, msecs

    // Internal state
    private boolean expectReply = false; // false only at start

    private long timeout = 2500;
    private boolean verbose; // Print activity to stdout
    //////private Formatter log = new Formatter(System.out);

    // Return address, if any
    private ZFrame replyTo;

    public mdwrkapi(String broker, String service, boolean verbose) {

        assert (broker != null);
        assert (service != null);
        this.broker = broker;
        this.service = service;
        this.verbose = verbose;
        ctx = new ZContext();
        reconnectToBroker();
    }

    /**
     * Send message to broker If no msg is provided, creates one internally
     *
     * @param command
     * @param option
     * @param msg
     */
    void sendToBroker(MDP command, String option, ZMsg msg) {
        msg = msg != null ? msg.duplicate() : new ZMsg();

        if (verbose) {
            log.debug("I: sending {} to broker", command);
            log.debug("ZMsg:{}", msg.toString());
        }
        msg.send(worker, true);  //20220727 change  from msg.send(worker); to msg.send(worker, true);
    }

    /**
     * Connect or reconnect to broker
     */
    void reconnectToBroker() {
        if (worker != null) {
            worker.close();
        }
        worker = ctx.createSocket(SocketType.DEALER);
        Random rand = new Random(System.nanoTime());
        String identity = String.format("%04X-%04X", rand.nextInt(0x10000), rand.nextInt(0x10000));
        worker.setIdentity(identity.getBytes(ZMQ.CHARSET));
        worker.connect(broker);
        if (verbose)
            log.debug("I: connecting to broker at {}", broker);
        ZFrame frame = new ZFrame(WORKER_READY);
        frame.send(worker, 0);

        // If liveness hits zero, queue is considered disconnected
        liveness = HEARTBEAT_LIVENESS;
        heartbeatAt = System.currentTimeMillis() + heartbeat;

    }

    /**
     * Send reply, if any, to broker and wait for next request.
     */
    public ZMsg receive(ZMsg reply) {

        if (verbose) {
            log.debug("I: receive reply=[{}] expectReply=[{}] timeout=[{}] heartbeat=[{}] HEARTBEAT_LIVENESS=[{}] reconnect=[{}]", (reply == null ? "null": "not null"), expectReply, timeout, heartbeat, HEARTBEAT_LIVENESS, reconnect);
        }
        // Format and send the reply if we were provided one
        assert (reply != null || !expectReply);
        if (reply != null) {
            assert (replyTo != null);
            if (verbose) {
                log.debug("I: ZFrame:replyTo=[{}]", reply.toString());
            }
            reply.wrap(replyTo);
            sendToBroker(MDP.W_REPLY, null, reply);
            reply.destroy();
        }
        expectReply = true;

        while (!Thread.currentThread().isInterrupted()) {
            // Poll socket for a reply, with timeout
            ZMQ.Poller items = ctx.createPoller(1);
            items.register(worker, ZMQ.Poller.POLLIN);
            if (items.poll(timeout) == -1)
                break; // Interrupted
            if (items.pollin(0)) {
                ZMsg msg = ZMsg.recvMsg(worker);
                if (msg == null)
                    break; // Interrupted
                if (verbose) {
                    log.debug("I: received message from broker:");
                    log.debug("ZMsg:{}", msg.toString());
                 }
                //liveness = HEARTBEAT_LIVENESS;
                // Don't try to handle errors, just assert noisily
                //assert (msg != null && msg.size() >= 3);
                assert (msg != null && msg.size() >= 2);

                ZFrame empty = msg.pop();
                assert (empty.getData().length == 0);
                empty.destroy();

                //ZFrame header = msg.pop();
                //assert (MDP.W_WORKER.frameEquals(header));
                //header.destroy();

                //ZFrame command = msg.pop();
                replyTo = msg.unwrap();
                return msg; // We have a request to process
            }
                /*
                if (MDP.W_REQUEST.frameEquals(command)) {
                    // We should pop and save as many addresses as there are
                    // up to a null part, but for now, just save one
                    replyTo = msg.unwrap();
                    command.destroy();
                    return msg; // We have a request to process
                } else if (MDP.W_HEARTBEAT.frameEquals(command)) {
                    // Do nothing for heartbeats
                } else if (MDP.W_DISCONNECT.frameEquals(command)) {
                     reconnectToBroker();
                } else {
////                    log.format("E: invalid input message: \n");
////                    msg.dump(log.out());
                    log.debug("E: invalid input message:"); //20220727
                    log.debug("ZMsg:{}",msg.toString());
                }
                command.destroy();
                msg.destroy();
            } else if (--liveness == 0) {
                if (verbose)
////                    log.format("W: disconnected from broker - retrying\n");
                    log.error("W: disconnected from broker - retrying");
                try {
                    Thread.sleep(reconnect);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Restore the
							// interrupted status
                    break;
                }
                reconnectToBroker();
            }
            // Send HEARTBEAT if it's time
            if (System.currentTimeMillis() > heartbeatAt) {
                sendToBroker(MDP.W_HEARTBEAT, null, null);
                heartbeatAt = System.currentTimeMillis() + heartbeat;
            }*/
            items.close();
        }
        if (Thread.currentThread().isInterrupted())
            log.error("W: interrupt received, killing worker");
        return null;
    }

    public void destroy() {
        ctx.destroy();
    }

    // ============== getters and setters =================
    public int getHeartbeat() {
        return heartbeat;
    }

    public void setHeartbeat(int heartbeat) {
        this.heartbeat = heartbeat;
    }

    public int getReconnect() {
        return reconnect;
    }

    public void setReconnect(int reconnect) {
        this.reconnect = reconnect;
    }

    public ZFrame getReplyTo() {
        return this.replyTo;
    }

    public void setReplyTo(ZFrame replyTo) {
        this.replyTo = replyTo;
    }

    //20220728
    public long getTimeout() {
        return this.timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

/*    //20220801 MatsudairaSyuMe add for hheartbeat
    public void chkHeartbeat() {
        if (System.currentTimeMillis() > heartbeatAt) {
            sendToBroker(MDP.W_HEARTBEAT, null, null);
            heartbeatAt = System.currentTimeMillis() + heartbeat;
        }
    }*/
}

