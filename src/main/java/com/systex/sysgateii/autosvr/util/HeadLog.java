package com.systex.sysgateii.autosvr.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import ch.qos.logback.classic.util.ContextInitializer;

public class HeadLog implements Runnable {

//  static Logger logger = LoggerFactory.getLogger(HeadLog.class);
	public static Logger logger = null;

	private String name;

	@Override
	public void run() {

		MDC.put("logFileName", getName());
		//System.out.println(getName());
		logger.info("hello");

		// remember remove this
		MDC.remove("logFileName");

	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

}
