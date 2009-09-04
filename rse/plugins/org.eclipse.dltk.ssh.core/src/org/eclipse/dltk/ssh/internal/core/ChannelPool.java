/*******************************************************************************
 * Copyright (c) 2009 xored software, Inc.  
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html  
 *
 * Contributors:
 *     xored software, Inc. - initial API and Implementation (Alex Panchenko)
 *******************************************************************************/
package org.eclipse.dltk.ssh.internal.core;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jsch.core.IJSchService;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

public class ChannelPool {

	private final String userName;
	private final int port;
	private final String hostName;

	private String password;

	private Session session;
	private final List<ChannelSftp> freeChannels = new ArrayList<ChannelSftp>();
	private final Map<ChannelSftp, ChannelUsageInfo> usedChannels = new IdentityHashMap<ChannelSftp, ChannelUsageInfo>();

	private static class ChannelUsageInfo {
		private final Object context;

		public ChannelUsageInfo(Object context) {
			this.context = context;
		}

	}

	/**
	 * @param userName
	 * @param hostName
	 * @param port
	 */
	public ChannelPool(String userName, String hostName, int port) {
		this.userName = userName;
		this.hostName = hostName;
		this.port = port;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getPassword() {
		return password;
	}

	private final class LocalUserInfo implements UserInfo,
			UIKeyboardInteractive {
		public void showMessage(String arg0) {
		}

		public boolean promptYesNo(String arg0) {
			return false;
		}

		public boolean promptPassword(String arg0) {
			return true;
		}

		public boolean promptPassphrase(String arg0) {
			return false;
		}

		public String getPassword() {
			return password;
		}

		public String getPassphrase() {
			return ""; //$NON-NLS-1$
		}

		public String[] promptKeyboardInteractive(String destination,
				String name, String instruction, String[] prompt, boolean[] echo) {
			final String p = password;
			return p != null ? new String[] { p } : null;
		}
	}

	protected synchronized void connectSession() throws JSchException {
		if (session == null) {
			IJSchService service = Activator.getDefault().getJSch();
			session = service.createSession(hostName, port, userName);
			session.setTimeout(0);
			session.setServerAliveInterval(300000);
			session.setServerAliveCountMax(6);
			session.setPassword(password); // Set password
			// directly
			UserInfo ui = new LocalUserInfo();
			session.setUserInfo(ui);
		}

		if (!session.isConnected()) {
			// Connect with default timeout
			session.connect(60 * 1000);
		}
	}

	protected ChannelSftp acquireChannel(final Object context, int tryCount) {
		for (;;) {
			try {
				return acquireChannel(context);
			} catch (JSchException ex) {
				Activator.log(ex);
				if (--tryCount <= 0) {
					return null;
				}
			}
		}
	}

	protected synchronized ChannelSftp acquireChannel(Object context)
			throws JSchException {
		try {
			connectSession();
			while (!freeChannels.isEmpty()) {
				final ChannelSftp channel = freeChannels.remove(freeChannels
						.size() - 1);
				if (channel.isConnected()) {
					usedChannels.put(channel, createUsageInfo(context));
					return channel;
				}
			}
			final ChannelSftp channel = (ChannelSftp) session
					.openChannel("sftp"); //$NON-NLS-1$			
			if (!channel.isConnected()) {
				channel.connect();
			}
			usedChannels.put(channel, createUsageInfo(context));
			return channel;
		} catch (JSchException e) {
			// String eToStr = e.toString();
			boolean needLog = true;
			// if (eToStr.indexOf("Auth cancel") >= 0 || eToStr.indexOf("Auth fail") >= 0 || eToStr.indexOf("session is down") >= 0) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			// if (session.isConnected()) {
			// session.disconnect();
			// session = null;
			// }
			// }
			disconnect();
			if (needLog) {
				Activator.error("Failed to create direct connection", e); //$NON-NLS-1$
			}
			throw e;
			// if (session != null) {
			// session.disconnect();
			// session = null;
			// }
		}
	}

	/**
	 * @return
	 */
	private ChannelUsageInfo createUsageInfo(Object context) {
		return new ChannelUsageInfo(context);
	}

	protected void releaseChannel(ChannelSftp channel) {
		usedChannels.remove(channel);
		freeChannels.add(channel);
	}

	public void disconnect() {
		for (ChannelSftp channel : freeChannels) {
			channel.disconnect();
		}
		freeChannels.clear();
		// TODO log used connections
		for (Map.Entry<ChannelSftp, ChannelUsageInfo> entry : usedChannels
				.entrySet()) {
			entry.getKey().disconnect();
		}
		usedChannels.clear();
		if (session != null) {
			session.disconnect();
			session = null;
		}
	}

	public boolean isConnected() {
		return session != null && session.isConnected();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((hostName == null) ? 0 : hostName.hashCode());
		result = prime * result + port;
		result = prime * result
				+ ((userName == null) ? 0 : userName.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ChannelPool other = (ChannelPool) obj;
		if (hostName == null) {
			if (other.hostName != null)
				return false;
		} else if (!hostName.equals(other.hostName))
			return false;
		if (port != other.port)
			return false;
		if (userName == null) {
			if (other.userName != null)
				return false;
		} else if (!userName.equals(other.userName))
			return false;
		return true;
	}

}
