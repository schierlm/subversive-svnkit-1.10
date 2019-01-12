package org.polarion.team.svn.connector.svnkit;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.subversion.javahl.ClientException;
import org.apache.subversion.javahl.ISVNRepos;
import org.apache.subversion.javahl.SVNRepos;
import org.apache.subversion.javahl.SubversionException;
import org.apache.subversion.javahl.types.Lock;
import org.eclipse.team.svn.core.connector.ISVNCallListener;
import org.eclipse.team.svn.core.connector.ISVNManager;
import org.eclipse.team.svn.core.connector.ISVNProgressMonitor;
import org.eclipse.team.svn.core.connector.ISVNRepositoryFreezeAction;
import org.eclipse.team.svn.core.connector.ISVNRepositoryMessageCallback;
import org.eclipse.team.svn.core.connector.ISVNRepositoryNotificationCallback;
import org.eclipse.team.svn.core.connector.SVNConnectorException;
import org.eclipse.team.svn.core.connector.SVNDepth;
import org.eclipse.team.svn.core.connector.SVNEntryReference;
import org.eclipse.team.svn.core.connector.SVNLock;
import org.eclipse.team.svn.core.connector.SVNProperty;
import org.eclipse.team.svn.core.connector.SVNRevisionRange;
import org.eclipse.team.svn.core.utility.SVNRepositoryNotificationComposite;

public class SVNKitManager extends SVNKitService implements ISVNManager {
	protected ISVNRepos svnAdmin;
	protected SVNRepositoryNotificationComposite composite;
	protected ISVNRepositoryNotificationCallback installedNotificationCallback;

	public SVNKitManager() {
		this.svnAdmin = new SVNRepos();
		this.composite = new SVNRepositoryNotificationComposite();
	}

	public void create(String repositoryPath, RepositoryKind repositoryType, String configPath, long options, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("repositoryPath", repositoryPath);
		parameters.put("repositoryType", repositoryType);
		parameters.put("configPath", configPath);
		parameters.put("options", options);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.CREATE, parameters);
		repositoryPath = (String)parameters.get("repositoryPath");
		repositoryType = (ISVNManager.RepositoryKind)parameters.get("repositoryType");
		configPath = (String)parameters.get("configPath");
		options = (Long)parameters.get("options");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			
			ISVNManager.RepositoryKind fsType = repositoryType == null ? ISVNManager.RepositoryKind.FSFS : repositoryType;
			this.svnAdmin.create(new File(repositoryPath), (options & Options.DISABLE_FSYNC_COMMIT) != 0, (options & Options.KEEP_LOG) != 0, configPath != null ? new File(configPath) : null, fsType.id);
			
			this.fireSucceeded(ISVNCallListener.CREATE, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.CREATE, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}
	
	public void deltify(String path, SVNRevisionRange range, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("range", range);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.DELTIFY, parameters);
		path = (String)parameters.get("path");
		range = (SVNRevisionRange)parameters.get("range");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			
			this.svnAdmin.deltify(new File(path), ConversionUtility.convert(range.from), ConversionUtility.convert(range.to));
			
			this.fireSucceeded(ISVNCallListener.DELTIFY, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.DELTIFY, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public void hotCopy(String path, String targetPath, long options, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("targetPath", targetPath);
		parameters.put("options", options);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.HOT_COPY, parameters);
		path = (String)parameters.get("path");
		targetPath = (String)parameters.get("targetPath");
		options = (Long)parameters.get("options");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			
			this.svnAdmin.hotcopy(new File(path), new File(targetPath), (options & Options.CLEAN_LOGS) != 0);
			
			this.fireSucceeded(ISVNCallListener.HOT_COPY, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.HOT_COPY, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public void dump(String path, OutputStream dataOut, SVNRevisionRange range, ISVNRepositoryNotificationCallback callback, long options, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("dataOut", dataOut);
		parameters.put("range", range);
		parameters.put("callback", callback);
		parameters.put("options", options);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.DUMP, parameters);
		path = (String)parameters.get("path");
		dataOut = (OutputStream)parameters.get("dataOut");
		range = (SVNRevisionRange)parameters.get("range");
		callback = (ISVNRepositoryNotificationCallback)parameters.get("callback");
		options = (Long)parameters.get("options");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			this.composite.add(callback);
			wrapper.start();
			
			this.svnAdmin.dump(new File(path), dataOut, ConversionUtility.convert(range.from), ConversionUtility.convert(range.to), (options & Options.INCREMENTAL) != 0, (options & Options.USE_DELTAS) != 0, ConversionUtility.convert(this.composite));
			
			this.fireSucceeded(ISVNCallListener.DUMP, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.DUMP, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
			this.composite.remove(callback);
		}
	}

	public void listDBLogs(String path, ISVNRepositoryMessageCallback receiver, long options, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("receiver", receiver);
		parameters.put("options", options);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.LIST_DB_LOGS, parameters);
		path = (String)parameters.get("path");
		receiver = (ISVNRepositoryMessageCallback)parameters.get("receiver");
		options = (Long)parameters.get("options");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			
			if ((options & Options.UNUSED_ONLY) != 0) {
				this.svnAdmin.listUnusedDBLogs(new File(path), ConversionUtility.convert(receiver));
			}
			else {
				this.svnAdmin.listDBLogs(new File(path), ConversionUtility.convert(receiver));
			}
			
			this.fireSucceeded(ISVNCallListener.LIST_DB_LOGS, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.LIST_DB_LOGS, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public void load(String path, InputStream dataInput, SVNRevisionRange range, String relativePath, ISVNRepositoryNotificationCallback callback, long options, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("dataInput", dataInput);
		parameters.put("range", range);
		parameters.put("relativePath", relativePath);
		parameters.put("callback", callback);
		parameters.put("options", options);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.LOAD, parameters);
		path = (String)parameters.get("path");
		dataInput = (InputStream)parameters.get("dataInput");
		range = (SVNRevisionRange)parameters.get("range");
		relativePath = (String)parameters.get("relativePath");
		callback = (ISVNRepositoryNotificationCallback)parameters.get("callback");
		options = (Long)parameters.get("options");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			this.composite.add(callback);
			wrapper.start();
			
			this.svnAdmin.load(new File(path), dataInput, ConversionUtility.convert(range.from), ConversionUtility.convert(range.to), (options & Options.IGNORE_UUID) != 0, (options & Options.FORCE_UUID) != 0, (options & Options.USE_PRECOMMIT_HOOK) != 0, (options & Options.USE_POSTCOMMIT_HOOK) != 0, relativePath, ConversionUtility.convert(this.composite));
			
			this.fireSucceeded(ISVNCallListener.LOAD, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.LOAD, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
			this.composite.remove(callback);
		}
	}

	public void listTransactions(String path, ISVNRepositoryMessageCallback receiver, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("receiver", receiver);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.LIST_TRANSACTIONS, parameters);
		path = (String)parameters.get("path");
		receiver = (ISVNRepositoryMessageCallback)parameters.get("receiver");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			
			this.svnAdmin.lstxns(new File(path), ConversionUtility.convert(receiver));
			
			this.fireSucceeded(ISVNCallListener.LIST_TRANSACTIONS, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.LIST_TRANSACTIONS, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public long recover(String path, ISVNRepositoryNotificationCallback callback, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("callback", callback);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.RECOVER, parameters);
		path = (String)parameters.get("path");
		callback = (ISVNRepositoryNotificationCallback)parameters.get("callback");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			this.composite.add(callback);
			wrapper.start();
			
			long retVal = this.svnAdmin.recover(new File(path), ConversionUtility.convert(this.composite));
			
			this.fireSucceeded(ISVNCallListener.RECOVER, parameters, null);
			
			return retVal;
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.RECOVER, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
			this.composite.remove(callback);
		}
		return 0; //unreachable
	}

	public void freeze(ISVNRepositoryFreezeAction action, String[] paths, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("action", action);
		parameters.put("paths", paths);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.FREEZE, parameters);
		action = (ISVNRepositoryFreezeAction)parameters.get("action");
		paths = (String [])parameters.get("paths");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			
			File []files = new File[paths.length];
			for (int i =0; i < paths.length; i++) {
				files[i] = new File(paths[i]);
			}
			this.svnAdmin.freeze(ConversionUtility.convert(action), files);
			
			this.fireSucceeded(ISVNCallListener.FREEZE, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.FREEZE, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public void removeTransaction(String path, String[] transactions, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("transactions", transactions);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.REMOVE_TRANSACTIONS, parameters);
		path = (String)parameters.get("path");
		transactions = (String [])parameters.get("transactions");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			
			this.svnAdmin.rmtxns(new File(path), transactions);
			
			this.fireSucceeded(ISVNCallListener.REMOVE_TRANSACTIONS, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.REMOVE_TRANSACTIONS, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public void setRevisionProperty(SVNEntryReference reference, SVNProperty property, long options, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("reference", reference);
		parameters.put("property", property);
		parameters.put("options", Long.valueOf(options));
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.SET_REPOSITORY_REVISION_PROPERTY, parameters);
		reference = (SVNEntryReference)parameters.get("reference");
		property = (SVNProperty)parameters.get("property");
		options = ((Long)parameters.get("options")).longValue();
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.svnAdmin.setRevProp(new File(reference.path), ConversionUtility.convert(reference.pegRevision), property.name, property.value, (options & Options.USE_PREREVPROPCHANGE_HOOK) != 0, (options & Options.USE_POSTREVPROPCHANGE_HOOK) != 0);
			
			this.fireSucceeded(ISVNCallListener.SET_REPOSITORY_REVISION_PROPERTY, parameters, null);
		}
		catch (SubversionException ex) {
			this.handleSubversionException(ex, ISVNCallListener.SET_REPOSITORY_REVISION_PROPERTY, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public void verify(String path, SVNRevisionRange range, ISVNRepositoryNotificationCallback callback, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("range", range);
		parameters.put("callback", callback);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.VERIFY, parameters);
		path = (String)parameters.get("path");
		range = (SVNRevisionRange)parameters.get("range");
		callback = (ISVNRepositoryNotificationCallback)parameters.get("callback");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			this.composite.add(callback);
			wrapper.start();
			
			this.svnAdmin.verify(new File(path), ConversionUtility.convert(range.from), ConversionUtility.convert(range.to), ConversionUtility.convert(this.composite));
			
			this.fireSucceeded(ISVNCallListener.VERIFY, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.VERIFY, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
			this.composite.remove(callback);
		}
	}

	public SVNLock[] listLocks(String path, SVNDepth depth, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("depth", depth);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.LIST_LOCKS, parameters);
		path = (String)parameters.get("path");
		depth = (SVNDepth)parameters.get("depth");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			
			Set<Lock> locks = this.svnAdmin.lslocks(new File(path), ConversionUtility.convertDepth(depth));
			SVNLock []retVal = new SVNLock[locks == null ? 0 : locks.size()];
			int i = 0;
			for (Lock lock : locks) {
				retVal[i++] = ConversionUtility.convert(lock);
			}
			
			this.fireSucceeded(ISVNCallListener.LIST_LOCKS, parameters, null);
			
			return retVal;
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.LIST_LOCKS, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
		return null;//unreachable
	}

	public void removeLocks(String path, String[] locks, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("locks", locks);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.REMOVE_LOCKS, parameters);
		path = (String)parameters.get("path");
		locks = (String [])parameters.get("locks");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			
			this.svnAdmin.rmlocks(new File(path), locks);
			
			this.fireSucceeded(ISVNCallListener.REMOVE_LOCKS, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.REMOVE_LOCKS, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public void upgrade(String path, ISVNRepositoryNotificationCallback callback, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("callback", callback);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.REPOSITORY_UPGRADE, parameters);
		path = (String)parameters.get("path");
		callback = (ISVNRepositoryNotificationCallback)parameters.get("callback");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			this.composite.add(callback);
			wrapper.start();
			
			this.svnAdmin.upgrade(new File(path), ConversionUtility.convert(this.composite));
			
			this.fireSucceeded(ISVNCallListener.REPOSITORY_UPGRADE, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.REPOSITORY_UPGRADE, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
			this.composite.remove(callback);
		}
	}

	public void pack(String path, ISVNRepositoryNotificationCallback callback, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("callback", callback);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.PACK, parameters);
		path = (String)parameters.get("path");
		callback = (ISVNRepositoryNotificationCallback)parameters.get("callback");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			this.composite.add(callback);
			wrapper.start();
			
			this.svnAdmin.pack(new File(path), ConversionUtility.convert(this.composite));
			
			this.fireSucceeded(ISVNCallListener.PACK, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.PACK, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
			this.composite.remove(callback);
		}
	}

	public void dispose() {
		this.svnAdmin.dispose();
	}

	protected void cancelOperation() throws Exception {
		this.svnAdmin.cancelOperation();
	}

}
