package com.systex.sysgateii.comm.mdp;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import org.zeromq.*;
import org.zeromq.ZMQ.Poller;

//private Formatter log = new Formatter(System.out);

//
// Simple Pirate queue
// This is identical to load-balancing  pattern, with no reliability mechanisms
// at all. It depends on the client for recovery. Runs forever.
//
public class spqueue  implements Runnable {
	private static Logger log = LoggerFactory.getLogger(spqueue.class);
	private ZContext ctx; // Our context
	private ZMQ.Socket frontend; // Socket for frontend
	private ZMQ.Socket backend; // Socket for backend
	private boolean verbose = false; // Print activity to stdout
	private final static String WORKER_READY = "\001"; // Signals worker is ready
    private final static String IGNORE_MSG = "\004"; // 20220805 Signals form drops this msg

	public spqueue(boolean verbose) {
		this.verbose = verbose;
		this.ctx = new ZContext();
		this.frontend = ctx.createSocket(SocketType.ROUTER);
		this.backend = ctx.createSocket(SocketType.ROUTER);
	}

	/**
     * Bind broker to endpoint, can call this multiple times. We use a single socket
     * for both clients and workers.
    */
    public void bind(String frontendpoint, String backendpoint) {
    	this.frontend.bind(frontendpoint);
    	this.backend.bind(backendpoint);
    	log.debug("I: spqueue active at [{}] [{}]", frontendpoint, backendpoint);
    }

    @Override
    public void run() {
    // Queue of available workers
    	ArrayList<ZFrame> workers = new ArrayList<ZFrame>();
    	Poller poller = ctx.createPoller(2);
    	poller.register(backend, Poller.POLLIN);
    	poller.register(frontend, Poller.POLLIN);
    	//ZThread.start(new mdworker2("tcp://localhost:5556", "echo", true));
        // The body of this example is exactly the same as lruqueue2.
        while (true) {
        	boolean workersAvailable = workers.size() > 0;
        	int rc = poller.poll(-1);

            // Poll frontend only if we have available workers
            if (rc == -1)
                break; // Interrupted

            // Handle worker activity on backend
            if (poller.pollin(0)) {
            // Use worker address for LRU routing
            	ZMsg msg = ZMsg.recvMsg(backend);
            	if (msg == null)
            	    break; // Interrupted
           		if (verbose) {
          			log.debug("I: received message from backend:");
          			log.debug("from backend ZMsg:{}",msg.toString());
          			//msg.dump(log.out());
				}

            	ZFrame address = msg.unwrap();
            	workers.add(address);

            	// Forward message to client if it's not a READY
            	ZFrame frame = msg.getFirst();
            	if (new String(frame.getData(), ZMQ.CHARSET).equals(WORKER_READY))
            		msg.destroy();
                else {
                    //20220805 MatsudairaSyuMe
                    ZFrame lastframe = msg.getLast();
                    if (new String(lastframe.getData(), ZMQ.CHARSET).equals(IGNORE_MSG)) {
                        if (verbose) 
                            log.debug("I: receive ignore message from backend ZMsg:{}", msg.toString());
                        msg.destroy();
                    } else {
                        if (verbose)
                            log.debug("I: receive from backend and send message to frontend ZMsg:{}", msg.toString());
                        msg.send(frontend);
                    }
                }
            }
            if (workersAvailable && poller.pollin(1)) {
            // Get client request, route to first available worker
            	ZMsg msg = ZMsg.recvMsg(frontend);
            	if (msg != null) {
            		if (verbose) {
            			log.debug("I: received message from frontend:");
            			log.debug("from fromtend ZMsg:{}",msg.toString());
            			//msg.dump(log.out());
					}
            		msg.wrap(workers.remove(0));
            		msg.send(backend);
            	}
            }
        }

        // When we're done, clean up properly
        while (workers.size() > 0) {
        	ZFrame frame = workers.remove(0);
        	frame.destroy();
        }

        workers.clear();
    }
}