package org.eclipse.dltk.ssh.internal.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Vector;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.dltk.ssh.core.ISshConnection;
import org.eclipse.dltk.ssh.core.ISshFileHandle;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.ChannelSftp.LsEntry;

/**
 * TODO: Add correct operation synchronization.
 * 
 */
public class SshConnection extends ChannelPool implements ISshConnection {
	private long disabledTime = 0;

	private static abstract class Operation {
		boolean finished = false;

		public boolean isLongRunning() {
			return false;
		}

		public abstract void perform(ChannelSftp channel) throws SftpException;

		public void setFinished() {
			finished = true;
		}

		public boolean isFinished() {
			return finished;
		}

	}

	private static class GetStatOperation extends Operation {
		private IPath path;
		private SftpATTRS attrs;

		public GetStatOperation(IPath path) {
			this.path = path;
		}

		@Override
		public String toString() {
			return "Get information for file:" + path; //$NON-NLS-1$
		}

		@Override
		public void perform(ChannelSftp channel) throws SftpException {
			attrs = channel.stat(path.toString());
		}

		public SftpATTRS getAttrs() {
			return attrs;
		}
	}

	private static class ResolveLinkOperation extends Operation {
		private IPath path;
		private IPath resolvedPath;

		public ResolveLinkOperation(IPath path) {
			this.path = path;
		}

		@Override
		public String toString() {
			return "Resolve link information for file:" + path; //$NON-NLS-1$
		}

		@Override
		public void perform(ChannelSftp channel) throws SftpException {
			SftpATTRS attrs = channel.stat(path.toString());
			boolean isRoot = (path.segmentCount() == 0);
			String linkTarget = null;
			String canonicalPath;
			String parentPath = path.removeLastSegments(1).toString();
			if (attrs.isLink() && !isRoot) {
				try {
					String fullPath = path.toString();
					boolean readlinkDone = false;
					try {
						linkTarget = channel.readlink(fullPath);
						readlinkDone = true;
					} catch (Throwable t) {
						channel.cd(fullPath);
						linkTarget = channel.pwd();
						canonicalPath = linkTarget;
					}
					if (linkTarget != null && !linkTarget.equals(fullPath)) {
						if (readlinkDone) {
							String curdir = channel.pwd();
							if (!parentPath.equals(curdir)) {
								channel.cd(parentPath);
							}
						}
						SftpATTRS attrsTarget = channel.stat(linkTarget);
						if (readlinkDone && attrsTarget.isDir()) {
							channel.cd(fullPath);
							canonicalPath = channel.pwd();
						}
					} else {
						linkTarget = null;
					}
				} catch (Exception e) {
					if (e instanceof SftpException
							&& ((SftpException) e).id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
						if (linkTarget == null) {
							linkTarget = ":dangling link"; //$NON-NLS-1$
						} else {
							linkTarget = ":dangling link:" + linkTarget; //$NON-NLS-1$
						}
					}
				}
				resolvedPath = new Path(linkTarget);
			}
		}

		public IPath getResolvedPath() {
			return resolvedPath;
		}
	}

	private static final int STREAM_BUFFER_SIZE = 32000;

	private class GetOperation extends Operation {
		private IPath path;
		private InputStream stream;

		public GetOperation(IPath path) {
			this.path = path;
		}

		@Override
		public boolean isLongRunning() {
			return true;
		}

		@Override
		public void perform(ChannelSftp channel) throws SftpException {
			stream = new GetOperationInputStream(channel.get(path.toString()),
					channel);
		}

		@Override
		public String toString() {
			return "Get input stream for file:" + path; //$NON-NLS-1$
		}

		public InputStream getStream() {
			return stream;
		}

	}

	private class GetOperationInputStream extends BufferedInputStream {

		private final ChannelSftp channel;

		public GetOperationInputStream(InputStream in, ChannelSftp channel) {
			super(in, STREAM_BUFFER_SIZE);
			this.channel = channel;
		}

		@Override
		public void close() throws IOException {
			try {
				super.close();
			} finally {
				releaseChannel(channel);
			}
		}

	}

	private class PutOperation extends Operation {
		private IPath path;
		private OutputStream stream;

		public PutOperation(IPath path) {
			this.path = path;
		}

		@Override
		public String toString() {
			return "Get output stream for file:" + path; //$NON-NLS-1$
		}

		@Override
		public boolean isLongRunning() {
			return true;
		}

		@Override
		public void perform(ChannelSftp channel) throws SftpException {
			stream = new PutOperationOutputStream(channel.put(path.toString(),
					ChannelSftp.OVERWRITE), channel);
		}

		public OutputStream getStream() {
			return stream;
		}

	}

	private class PutOperationOutputStream extends BufferedOutputStream {
		private final ChannelSftp channel;

		public PutOperationOutputStream(OutputStream out, ChannelSftp channel) {
			super(out, STREAM_BUFFER_SIZE);
			this.channel = channel;
		}

		@Override
		public void close() throws IOException {
			try {
				super.close();
			} finally {
				releaseChannel(channel);
				// TODO channel.disconnect();
				// TODO channel = null;
			}
		}

	}

	private static class ListFolderOperation extends Operation {
		private IPath path;
		private Vector<LsEntry> v;

		public ListFolderOperation(IPath path) {
			this.path = path;
		}

		@Override
		public String toString() {
			return "List folder:" + path + " for files"; //$NON-NLS-1$ //$NON-NLS-2$
		}

		@Override
		@SuppressWarnings("unchecked")
		public void perform(ChannelSftp channel) throws SftpException {
			v = channel.ls(path.toString());
		}

		public Vector<LsEntry> getVector() {
			return v;
		}
	}

	private static final int DEFAULT_RETRY_COUNT = 2;

	public SshConnection(String userName, String hostName, int port) {
		super(userName, hostName, port);
	}

	public boolean connect() {
		try {
			final ChannelSftp channel = acquireChannel("connect()"); //$NON-NLS-1$
			try {
				return true;
			} finally {
				releaseChannel(channel);
			}
		} catch (JSchException e) {
			return false;
		}
	}

	private void performOperation(final Operation op) {
		performOperation(op, DEFAULT_RETRY_COUNT);
	}

	private void performOperation(final Operation op, int tryCount) {
		final ChannelSftp channel = acquireChannel(op, tryCount);
		if (channel != null) {
			boolean badChannel = false;
			try {
				op.perform(channel);
				op.setFinished();
			} catch (SftpException e) {
				if (e.id == ChannelSftp.SSH_FX_FAILURE
						&& e.getCause() instanceof JSchException) {
					Activator.log(e);
					badChannel = true;
					disconnect();
					if (tryCount > 0) {
						performOperation(op, tryCount - 1);
					}
				} else if (e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) {
					if (e.id == ChannelSftp.SSH_FX_PERMISSION_DENIED) {
						Activator.log("Permission denied to perform:" //$NON-NLS-1$
								+ op.toString());
					} else {
						Activator.log(e);
					}
				}
			} finally {
				if (!badChannel && !op.isLongRunning()) {
					releaseChannel(channel);
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.dltk.ssh.core.ISshConnection#getHandle(org.eclipse.core
	 * .runtime .IPath)
	 */
	public ISshFileHandle getHandle(IPath path) throws Exception {
		if (isDisabled()) {
			return null;
		}
		// GetStatOperation op = new GetStatOperation(path);
		// performOperation(op, DEFAULT_RETRY_COUNT);
		// if (op.isFinished()) {
		// return new SshFileHandle(this, path, op.getAttrs());
		// }
		return new SshFileHandle(this, path, null);
	}

	public boolean isDisabled() {
		return disabledTime > System.currentTimeMillis();
	}

	public void setDisabled(int timeout) {
		disabledTime = System.currentTimeMillis() + timeout;
	}

	SftpATTRS getAttrs(IPath path) {
		GetStatOperation op = new GetStatOperation(path);
		performOperation(op);
		if (op.isFinished()) {
			return op.getAttrs();
		}
		return null;
	}

	IPath getResolvedPath(IPath path) {
		ResolveLinkOperation op = new ResolveLinkOperation(path);
		performOperation(op);
		if (op.isFinished()) {
			return op.getResolvedPath();
		}
		return null;
	}

	Vector<LsEntry> list(IPath path) {
		ListFolderOperation op = new ListFolderOperation(path);
		performOperation(op);
		if (op.isFinished()) {
			return op.getVector();
		}
		return null;
	}

	void setLastModified(final IPath path, final long timestamp) {
		Operation op = new Operation() {
			@Override
			public void perform(ChannelSftp channel) throws SftpException {
				Date date = new Date(timestamp);
				System.out.println(date.toString());
				channel.setMtime(path.toString(), (int) (timestamp / 1000L));
			}
		};
		performOperation(op);
	}

	void delete(final IPath path, final boolean dir) {
		Operation op = new Operation() {
			@Override
			public void perform(ChannelSftp channel) throws SftpException {
				if (!dir) {
					channel.rm(path.toString());
				} else {
					channel.rmdir(path.toString());
				}
			}
		};
		performOperation(op);
	}

	void mkdir(final IPath path) {
		Operation op = new Operation() {
			@Override
			public void perform(ChannelSftp channel) throws SftpException {
				channel.mkdir(path.toString());
			}
		};
		performOperation(op);
	}

	InputStream get(IPath path) {
		GetOperation op = new GetOperation(path);
		performOperation(op);
		if (op.isFinished()) {
			return op.getStream();
		}
		return null;
	}

	OutputStream put(IPath path) {
		PutOperation op = new PutOperation(path);
		performOperation(op);
		if (op.isFinished()) {
			return op.getStream();
		}
		return null;
	}

}
