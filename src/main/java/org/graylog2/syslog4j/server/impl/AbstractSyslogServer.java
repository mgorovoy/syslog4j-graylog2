package org.graylog2.syslog4j.server.impl;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.graylog2.syslog4j.SyslogCharSetIF;
import org.graylog2.syslog4j.SyslogRuntimeException;
import org.graylog2.syslog4j.server.SyslogServerConfigIF;
import org.graylog2.syslog4j.server.SyslogServerEventHandlerIF;
import org.graylog2.syslog4j.server.SyslogServerEventIF;
import org.graylog2.syslog4j.server.SyslogServerIF;
import org.graylog2.syslog4j.server.SyslogServerSessionEventHandlerIF;
import org.graylog2.syslog4j.server.SyslogServerSessionlessEventHandlerIF;
import org.graylog2.syslog4j.server.impl.event.CiscoSyslogServerEvent;
import org.graylog2.syslog4j.server.impl.event.FortiGateSyslogEvent;
import org.graylog2.syslog4j.server.impl.event.SyslogServerEvent;
import org.graylog2.syslog4j.server.impl.event.structured.StructuredSyslogServerEvent;
import org.graylog2.syslog4j.util.SyslogUtility;

/**
 * AbstractSyslogServer provides a base abstract implementation of the SyslogServerIF.
 * <p/>
 * <p>Syslog4j is licensed under the Lesser GNU Public License v2.1.  A copy
 * of the LGPL license is available in the META-INF folder in all
 * distributions of Syslog4j and in the base directory of the "doc" ZIP.</p>
 *
 * @author &lt;syslog4j@productivity.org&gt;
 * @version $Id: AbstractSyslogServer.java,v 1.12 2011/01/11 05:11:13 cvs Exp $
 */
public abstract class AbstractSyslogServer implements SyslogServerIF {
    public static class Sessions extends HashMap {
        private static final long serialVersionUID = -4438949276263772580L;
        
        public static final Object syncObject = new Object();

        public void addSocket(Socket socket) {
            synchronized (syncObject) {
                put(socket, new HashMap());
            }
        }

        public Iterator getSockets() {
            if (size() > 0) {
                return keySet().iterator();

            } else {
                return null;
            }
        }

        public void addSession(Socket socket, SyslogServerEventHandlerIF eventHandler, Object session) {
            synchronized (syncObject) {
                Map handlerMap = getHandlerMap(socket);

                if (handlerMap == null) {
                    handlerMap = new HashMap();
                }

                handlerMap.put(eventHandler, session);
            }
        }

        public void removeSocket(Socket socket) {
            synchronized (syncObject) {
                Map handlerMap = getHandlerMap(socket);

                if (handlerMap != null) {
                    handlerMap.clear();
                }
            }
        }

        protected Map getHandlerMap(Socket socket) {
            Map handlerMap = null;

            if (containsKey(socket)) {
                handlerMap = (Map) get(socket);
            }

            return handlerMap;
        }

        public Object getSession(Socket socket, SyslogServerEventHandlerIF eventHandler) {
            synchronized (syncObject) {
                Map handlerMap = getHandlerMap(socket);

                Object session = handlerMap.get(eventHandler);

                return session;
            }
        }
    }

	private static final Pattern	RFC5424_REGEX		= Pattern.compile("^<[0-9]{1,3}>[0-9]+\\s");
	private static final Pattern	CISCO_REGEX			= Pattern.compile("^<[0-9]{1,3}>[0-9]*:");
	private static final Pattern	FORTI_REGEX			= Pattern.compile("^<[0-9]{1,3}>date=");

    protected String syslogProtocol = null;
    protected SyslogServerConfigIF syslogServerConfig = null;
    protected Thread thread = null;

    protected boolean shutdown = false;

    public void initialize(String protocol, SyslogServerConfigIF config) throws SyslogRuntimeException {
        this.syslogProtocol = protocol;
        this.syslogServerConfig = config;
        initialize();
    }

    public String getProtocol() {
        return this.syslogProtocol;
    }

    public SyslogServerConfigIF getConfig() {
        return this.syslogServerConfig;
    }

    protected abstract void initialize() throws SyslogRuntimeException;

    public void shutdown() throws SyslogRuntimeException {
        this.shutdown = true;
    }

    public Thread getThread() {
        return this.thread;
    }

    public void setThread(Thread thread) {
        this.thread = thread;
    }

    protected static boolean isStructuredMessage(SyslogCharSetIF syslogCharSet, byte[] receiveData) {
        String receiveDataString = SyslogUtility.newString(syslogCharSet, receiveData);

        boolean isStructuredMessage = isStructuredMessage(syslogCharSet, receiveDataString);

        return isStructuredMessage;
    }

    protected static boolean isStructuredMessage(SyslogCharSetIF syslogCharSet, String receiveData) {
		// Structured Syslog messages have a VERSION number field after <priority> followed by SPACE character
		return RFC5424_REGEX.matcher(receiveData).find();
    }

    protected static boolean isCiscoMessage(SyslogCharSetIF syslogCharSet, String receiveData) {
		// Cisco Syslog messages have an optional SERIAL number field after <priority> followed by COLON character
		return CISCO_REGEX.matcher(receiveData).find();
    }

    protected static boolean isFortiGateMessage(SyslogCharSetIF syslogCharSet, String receiveData) {
		// FortiGate Syslog messages have 'date=' after <priority>
		return FORTI_REGEX.matcher(receiveData).find();
    }

    protected static SyslogServerEventIF createEvent(SyslogServerConfigIF serverConfig, byte[] lineBytes, int lineBytesLength, InetAddress inetAddr) {
    	String line = SyslogUtility.newString(serverConfig, lineBytes, lineBytesLength);

        return createEvent(serverConfig, line, inetAddr);
    }

    protected static SyslogServerEventIF createEvent(SyslogServerConfigIF serverConfig, String line, InetAddress inetAddr) {
        SyslogServerEventIF event = null;

        if (serverConfig.isUseStructuredData() && AbstractSyslogServer.isStructuredMessage(serverConfig, line)) {
            event = new StructuredSyslogServerEvent(line, inetAddr);

		} else if (AbstractSyslogServer.isCiscoMessage(serverConfig, line)) {
            event = new CiscoSyslogServerEvent(line, inetAddr);

        } else if (AbstractSyslogServer.isFortiGateMessage(serverConfig, line)) {
            event = new FortiGateSyslogEvent(line);

        } else {
            event = new SyslogServerEvent(line, inetAddr);
        }

        return event;
    }

    public static void handleInitialize(SyslogServerIF syslogServer) {
        List eventHandlers = syslogServer.getConfig().getEventHandlers();

        for (int i = 0; i < eventHandlers.size(); i++) {
            SyslogServerEventHandlerIF eventHandler = (SyslogServerEventHandlerIF) eventHandlers.get(i);

            try {
                eventHandler.initialize(syslogServer);

            } catch (Exception exception) {
                //
            }
        }
    }

    public static void handleDestroy(SyslogServerIF syslogServer) {
        List eventHandlers = syslogServer.getConfig().getEventHandlers();

        for (int i = 0; i < eventHandlers.size(); i++) {
            SyslogServerEventHandlerIF eventHandler = (SyslogServerEventHandlerIF) eventHandlers.get(i);

            try {
                eventHandler.destroy(syslogServer);

            } catch (Exception exception) {
                //
            }
        }
    }

    public static void handleSessionOpen(Sessions sessions, SyslogServerIF syslogServer, Socket socket) {
        List eventHandlers = syslogServer.getConfig().getEventHandlers();

        for (int i = 0; i < eventHandlers.size(); i++) {
            SyslogServerEventHandlerIF eventHandler = (SyslogServerEventHandlerIF) eventHandlers.get(i);

            if (eventHandler instanceof SyslogServerSessionEventHandlerIF) {
                try {
                    Object session = ((SyslogServerSessionEventHandlerIF) eventHandler).sessionOpened(syslogServer, socket.getRemoteSocketAddress());

                    if (session != null) {
                        sessions.addSession(socket, eventHandler, session);
                    }

                } catch (Exception exception) {
                    try {
                        ((SyslogServerSessionEventHandlerIF) eventHandler).exception(null, syslogServer, socket.getRemoteSocketAddress(), exception);

                    } catch (Exception e) {
                        //
                    }
                }
            }
        }
    }

    public static void handleSessionClosed(Sessions sessions, SyslogServerIF syslogServer, Socket socket, boolean timeout) {
        List eventHandlers = syslogServer.getConfig().getEventHandlers();

        for (int i = 0; i < eventHandlers.size(); i++) {
            SyslogServerEventHandlerIF eventHandler = (SyslogServerEventHandlerIF) eventHandlers.get(i);

            if (eventHandler instanceof SyslogServerSessionEventHandlerIF) {
                Object session = sessions.getSession(socket, eventHandler);

                try {
                    ((SyslogServerSessionEventHandlerIF) eventHandler).sessionClosed(session, syslogServer, socket.getRemoteSocketAddress(), timeout);

                } catch (Exception exception) {
                    try {
                        ((SyslogServerSessionEventHandlerIF) eventHandler).exception(session, syslogServer, socket.getRemoteSocketAddress(), exception);

                    } catch (Exception e) {
                        //
                    }
                }
            }
        }
    }

    public static void handleEvent(Sessions sessions, SyslogServerIF syslogServer, DatagramPacket packet, SyslogServerEventIF event) {
        handleEvent(sessions, syslogServer, null, packet.getSocketAddress(), event);
    }

    public static void handleEvent(Sessions sessions, SyslogServerIF syslogServer, Socket socket, SyslogServerEventIF event) {
        handleEvent(sessions, syslogServer, socket, socket.getRemoteSocketAddress(), event);
    }

    protected static void handleEvent(Sessions sessions, SyslogServerIF syslogServer, Socket socket, SocketAddress socketAddress, SyslogServerEventIF event) {
        List eventHandlers = syslogServer.getConfig().getEventHandlers();

        for (int i = 0; i < eventHandlers.size(); i++) {
            SyslogServerEventHandlerIF eventHandler = (SyslogServerEventHandlerIF) eventHandlers.get(i);

            Object session = (sessions != null && socket != null) ? sessions.getSession(socket, eventHandler) : null;

            if (eventHandler instanceof SyslogServerSessionEventHandlerIF) {
                try {
                    ((SyslogServerSessionEventHandlerIF) eventHandler).event(session, syslogServer, socketAddress, event);

                } catch (Exception exception) {
                    try {
                        ((SyslogServerSessionEventHandlerIF) eventHandler).exception(session, syslogServer, socketAddress, exception);

                    } catch (Exception e) {
                        //
                    }
                }

            } else if (eventHandler instanceof SyslogServerSessionlessEventHandlerIF) {
                try {
                    ((SyslogServerSessionlessEventHandlerIF) eventHandler).event(syslogServer, socketAddress, event);

                } catch (Exception exception) {
                    try {
                        ((SyslogServerSessionlessEventHandlerIF) eventHandler).exception(syslogServer, socketAddress, exception);

                    } catch (Exception e) {
                        //
                    }
                }
            }
        }
    }

    public static void handleException(Object session, SyslogServerIF syslogServer, SocketAddress socketAddress, Exception exception) {
        List eventHandlers = syslogServer.getConfig().getEventHandlers();

        for (int i = 0; i < eventHandlers.size(); i++) {
            SyslogServerEventHandlerIF eventHandler = (SyslogServerEventHandlerIF) eventHandlers.get(i);

            if (eventHandler instanceof SyslogServerSessionEventHandlerIF) {
                try {
                    ((SyslogServerSessionEventHandlerIF) eventHandler).exception(session, syslogServer, socketAddress, exception);

                } catch (Exception e) {
                    //
                }

            } else if (eventHandler instanceof SyslogServerSessionlessEventHandlerIF) {
                try {
                    ((SyslogServerSessionlessEventHandlerIF) eventHandler).exception(syslogServer, socketAddress, exception);

                } catch (Exception e) {
                    //
                }
            }
        }
    }
}
