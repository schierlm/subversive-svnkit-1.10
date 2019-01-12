package org.polarion.team.svn.connector.svnkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.subversion.javahl.ClientException;
import org.apache.subversion.javahl.SubversionException;
import org.eclipse.team.svn.core.connector.ISVNCallListener;
import org.eclipse.team.svn.core.connector.ISVNNotificationCallback;
import org.eclipse.team.svn.core.connector.ISVNProgressMonitor;
import org.eclipse.team.svn.core.connector.ISVNRepositoryNotificationCallback;
import org.eclipse.team.svn.core.connector.SVNConnectorAuthenticationException;
import org.eclipse.team.svn.core.connector.SVNConnectorCancelException;
import org.eclipse.team.svn.core.connector.SVNConnectorException;
import org.eclipse.team.svn.core.connector.SVNConnectorUnresolvedConflictException;
import org.eclipse.team.svn.core.connector.SVNEntry;
import org.eclipse.team.svn.core.connector.SVNEntryStatus;
import org.eclipse.team.svn.core.connector.SVNErrorCodes;
import org.eclipse.team.svn.core.connector.SVNNotification;
import org.eclipse.team.svn.core.connector.SVNRepositoryNotification;

public abstract class SVNKitService {
	private static ProgressMonitorThread monitorWrapperThread;

	private ArrayList<ISVNCallListener> callListeners;

	public SVNKitService() {
		this.callListeners = new ArrayList<ISVNCallListener>();
	}

	public void addCallListener(ISVNCallListener listener) {
		this.callListeners.add(listener);
	}
	
	public void removeCallListener(ISVNCallListener listener) {
		this.callListeners.remove(listener);
	}
	
	protected void handleSubversionException(SubversionException ex, String methodName, Map<String, Object> parameters) throws SVNConnectorException {
		SVNConnectorException exception = new SVNConnectorException(ConversionUtility.convertZeroCodedLine(ex.getMessage()), ex);
		this.fireFailed(methodName, parameters, exception);
		throw exception;
	}
	
	protected void handleClientException(ClientException ex, String methodName, Map<String, Object> parameters) throws SVNConnectorException {
		String msg = ConversionUtility.convertZeroCodedLine(ex.getMessage());
		SVNConnectorException exception = null;
		if (this.findConflict(ex)) {
			exception = new SVNConnectorUnresolvedConflictException(msg, ex);
		}
		if (this.findCancel(ex)) {
			exception = new SVNConnectorCancelException(msg, ex);
		}
		if (this.findAuthentication(ex)) {
			exception = new SVNConnectorAuthenticationException(msg, ex);
		}
		if (exception == null) {
			exception = new SVNConnectorException(msg, ex.getAprError(), ex);
		}
		if (methodName != null) {
			this.fireFailed(methodName, parameters, exception);
		}
		throw exception;
	}
	
	protected boolean findConflict(ClientException t) {
    	return t.getAprError() == SVNErrorCodes.fsConflict || t.getAprError() == SVNErrorCodes.fsTxnOutOfDate;
	}
    
	protected boolean findAuthentication(ClientException t) {
    	return t.getAprError() == SVNErrorCodes.raNotAuthorized;
	}
    
	protected boolean findCancel(ClientException t) {
    	return t.getAprError() == SVNErrorCodes.cancelled;
	}
	
	protected void fireAsked(String methodName, Map<String, Object> parameters) {
		for (ISVNCallListener listener : this.callListeners.toArray(new ISVNCallListener[0])) {
			listener.asked(methodName, parameters);
		}
	}
	
	protected void fireSucceeded(String methodName, Map<String, Object> parameters, Object returnValue) {
		for (ISVNCallListener listener : this.callListeners.toArray(new ISVNCallListener[0])) {
			listener.succeeded(methodName, parameters, returnValue);
		}
	}
	
	protected void fireFailed(String methodName, Map<String, Object> parameters, SVNConnectorException exception) {
		for (ISVNCallListener listener : this.callListeners.toArray(new ISVNCallListener[0])) {
			listener.failed(methodName, parameters, exception);
		}
	}
	
	protected abstract void cancelOperation() throws Exception;
	
	private synchronized static ProgressMonitorThread getProgressMonitorThread() {
		if (SVNKitService.monitorWrapperThread == null) {
			SVNKitService.monitorWrapperThread = new ProgressMonitorThread();
			SVNKitService.monitorWrapperThread.start();
		}
		return SVNKitService.monitorWrapperThread;
	}
	
	protected static ISVNProgressMonitor.ItemState makeItemState(SVNNotification arg0) {
		return new ISVNProgressMonitor.ItemState(arg0.path, arg0.action.id, arg0.kind, arg0.mimeType, arg0.contentState.id, arg0.propState.id, arg0.revision, arg0.errMsg);
	}
	
	protected static ISVNProgressMonitor.ItemState makeItemState(SVNRepositoryNotification arg0) {
		return new ISVNProgressMonitor.ItemState(arg0.path, arg0.action.id, SVNEntry.Kind.UNKNOWN, null, SVNEntryStatus.Kind.NONE.id, SVNEntryStatus.Kind.NONE.id, arg0.revision, arg0.warning);
	}
	
	protected class ProgressMonitorWrapper implements ISVNNotificationCallback, ISVNRepositoryNotificationCallback {
		public ISVNProgressMonitor monitor;
		protected int current;
		protected boolean isCanceled;
		protected long cancellationTime;
		protected Thread monitoredThread;
		
		public ProgressMonitorWrapper(ISVNProgressMonitor monitor) {
			this.monitor = monitor;
			this.current = 0;
			this.isCanceled = false;
		}

		public void cancel() {
			try {
				this.isCanceled = true;
				this.cancellationTime = System.currentTimeMillis();
				SVNKitService.this.cancelOperation();
			}
			catch (Exception e) {
			}
		}

		public void notify(SVNNotification arg0) {
			this.monitor.progress(this.current++, ISVNProgressMonitor.TOTAL_UNKNOWN, SVNKitService.makeItemState(arg0));
		}

		public void notify(SVNRepositoryNotification info) {
			this.monitor.progress(this.current++, ISVNProgressMonitor.TOTAL_UNKNOWN, SVNKitService.makeItemState(info));
		}

		public void start() {
			this.monitoredThread = Thread.currentThread();
			SVNKitService.getProgressMonitorThread().add(this);
		}

		public void finish() {
			synchronized (this) {
				this.monitoredThread = null;
			}
			SVNKitService.getProgressMonitorThread().remove(this);
		}
		
		public synchronized void interrupt() {
			if (this.monitoredThread != null) {
				this.monitoredThread.interrupt();
			}
		}
		
		public boolean isCanceled() {
			return this.isCanceled;
		}

		public long getCancellationTime() {
			return this.cancellationTime;
		}

	}

	protected static class ProgressMonitorThread extends Thread {
		private final List<ProgressMonitorWrapper> monitors = new ArrayList<ProgressMonitorWrapper>();

		public ProgressMonitorThread() {
			super("SVN Kit 1.10 Connector");
		}

		public void add(ProgressMonitorWrapper monitor) {
			synchronized (this.monitors) {
				this.monitors.add(monitor);
				this.monitors.notify();
			}
		}
		
		public void remove(ProgressMonitorWrapper monitor) {
			synchronized (this.monitors) {
				this.monitors.remove(monitor);
			}
		}
		
		public void run() {
			while (!this.isInterrupted()) {
				this.checkForActivityCancelled();
				
				try {
					synchronized (this.monitors) {
						if (this.monitors.size() == 0) {
							this.monitors.wait();
						}
						else {
							this.monitors.wait(100);
						}
					}
				}
				catch (InterruptedException ex) {
					break;
				}
			}
		}

		private void checkForActivityCancelled() {
			ProgressMonitorWrapper []monitors;
			synchronized (this.monitors) {
				monitors = this.monitors.toArray(new ProgressMonitorWrapper[this.monitors.size()]);
			}
			long staleThreadsTime = System.currentTimeMillis() - 3000; // 3 seconds should be enough for cancellation
			for (ProgressMonitorWrapper monitor : monitors) {
				if (monitor.isCanceled()) {
					if (monitor.getCancellationTime() < staleThreadsTime) {
						monitor.interrupt();
					}
				}
				else if (monitor.monitor.isActivityCancelled()) {
					monitor.cancel();
				}
			}
		}

	}
	
}
