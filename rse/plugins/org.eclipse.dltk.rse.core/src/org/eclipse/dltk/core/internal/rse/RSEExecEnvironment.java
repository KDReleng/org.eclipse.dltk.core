package org.eclipse.dltk.core.internal.rse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.dltk.core.DLTKCore;
import org.eclipse.dltk.core.environment.IDeployment;
import org.eclipse.dltk.core.environment.IEnvironment;
import org.eclipse.dltk.core.environment.IExecutionEnvironment;
import org.eclipse.dltk.core.internal.rse.perfomance.RSEPerfomanceStatistics;
import org.eclipse.dltk.internal.launching.execution.EFSDeployment;
import org.eclipse.rse.core.model.IHost;
import org.eclipse.rse.core.subsystems.ISubSystem;
import org.eclipse.rse.internal.efs.RSEFileSystem;
import org.eclipse.rse.services.clientserver.messages.SystemMessageException;
import org.eclipse.rse.services.shells.IHostShell;
import org.eclipse.rse.services.shells.IShellService;
import org.eclipse.rse.subsystems.shells.core.subsystems.servicesubsystem.IShellServiceSubSystem;

public class RSEExecEnvironment implements IExecutionEnvironment {
	private final static String EXIT_CMD = "exit"; //$NON-NLS-1$
	// private final static String CMD_DELIMITER = " ;"; //$NON-NLS-1$
	private RSEEnvironment environment;
	private static int counter = -1;

	private static Map hostToEnvironment = new HashMap();

	public RSEExecEnvironment(RSEEnvironment env) {
		this.environment = env;
	}

	public IDeployment createDeployment() {
		if (RSEPerfomanceStatistics.PERFOMANCE_TRACING) {
			RSEPerfomanceStatistics
					.inc(RSEPerfomanceStatistics.DEPLOYMENTS_CREATED);
		}
		try {
			String rootPath = getTempDir() + environment.getSeparator()
					+ getTempName("dltk", ".tmp");
			URI rootUri = createRemoteURI(environment.getHost(), rootPath);
			return new EFSDeployment(environment, rootUri);
		} catch (CoreException e) {
			if (DLTKCore.DEBUG)
				e.printStackTrace();
		}
		return null;
	}

	private URI createRemoteURI(IHost host, String rootPath) {
		return RSEFileSystem.getURIFor(host.getHostName(), rootPath);
	}

	private IShellServiceSubSystem getShellServiceSubSystem(IHost host) {
		ISubSystem[] subsys = host.getSubSystems();
		for (int i = 0; i < subsys.length; i++) {
			if (subsys[i] instanceof IShellServiceSubSystem)
				return (IShellServiceSubSystem) subsys[i];
		}
		return null;
	}

	private String getTempName(String prefix, String suffix) {
		if (counter == -1) {
			counter = new Random().nextInt() & 0xffff;
		}
		counter++;
		return prefix + Integer.toString(counter) + suffix;
	}

	private String getTempDir() {
		IShellServiceSubSystem system = getShellServiceSubSystem(environment
				.getHost());
		try {
			system.connect(new NullProgressMonitor(), false);
		} catch (Exception e) {
			if (DLTKCore.DEBUG) {
				e.printStackTrace();
			}
		}
		String temp = system.getConnectorService().getTempDirectory();
		if (temp.length() == 0) {
			temp = "/tmp";
		}
		return temp;
	}

	public Process exec(String[] cmdLine, IPath workingDir, String[] environment)
			throws CoreException {
		if (RSEPerfomanceStatistics.PERFOMANCE_TRACING) {
			RSEPerfomanceStatistics
					.inc(RSEPerfomanceStatistics.EXECUTION_COUNT);
		}
		long start = System.currentTimeMillis();
		IShellServiceSubSystem shell = getShellServiceSubSystem(this.environment
				.getHost());
		try {
			shell.connect(null, false);
		} catch (Exception e) {
			if (DLTKCore.DEBUG) {
				e.printStackTrace();
			}
			return null;
		}

		if (!shell.isConnected()) {
			return null;
		}
		IShellService shellService = shell.getShellService();
		IHostShell hostShell = null;
		String workingDirectory = null;
		if (workingDir != null) {
			workingDirectory = this.environment.convertPathToString(workingDir);
		} else {
			workingDirectory = "/";
		}
		try {
			hostShell = shellService.runCommand(workingDirectory, "bash",
					environment, new NullProgressMonitor());
		} catch (SystemMessageException e1) {
			DLTKRSEPlugin.log(e1);
			return null;
		}

		// Sometimes environment variables aren't set, so use export.
		if (environment != null) {
			// TODO: Skip environment variables what is already in shell.
			for (int i = 0; i < environment.length; i++) {
				hostShell.writeToShell("export "
						+ toShellArguments(environment[i]));
			}
		}
		String pattern = "DLTK_INITIAL_PREFIX_EXECUTION_STRING:"
				+ String.valueOf(System.currentTimeMillis());
		String echoCommand = "echo \"" + pattern + "\"";
		// hostShell.writeToShell(echoCommand);
		String command = createCommand(cmdLine);
		hostShell.writeToShell(echoCommand + " ;" + command + " ;"
				+ echoCommand + " ;" + EXIT_CMD);
		// hostShell.writeToShell(echoCommand);
		// hostShell.writeToShell(EXIT_CMD);
		Process p = null;
		try {
			p = new MyHostShellProcessAdapter(hostShell, pattern);
		} catch (Exception e) {
			if (p != null) {
				p.destroy();
			}
			throw new RuntimeException("Failed to run remote command");
		}
		long end = System.currentTimeMillis();
		if (RSEPerfomanceStatistics.PERFOMANCE_TRACING) {
			RSEPerfomanceStatistics.inc(RSEPerfomanceStatistics.EXECUTION_TIME,
					(end - start));
		}
		return p;
	}

	private String toShellArguments(String cmd) {
		String replaceAll = cmd.replaceAll(" ", "\\\\ ");
		return replaceAll;
	}

	private String createWorkingDir(IPath workingDir) {
		if (workingDir == null)
			return ".";
		return workingDir.toPortableString();
	}

	private String createCommand(String[] cmdLine) {
		StringBuffer cmd = new StringBuffer();
		for (int i = 1; i < cmdLine.length; i++) {
			cmd.append(cmdLine[i]);
			if (i != cmdLine.length - 1) {
				cmd.append(" ");
			}
		}
		return cmdLine[0] + " " + /* toShellArguments( */cmd.toString()/* ) */;
	}

	public Map getEnvironmentVariables(boolean realyNeed) {
		if (!realyNeed) {
			return new HashMap();
		}
		long start = System.currentTimeMillis();
		if (hostToEnvironment.containsKey(this.environment.getHost())) {
			return (Map) hostToEnvironment.get(this.environment.getHost());
		}
		final Map result = new HashMap();
		try {
			Process process = this.exec(new String[] { "set" }, new Path(""),
					null);
			if (process != null) {
				final BufferedReader input = new BufferedReader(
						new InputStreamReader(process.getInputStream()));
				Thread t = new Thread(new Runnable() {
					public void run() {
						try {
							while (true) {
								String line;
								line = input.readLine();
								if (line == null) {
									break;
								}
								line = line.trim();
								int pos = line.indexOf("=");
								if (pos != -1) {
									String varName = line.substring(0, pos);
									String varValue = line.substring(pos + 1);
									result.put(varName, varValue);
								}
							}
						} catch (IOException e) {
							DLTKRSEPlugin.log(e);
						}
					}
				});
				t.start();
				try {
					t.join(25000);// No more than 25 seconds
				} catch (InterruptedException e) {
					DLTKRSEPlugin.log(e);
				}
				process.destroy();
			}
		} catch (CoreException e) {
			DLTKRSEPlugin.log(e);
		}
		if (result.size() > 0) {
			hostToEnvironment.put(this.environment.getHost(), result);
		}
		long end = System.currentTimeMillis();
		if (RSEPerfomanceStatistics.PERFOMANCE_TRACING) {
			RSEPerfomanceStatistics
					.inc(RSEPerfomanceStatistics.ENVIRONMENT_RECEIVE_COUNT);
			RSEPerfomanceStatistics.inc(
					RSEPerfomanceStatistics.ENVIRONMENT_RECEIVE_TIME,
					(end - start));
		}
		return result;
	}

	public IEnvironment getEnvironment() {
		return environment;
	}

	public boolean isValidExecutableAndEquals(String possibleName, IPath path) {
		if (environment.getHost().getSystemType().isWindows()) {
			possibleName = possibleName.toLowerCase();
			String fName = path.removeFileExtension().toString().toLowerCase();
			String ext = path.getFileExtension();
			if (possibleName.equals(fName)
					&& ("exe".equalsIgnoreCase(ext) || "bat".equalsIgnoreCase(ext))) { //$NON-NLS-1$ //$NON-NLS-2$
				return true;
			}
		} else {
			String fName = path.lastSegment();
			if (fName.equals(possibleName)) {
				return true;
			}
		}
		return false;
	}
}
