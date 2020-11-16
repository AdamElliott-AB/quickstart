package com.example.myproject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.AbstractYamcsService;
import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.YConfiguration;
import org.yamcs.Spec.OptionType;
import org.yamcs.utils.ByteArrayUtils;

public class SimpleSimulator extends AbstractYamcsService {

	SimThread simThread;
	int tmPort = 4848;
	
	@Override
	public Spec getSpec() {
		Spec spec = new Spec();
        spec.addOption("tmPort", OptionType.INTEGER);
        return spec;
	}

	@Override
	public void init(String yamcsInstance, YConfiguration config) throws InitException {
		super.init(yamcsInstance, config);
        tmPort = config.getInt("tmPort", 4848);
	}

	@Override
	protected void doStart() {
		simThread = new SimThread();
		simThread.start();
        notifyStarted();
	}

	@Override
	protected void doStop() {
		simThread.stopSim();
		simThread = null;
        notifyStopped();
	}

	
	class SimThread extends Thread {
		boolean running = true;
		
		Map<Integer, AtomicInteger> seqMap = new HashMap<>();
		
		@Override
		public void run() {
			long startTime = System.currentTimeMillis();
			
			while (running) {
				long elapsedTime = System.currentTimeMillis() - startTime;
				
				// calculate dummy measurement value (repeating up/down slope)
				int measurement = (int) (elapsedTime / 1000);
				measurement = measurement % 120;
				if (measurement > 60) {
					measurement = 120 - measurement;
				}
				
				// send a telemetry packet
		        byte[] data = new byte[4];
		        ByteArrayUtils.encodeInt(measurement, data, 0);
				sendTm(101, data);
				
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {}
			}
		}

		private void sendTm(int apid, byte[] data) {
			byte[] binary = new byte[data.length + 6];

			// SpacePacket primary header
	        // 1. set APID
	        ByteArrayUtils.encodeShort((short) (0x0000 | (apid & 0x07FF)), binary, 0);
	        // 2. set sequence count
	        int seqCount = getSeqCount(apid);
	        ByteArrayUtils.encodeShort((short) (0xC000 | (seqCount & 0x3FFF)), binary, 2);
	        // 3. set packet-length field in CCSDS primary header
	        ByteArrayUtils.encodeShort(binary.length - 7, binary, 4);

			// user data
	        System.arraycopy(data, 0, binary, 6, data.length);
	        
	        // send packet over UDP
	        try {
	        	DatagramPacket packet = new DatagramPacket(binary, binary.length, InetAddress.getByName("127.0.0.1"), tmPort);
	        	try (DatagramSocket socket = new DatagramSocket()) {
	        		socket.send(packet);
	        	}
	        } catch (Exception e) {
	        	e.printStackTrace();
	        }
		}
		
		private int getSeqCount(int apid) {
			AtomicInteger lastCount = seqMap.get(apid);
			int count = 0;
			if (lastCount == null) {
				lastCount = new AtomicInteger(0);
				seqMap.put(apid, lastCount);
			}
			count = lastCount.incrementAndGet();
			return count;
		}

		public void stopSim() {
			running = false;
			interrupt();
		}
	}
}
