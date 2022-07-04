package com.systex.sysgateii.comm.mdp;

import org.zeromq.ZMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZFrame;
import org.zeromq.ZThread.IDetachedRunnable;

import com.systex.sysgateii.autosvr.autoPrtSvr.Server.FASSvr;
import com.systex.sysgateii.autosvr.util.dataUtil;

/**
 * Majordomo Protocol worker2 example. Uses the mdwrk API to hide all MDP aspects
 *
 */
public class mdworker2 implements IDetachedRunnable {
	private static Logger log = LoggerFactory.getLogger(mdworker2.class);
    //private Formatter log = new Formatter(System.out);
	private String workname = "mdworker2";
    private String backend = "tcp://localhost:5555"; // default backend address 
    private boolean verbose = false; // Print activity to stdout
    private String service = "echo"; // default service name
    private FASSvr dispatcher = null;
    private static final long TOTAL_TIMEOUT = 30000; // msecs
    private long timeout = TOTAL_TIMEOUT;
    private static final long RETRY_INTERVAL = 1000; // msecs
    private long retryInterval = RETRY_INTERVAL;
    private int totalReTryTime = (int) (timeout / retryInterval);
    public mdworker2(String  workname, String backend, String service, long timeout, int retryInterval, FASSvr dispatcher, boolean verbose) {
        this.workname = workname;
        this.backend = backend;
        this.service = service;
        this.timeout = timeout;
        this.retryInterval = retryInterval;
        this.dispatcher = dispatcher;
        this.verbose = verbose;
        this.totalReTryTime = (int) (this.timeout / this.retryInterval);
        log.debug("I: {} timeout [{}] retyrinterval=[{}] totalReTryTime=[{}]", this.workname, this.timeout,this.retryInterval, this.totalReTryTime);
    }

    @Override
    public void run(Object[] args) {
		mdwrkapi workerSession = new mdwrkapi(backend, service, verbose);

		ZMsg reply = null;
		while (true) {
			ZMsg request = workerSession.receive(reply);
			if (request == null)
				break; // Interrupted
			byte[] resultmsg = request.pop().getData();
			String telegramKey = dataUtil.getTelegramKey(resultmsg);
			ZFrame clientAddress = workerSession.getReplyTo();
//            log.format("I: mdworker2 receive request address [%s] [%s] and send to FAS\n", clientAddress.toString(), new String(resultmsg));
			log.debug("I: {} receive request address [{}] [{}] and send to FAS", this.workname, clientAddress.toString(),
					new String(resultmsg));

			boolean alreadySendTelegram = dispatcher.sendTelegram(resultmsg);
//            log.format("I: mdworker2 send fas [%b]\n", alreadySendTelegram);
			log.debug("I: {} send fas [{}]", this.workname, alreadySendTelegram);
			request.clear();
			int reTry = 0;
			do {
				resultmsg = dispatcher.getResultTelegram(telegramKey);
				if (resultmsg != null) {
					log.debug("I: {} getResultTelegram request address [{}] resultmsg=[{}]", this.workname, clientAddress.toString(), resultmsg);
					break;
				} else {
					try {
						Thread.sleep(this.retryInterval);
					} catch (InterruptedException e) {
					}
				}
			} while (++reTry < this.totalReTryTime);
			//20220613 MatsudairaSyuME check if timeout
			if (resultmsg == null) {
				log.error("I: {} getResultTelegram timeout !!!! request address [{}] make E001 error message", this.workname, clientAddress.toString());
				resultmsg = "".getBytes();
			}
			//----
			request.addFirst(new ZFrame(resultmsg));
			reply = request; // Echo is complex , just for test :-)
		}
		workerSession.destroy();
    }

	public long getTimeout() {
		return timeout;
	}

	public void setTimeout(long timeout) {
		this.timeout = timeout;
		this.totalReTryTime = (int) (this.timeout / this.retryInterval);
	}

	public long getRetryInterval() {
		return retryInterval;
	}

	public void setRetryInterval(long retryInterval) {
		this.retryInterval = retryInterval;
		this.totalReTryTime = (int) (this.timeout / this.retryInterval);
	}

}
