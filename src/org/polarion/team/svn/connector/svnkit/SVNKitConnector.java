/*******************************************************************************
 * Copyright (c) 2005-2008 Polarion Software.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Alexander Gurov - Initial API and implementation
 *    Pavel Zuev - [patch] peg revisions for compare operation
 *    Micha Riser - [patch] JavaHLConnector creates a huge amount of short living threads
 *    Florent Angebault - [patch] SVN Kit 1.8 based connector gives a wrong path in diffStatus() (bug 484928)
 *******************************************************************************/

package org.polarion.team.svn.connector.svnkit;

import java.io.OutputStream;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.subversion.javahl.ClientException;
import org.apache.subversion.javahl.SubversionException;
import org.apache.subversion.javahl.callback.StatusCallback;
import org.apache.subversion.javahl.types.Status;
import org.eclipse.team.svn.core.connector.ISVNAnnotationCallback;
import org.eclipse.team.svn.core.connector.ISVNCallListener;
import org.eclipse.team.svn.core.connector.ISVNChangeListCallback;
import org.eclipse.team.svn.core.connector.ISVNConflictResolutionCallback;
import org.eclipse.team.svn.core.connector.ISVNConnector;
import org.eclipse.team.svn.core.connector.ISVNCredentialsPrompt;
import org.eclipse.team.svn.core.connector.ISVNDiffStatusCallback;
import org.eclipse.team.svn.core.connector.ISVNEntryCallback;
import org.eclipse.team.svn.core.connector.ISVNEntryInfoCallback;
import org.eclipse.team.svn.core.connector.ISVNEntryStatusCallback;
import org.eclipse.team.svn.core.connector.ISVNImportFilterCallback;
import org.eclipse.team.svn.core.connector.ISVNLogEntryCallback;
import org.eclipse.team.svn.core.connector.ISVNNotificationCallback;
import org.eclipse.team.svn.core.connector.ISVNPatchCallback;
import org.eclipse.team.svn.core.connector.ISVNProgressMonitor;
import org.eclipse.team.svn.core.connector.ISVNPropertyCallback;
import org.eclipse.team.svn.core.connector.SVNConflictResolution;
import org.eclipse.team.svn.core.connector.SVNConflictResolution.Choice;
import org.eclipse.team.svn.core.connector.SVNConnectorException;
import org.eclipse.team.svn.core.connector.SVNDepth;
import org.eclipse.team.svn.core.connector.SVNDiffStatus;
import org.eclipse.team.svn.core.connector.SVNEntry;
import org.eclipse.team.svn.core.connector.SVNEntryInfo;
import org.eclipse.team.svn.core.connector.SVNEntryReference;
import org.eclipse.team.svn.core.connector.SVNEntryRevisionReference;
import org.eclipse.team.svn.core.connector.SVNExternalReference;
import org.eclipse.team.svn.core.connector.SVNMergeInfo;
import org.eclipse.team.svn.core.connector.SVNMergeInfo.LogKind;
import org.eclipse.team.svn.core.connector.SVNProperty;
import org.eclipse.team.svn.core.connector.SVNRevision;
import org.eclipse.team.svn.core.connector.SVNRevisionRange;
import org.eclipse.team.svn.core.connector.configuration.ISVNConfigurationEventHandler;
import org.eclipse.team.svn.core.connector.ssl.SSLServerCertificateFailures;
import org.eclipse.team.svn.core.connector.ssl.SSLServerCertificateInfo;
import org.eclipse.team.svn.core.utility.SVNNotificationComposite;
import org.eclipse.team.svn.core.utility.SVNUtility;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.javahl17.UserPasswordSSHCallback;
import org.tmatesoft.svn.core.javahl17.UserPasswordSSLCallback;

/**
 * SVN connector wrapper
 * 
 * @author <A HREF="www.polarion.org">Alexander Gurov</A>, POLARION.ORG
 * 
 * @version $Revision: $ $Date: $
 */
public class SVNKitConnector extends SVNKitService implements ISVNConnector {
	protected SVNClientEx17 client;
	protected ISVNCredentialsPrompt prompt;
	protected SVNNotificationComposite composite;
	protected ISVNNotificationCallback installedNotify2;
	protected ISVNConflictResolutionCallback conflictResolver;
	protected ISVNConfigurationEventHandler configHandler;

	public SVNKitConnector() {
		SVNFileUtil.setSleepForTimestamp(false);// causes a great damage to performance of revert() and some other methods if they-re called sequentially (non-recursive calls for a lot of resources)...
//FIXME		SVNFileUtil.sleepForTimestamp(); // probably should be performed on per-operation basis.
		this.client = new SVNClientEx17();
		this.client.notification2(ConversionUtility.convert(this.composite = new SVNNotificationComposite()));
	}

	public void setConfigurationEventHandler(ISVNConfigurationEventHandler configHandler) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("configHandler", configHandler);
		this.fireAsked(ISVNCallListener.SET_CONFIGURATION_EVENT_HANDLER, parameters);
		configHandler = (ISVNConfigurationEventHandler)parameters.get("configHandler");
		
//		try {
			this.configHandler = configHandler;
			
			this.fireSucceeded(ISVNCallListener.SET_CONFIGURATION_EVENT_HANDLER, parameters, null);
//		}
//		catch (ClientException ex) {
//			this.handleClientException(ex, ISVNCallListener.SET_CONFIGURATION_EVENT_HANDLER, parameters);
//		}
	}

	public ISVNConfigurationEventHandler getConfigurationEventHandler() throws SVNConnectorException {
		this.fireAsked(ISVNCallListener.GET_CONFIGURATION_EVENT_HANDLER, null);
		this.fireSucceeded(ISVNCallListener.GET_CONFIGURATION_EVENT_HANDLER, null, this.configHandler);
		return this.configHandler;
	}

	public String getConfigDirectory() throws SVNConnectorException {
		this.fireAsked(ISVNCallListener.GET_CONFIG_DIRECTORY, null);
		try {
			String retVal = this.client.getConfigDirectory();
			this.fireSucceeded(ISVNCallListener.GET_CONFIG_DIRECTORY, null, retVal);
			return retVal;
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.GET_CONFIG_DIRECTORY, null);
		}
		// unreachable code
		return null;
	}
	
	public void setConfigDirectory(String configDir) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("configDir", configDir);
		this.fireAsked(ISVNCallListener.SET_CONFIG_DIRECTORY, parameters);
		configDir = (String)parameters.get("configDir");
		
		try {
			this.client.setConfigDirectory(configDir);
			
			this.fireSucceeded(ISVNCallListener.GET_CONFIG_DIRECTORY, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.SET_CONFIG_DIRECTORY, parameters);
		}
	}
	
	public void setUsername(String username) {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("username", username);
		this.fireAsked(ISVNCallListener.SET_USERNAME, parameters);
		username = (String)parameters.get("username");
		
		this.client.username(username);
		
		this.fireSucceeded(ISVNCallListener.SET_USERNAME, parameters, null);
	}

	public void setPassword(String password) {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("password", password);
		this.fireAsked(ISVNCallListener.SET_USERNAME, parameters);
		password = (String)parameters.get("password");
		
		this.client.password(password);
		
		this.fireSucceeded(ISVNCallListener.SET_USERNAME, parameters, null);
	}

	public boolean isCredentialsCacheEnabled() {
		this.fireAsked(ISVNCallListener.IS_CREDENTIALS_CACHE_ENABLED, null);
		
		boolean retVal = this.client.isCredentialsCacheEnabled();
		
		this.fireSucceeded(ISVNCallListener.IS_CREDENTIALS_CACHE_ENABLED, null, Boolean.valueOf(retVal));
		
		return retVal;
	}

	public void setCredentialsCacheEnabled(boolean cacheCredentials) {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("cacheCredentials", Boolean.valueOf(cacheCredentials));
		this.fireAsked(ISVNCallListener.SET_CREDENTIALS_CACHE_ENABLED, parameters);
		cacheCredentials = ((Boolean)parameters.get("cacheCredentials")).booleanValue();
		
		this.client.setCredentialsCacheEnabled(cacheCredentials);
		
		this.fireSucceeded(ISVNCallListener.SET_CREDENTIALS_CACHE_ENABLED, parameters, null);
	}

	public void setPrompt(ISVNCredentialsPrompt prompt) {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("prompt", prompt);
		this.fireAsked(ISVNCallListener.SET_PROMPT, parameters);
		prompt = (ISVNCredentialsPrompt)parameters.get("prompt");
		
		this.client.setPrompt(prompt == null ? null : new RepositoryInfoPrompt(this.prompt = prompt));
		
		this.fireSucceeded(ISVNCallListener.SET_PROMPT, parameters, null);
	}

	public ISVNCredentialsPrompt getPrompt() {
		this.fireAsked(ISVNCallListener.GET_PROMPT, null);
		this.fireSucceeded(ISVNCallListener.GET_PROMPT, null, this.prompt);
		return this.prompt;
	}

	public void setProxy(String host, int port, String username, String password) {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("host", host);
		parameters.put("port", Integer.valueOf(port));
		parameters.put("username", username);
		parameters.put("password", password);
		this.fireAsked(ISVNCallListener.SET_PROXY, parameters);
		host = (String)parameters.get("host");
		port = ((Integer)parameters.get("port")).intValue();
		username = (String)parameters.get("username");
		password = (String)parameters.get("password");
		
		this.client.setProxy(host, port, username, password);
		
		this.fireSucceeded(ISVNCallListener.SET_PROXY, parameters, null);
	}

	public void setClientSSLCertificate(String certPath, String passphrase) {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("certPath", certPath);
		parameters.put("passphrase", passphrase);
		this.fireAsked(ISVNCallListener.SET_CLIENT_SSL_CERTIFICATE, parameters);
		certPath = (String)parameters.get("certPath");
		passphrase = (String)parameters.get("passphrase");
		
		this.client.setClientSSLCertificate(certPath, passphrase);
		
		this.fireSucceeded(ISVNCallListener.SET_CLIENT_SSL_CERTIFICATE, parameters, null);
	}

	public boolean isSSLCertificateCacheEnabled() {
		this.fireAsked(ISVNCallListener.IS_SSL_CERTIFICATE_CACHE_ENABLED, null);
		
		boolean retVal = this.client.isSSLCertificateCacheEnabled();
		
		this.fireSucceeded(ISVNCallListener.IS_SSL_CERTIFICATE_CACHE_ENABLED, null, Boolean.valueOf(retVal));
		return retVal;
	}

	public void setSSLCertificateCacheEnabled(boolean enabled) {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("enabled", Boolean.valueOf(enabled));
		this.fireAsked(ISVNCallListener.SET_SSL_CERTIFICATE_CACHE_ENABLED, parameters);
		enabled = ((Boolean)parameters.get("enabled")).booleanValue();
		
		this.client.setSSLCertificateCacheEnabled(enabled);
		
		this.fireSucceeded(ISVNCallListener.SET_SSL_CERTIFICATE_CACHE_ENABLED, parameters, null);
	}

	public void setSSHCredentials(String username, String privateKeyPath, String passphrase, int port) {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("username", username);
		parameters.put("privateKeyPath", privateKeyPath);
		parameters.put("passphrase", passphrase);
		parameters.put("port", Integer.valueOf(port));
		this.fireAsked(ISVNCallListener.SET_SSH_CREDENTIALS, parameters);
		username = (String)parameters.get("username");
		privateKeyPath = (String)parameters.get("privateKeyPath");
		passphrase = (String)parameters.get("passphrase");
		port = ((Integer)parameters.get("port")).intValue();
		
		this.client.setSSHCredentials(username, privateKeyPath, passphrase, port);
		
		this.fireSucceeded(ISVNCallListener.SET_SSH_CREDENTIALS, parameters, null);
	}

	public void setSSHCredentials(String username, String password, int port) {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("username", username);
		parameters.put("password", password);
		parameters.put("port", Integer.valueOf(port));
		this.fireAsked(ISVNCallListener.SET_SSH_CREDENTIALS_PASSWORD, parameters);
		username = (String)parameters.get("username");
		password = (String)parameters.get("password");
		port = ((Integer)parameters.get("port")).intValue();
		
		this.client.setSSHCredentials(username, password, port);
		
		this.fireSucceeded(ISVNCallListener.SET_SSH_CREDENTIALS_PASSWORD, parameters, null);
	}

	public void setCommitMissingFiles(boolean commitMissingFiles) {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("commitMissingFiles", Boolean.valueOf(commitMissingFiles));
		this.fireAsked(ISVNCallListener.SET_COMMIT_MISSING_FILES, parameters);
		commitMissingFiles = ((Boolean)parameters.get("commitMissingFiles")).booleanValue();
		
		this.client.setCommitMissedFiles(commitMissingFiles);
		
		this.fireSucceeded(ISVNCallListener.SET_COMMIT_MISSING_FILES, parameters, null);
	}

	public boolean isCommitMissingFiles() {
		this.fireAsked(ISVNCallListener.IS_COMMIT_MISSING_FILES, null);
		
		boolean retVal = this.client.isCommitMissingFile();
		
		this.fireSucceeded(ISVNCallListener.IS_COMMIT_MISSING_FILES, null, Boolean.valueOf(retVal));
		
		return retVal;
	}

	public void setNotificationCallback(ISVNNotificationCallback notify) {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("notify", notify);
		this.fireAsked(ISVNCallListener.SET_NOTIFICATION_CALLBACK, parameters);
		notify = (ISVNNotificationCallback)parameters.get("notify");

		if (this.installedNotify2 != null) {
			this.composite.remove(this.installedNotify2);
		}
		this.installedNotify2 = notify;
		if (this.installedNotify2 != null) {
			this.composite.add(this.installedNotify2);
		}
		
		this.fireSucceeded(ISVNCallListener.SET_NOTIFICATION_CALLBACK, parameters, null);
	}

	public ISVNNotificationCallback getNotificationCallback() {
		this.fireAsked(ISVNCallListener.GET_NOTIFICATION_CALLBACK, null);
		this.fireSucceeded(ISVNCallListener.GET_NOTIFICATION_CALLBACK, null, this.installedNotify2);
		return this.installedNotify2;
	}


	public long checkout(SVNEntryRevisionReference fromReference, String destPath, SVNDepth depth, long options, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("fromReference", fromReference);
		parameters.put("destPath", destPath);
		parameters.put("depth", depth);
		parameters.put("options", Long.valueOf(options));
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.CHECKOUT, parameters);
		fromReference = (SVNEntryRevisionReference)parameters.get("fromReference");
		destPath = (String)parameters.get("destPath");
		depth = (SVNDepth)parameters.get("depth");
		options = ((Long)parameters.get("options")).longValue();
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			long retVal = this.client.checkout(fromReference.path, destPath, ConversionUtility.convert(fromReference.revision), ConversionUtility.convert(fromReference.pegRevision), ConversionUtility.convertDepth(depth), (options & Options.IGNORE_EXTERNALS) != 0, (options & Options.ALLOW_UNVERSIONED_OBSTRUCTIONS) != 0);
			this.fireSucceeded(ISVNCallListener.CHECKOUT, parameters, Long.valueOf(retVal));
			return retVal;
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.CHECKOUT, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
		// unreachable code
		return 0;
	}

	public void lock(String[] path, String comment, long options, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("comment", comment);
		parameters.put("options", Long.valueOf(options));
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.LOCK, parameters);
		path = (String[])parameters.get("path");
		comment = (String)parameters.get("comment");
		options = ((Long)parameters.get("options")).longValue();
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.lock(new HashSet<String>(Arrays.asList(path)), comment, (options & Options.FORCE) != 0);
			
			this.fireSucceeded(ISVNCallListener.LOCK, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.LOCK, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public void unlock(String[] path, long options, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("options", Long.valueOf(options));
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.UNLOCK, parameters);
		path = (String[])parameters.get("path");
		options = ((Long)parameters.get("options")).longValue();
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.unlock(new HashSet<String>(Arrays.asList(path)), (options & Options.FORCE) != 0);
			
			this.fireSucceeded(ISVNCallListener.UNLOCK, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.UNLOCK, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public void add(String path, SVNDepth depth, long options, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("depth", depth);
		parameters.put("options", Long.valueOf(options));
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.ADD, parameters);
		path = (String)parameters.get("path");
		depth = (SVNDepth)parameters.get("depth");
		options = ((Long)parameters.get("options")).longValue();
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.add(path, ConversionUtility.convertDepth(depth), (options & Options.FORCE) != 0, (options & Options.INCLUDE_IGNORED) != 0, (options & Options.IGNORE_AUTOPROPS) != 0, (options & Options.INCLUDE_PARENTS) != 0);
			
			this.fireSucceeded(ISVNCallListener.ADD, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.ADD, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public void commit(String []path, String message, String[] changeLists, SVNDepth depth, long options, Map revProps, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("message", message);
		parameters.put("changeLists", changeLists);
		parameters.put("depth", depth);
		parameters.put("options", Long.valueOf(options));
		parameters.put("revProps", revProps);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.COMMIT, parameters);
		path = (String[])parameters.get("path");
		message = (String)parameters.get("message");
		changeLists = (String[])parameters.get("changeLists");
		depth = (SVNDepth)parameters.get("depth");
		options = ((Long)parameters.get("options")).longValue();
		revProps = (Map)parameters.get("revProps");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		boolean noUnlock = (options & Options.KEEP_LOCKS) != 0;
		boolean keepChangelist = (options & Options.KEEP_CHANGE_LIST) != 0;
		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			if (this.client.isCommitMissingFile()) {
				// FIXME remove when SVN Kit is fixed.
				for (int i = 0; i < path.length && !monitor.isActivityCancelled(); i++) {
					this.client.status(path[i], ConversionUtility.convertDepth(depth), false, false, false, true, null, new StatusCallback() {
						public void doStatus(String path, Status status) {
							if (status.getTextStatus() == org.apache.subversion.javahl.types.Status.Kind.missing) {
								try {
									SVNKitConnector.this.client.remove(new HashSet<String>(Arrays.asList(new String[] {path})), true, false, null, null, null);
								} 
								catch (ClientException e) {
								}
							}
						}
					});
				}
			}
			
			this.composite.add(wrapper);
			wrapper.start();
			CommitInfo info1 = new CommitInfo(monitor);
			this.client.commit(new HashSet<String>(Arrays.asList(path)), ConversionUtility.convertDepth(depth), noUnlock, keepChangelist, changeLists == null ? null : Arrays.asList(changeLists), ConversionUtility.convertRevPropsToSVN(revProps), new CommitMessage(message), info1);
			
			if (info1.info != null && info1.info.getPostCommitError() != null) {
				parameters.put("lastPostCommitError", info1.info.getPostCommitError());
			}
			
			this.fireSucceeded(ISVNCallListener.COMMIT, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.COMMIT, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}
	
	public long[] update(String []path, SVNRevision revision, SVNDepth depth, long options, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("revision", revision);
		parameters.put("depth", depth);
		parameters.put("options", Long.valueOf(options));
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.UPDATE, parameters);
		path = (String[])parameters.get("path");
		revision = (SVNRevision)parameters.get("revision");
		depth = (SVNDepth)parameters.get("depth");
		options = ((Long)parameters.get("options")).longValue();
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			long []retVal = this.client.update(new HashSet<String>(Arrays.asList(path)), ConversionUtility.convert(revision), ConversionUtility.convertDepth(depth), (options & Options.DEPTH_IS_STICKY) != 0, (options & Options.INCLUDE_PARENTS) != 0, (options & Options.IGNORE_EXTERNALS) != 0, (options & Options.ALLOW_UNVERSIONED_OBSTRUCTIONS) != 0);
			
			this.fireSucceeded(ISVNCallListener.UPDATE, parameters, retVal);
			
			return retVal;
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.UPDATE, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
		// unreachable code
		return null;
	}

	public long switchTo(String path, SVNEntryRevisionReference toReference, SVNDepth depth, long options, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("toReference", toReference);
		parameters.put("depth", depth);
		parameters.put("options", Long.valueOf(options));
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.SWITCH, parameters);
		path = (String)parameters.get("path");
		toReference = (SVNEntryRevisionReference)parameters.get("toReference");
		depth = (SVNDepth)parameters.get("depth");
		options = ((Long)parameters.get("options")).longValue();
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			long retVal = this.client.doSwitch(path, toReference.path, ConversionUtility.convert(toReference.revision), ConversionUtility.convert(toReference.pegRevision), ConversionUtility.convertDepth(depth), (options & Options.DEPTH_IS_STICKY) != 0, (options & Options.IGNORE_EXTERNALS) != 0, (options & Options.ALLOW_UNVERSIONED_OBSTRUCTIONS) != 0, (options & Options.IGNORE_ANCESTRY) != 0);
			
			this.fireSucceeded(ISVNCallListener.SWITCH, parameters, Long.valueOf(retVal));
			
			return retVal;
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.SWITCH, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
		// unreachable code
		return 0;
	}

	public void revert(String []paths, SVNDepth depth, String []changeLists, long options, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("paths", paths);
		parameters.put("depth", depth);
		parameters.put("changeLists", changeLists);
		parameters.put("options", Long.valueOf(options));
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.REVERT, parameters);
		paths = (String [])parameters.get("paths");
		depth = (SVNDepth)parameters.get("depth");
		changeLists = (String[])parameters.get("changeLists");
		options = ((Long)parameters.get("options")).longValue();
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			for (String path : paths) {
				this.client.revert(path, ConversionUtility.convertDepth(depth), changeLists == null ? null : Arrays.asList(changeLists));
			}
			
			this.fireSucceeded(ISVNCallListener.REVERT, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.REVERT, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public void status(String path, SVNDepth depth, long options, String[] changeLists, ISVNEntryStatusCallback callback, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("depth", depth);
		parameters.put("options", Long.valueOf(options));
		parameters.put("changeLists", changeLists);
		parameters.put("callback", callback);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.STATUS, parameters);
		path = (String)parameters.get("path");
		depth = (SVNDepth)parameters.get("depth");
		options = ((Long)parameters.get("options")).longValue();
		changeLists = (String [])parameters.get("changeLists");
		callback = (ISVNEntryStatusCallback)parameters.get("callback");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.status(path, ConversionUtility.convertDepth(depth), (options & Options.SERVER_SIDE) != 0, (options & Options.INCLUDE_UNCHANGED) != 0, (options & Options.INCLUDE_IGNORED) != 0, (options & Options.IGNORE_EXTERNALS) != 0, changeLists == null ? null : Arrays.asList(changeLists), ConversionUtility.convert(callback));
			
			this.fireSucceeded(ISVNCallListener.STATUS, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.STATUS, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public void relocate(String from, String to, String path, SVNDepth depth, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("from", from);
		parameters.put("to", to);
		parameters.put("path", path);
		parameters.put("depth", depth);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.RELOCATE, parameters);
		from = (String)parameters.get("from");
		to = (String)parameters.get("to");
		path = (String)parameters.get("path");
		depth = (SVNDepth)parameters.get("depth");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.relocate(from, to, path, depth == SVNDepth.INFINITY);
			
			this.fireSucceeded(ISVNCallListener.RELOCATE, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.RELOCATE, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public void cleanup(String path, long options, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("options", Long.valueOf(options));
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.CLEANUP, parameters);
		path = (String)parameters.get("path");
		options = ((Long)parameters.get("options")).longValue();
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.cleanup(path);
			
			this.fireSucceeded(ISVNCallListener.CLEANUP, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.CLEANUP, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public void mergeTwo(SVNEntryRevisionReference reference1, SVNEntryRevisionReference reference2, String localPath, SVNDepth depth, long options, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("reference1", reference1);
		parameters.put("reference2", reference2);
		parameters.put("localPath", localPath);
		parameters.put("depth", depth);
		parameters.put("options", Long.valueOf(options));
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.MERGE_TWO, parameters);
		reference1 = (SVNEntryRevisionReference)parameters.get("reference1");
		reference2 = (SVNEntryRevisionReference)parameters.get("reference2");
		localPath = (String)parameters.get("localPath");
		depth = (SVNDepth)parameters.get("depth");
		options = ((Long)parameters.get("options")).longValue();
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();

			reference1 = SVNUtility.convertRevisionReference(this, reference1, monitor);
			reference2 = SVNUtility.convertRevisionReference(this, reference2, monitor);
			
			this.client.merge(reference1.path, ConversionUtility.convert(reference1.revision), reference2.path, ConversionUtility.convert(reference2.revision), localPath, (options & Options.FORCE) != 0, ConversionUtility.convertDepth(depth), (options & Options.IGNORE_ANCESTRY) != 0, (options & Options.SIMULATE) != 0, (options & Options.RECORD_ONLY) != 0);
			//FIXME
//			this.client.merge(reference1.path, ConversionUtility.convert(reference1.revision), reference2.path, ConversionUtility.convert(reference2.revision), localPath, (options & Options.FORCE) != 0, ConversionUtility.convertDepth(depth), (options & Options.IGNORE_MERGE_HISTORY) != 0, (options & Options.IGNORE_ANCESTRY) != 0, (options & Options.SIMULATE) != 0, (options & Options.RECORD_ONLY) != 0);
			
			this.fireSucceeded(ISVNCallListener.MERGE_TWO, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.MERGE_TWO, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public void merge(SVNEntryReference reference, SVNRevisionRange []revisions, String localPath, SVNDepth depth, long options, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("reference", reference);
		parameters.put("revisions", revisions);
		parameters.put("localPath", localPath);
		parameters.put("depth", depth);
		parameters.put("options", Long.valueOf(options));
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.MERGE, parameters);
		reference = (SVNEntryReference)parameters.get("reference");
		revisions = (SVNRevisionRange [])parameters.get("revisions");
		localPath = (String)parameters.get("localPath");
		depth = (SVNDepth)parameters.get("depth");
		options = ((Long)parameters.get("options")).longValue();
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.merge(reference.path, ConversionUtility.convert(reference.pegRevision), Arrays.asList(ConversionUtility.convert(revisions)), localPath, (options & Options.FORCE) != 0, ConversionUtility.convertDepth(depth), (options & Options.IGNORE_MERGE_HISTORY) != 0, (options & Options.IGNORE_ANCESTRY) != 0, (options & Options.SIMULATE) != 0, (options & Options.RECORD_ONLY) != 0);
			
			this.fireSucceeded(ISVNCallListener.MERGE, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.MERGE, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}
	
	public void mergeReintegrate(SVNEntryReference reference, String localPath, long options, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("reference", reference);
		parameters.put("localPath", localPath);
		parameters.put("options", Long.valueOf(options));
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.MERGE_REINTEGRATE, parameters);
		reference = (SVNEntryReference)parameters.get("reference");
		localPath = (String)parameters.get("localPath");
		options = ((Long)parameters.get("options")).longValue();
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.mergeReintegrate(reference.path, ConversionUtility.convert(reference.pegRevision), localPath, (options & Options.SIMULATE) != 0);
			
			this.fireSucceeded(ISVNCallListener.MERGE_REINTEGRATE, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.MERGE_REINTEGRATE, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}
	
	public SVNMergeInfo getMergeInfo(SVNEntryReference reference, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("reference", reference);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.GET_MERGE_INFO, parameters);
		reference = (SVNEntryReference)parameters.get("reference");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			SVNMergeInfo retVal = ConversionUtility.convert(this.client.getMergeinfo(reference.path, ConversionUtility.convert(reference.pegRevision)));
			
			this.fireSucceeded(ISVNCallListener.GET_MERGE_INFO, parameters, retVal);
			
			return retVal;
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.GET_MERGE_INFO, parameters);
		}
		catch (SubversionException ex) {
			this.handleSubversionException(ex, ISVNCallListener.GET_MERGE_INFO, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
		// unreachable code
		return null;
	}

    public void listMergeInfoLog(LogKind logKind, SVNEntryReference reference, SVNEntryReference mergeSourceReference, SVNRevisionRange mergeSourceRange, String[] revProps, SVNDepth depth, long options, ISVNLogEntryCallback cb, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("logKind", logKind);
		parameters.put("reference", reference);
		parameters.put("mergeSourceReference", mergeSourceReference);
		parameters.put("mergeSourceRange", mergeSourceRange);
		parameters.put("revProps", revProps);
		parameters.put("options", Long.valueOf(options));
		parameters.put("cb", cb);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.LIST_MERGE_INFO_LOG, parameters);
		logKind = (LogKind)parameters.get("logKind");
		reference = (SVNEntryReference)parameters.get("reference");
		mergeSourceReference = (SVNEntryReference)parameters.get("mergeSourceReference");
		mergeSourceRange = (SVNRevisionRange)parameters.get("mergeSourceRange");
		revProps = (String [])parameters.get("revProps");
		options = ((Long)parameters.get("options")).longValue();
		cb = (ISVNLogEntryCallback)parameters.get("cb");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.getMergeinfoLog(ConversionUtility.convertLogKind(logKind), reference.path, ConversionUtility.convert(reference.pegRevision), mergeSourceReference.path, ConversionUtility.convert(mergeSourceReference.pegRevision), ConversionUtility.convert(mergeSourceRange.from), ConversionUtility.convert(mergeSourceRange.to), (options & Options.DISCOVER_PATHS) != 0, ConversionUtility.convertDepth(depth), new HashSet<String>(Arrays.asList(revProps)), ConversionUtility.convert(cb));
			
			this.fireSucceeded(ISVNCallListener.LIST_MERGE_INFO_LOG, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.LIST_MERGE_INFO_LOG, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
    }
    
	public String []suggestMergeSources(SVNEntryReference reference, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("reference", reference);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.SUGGEST_MERGE_SOURCES, parameters);
		reference = (SVNEntryReference)parameters.get("reference");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			Set<String> tmp = this.client.suggestMergeSources(reference.path, ConversionUtility.convert(reference.pegRevision));
			String []retVal = tmp == null ? null : tmp.toArray(new String[tmp.size()]);
			
			this.fireSucceeded(ISVNCallListener.SUGGEST_MERGE_SOURCES, parameters, retVal);
			
			return retVal;
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.SUGGEST_MERGE_SOURCES, parameters);
		}
		catch (SubversionException ex) {
			this.handleSubversionException(ex, ISVNCallListener.SUGGEST_MERGE_SOURCES, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
		// unreachable code
		return null;
	}

	public void resolve(String path, Choice conflictResult, SVNDepth depth, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("conflictResult", conflictResult);
		parameters.put("depth", depth);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.RESOLVE, parameters);
		path = (String)parameters.get("path");
		conflictResult = (SVNConflictResolution.Choice)parameters.get("conflictResult");
		depth = (SVNDepth)parameters.get("depth");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.resolve(path, ConversionUtility.convertDepth(depth), ConversionUtility.convertConflictChoice(conflictResult));
			
			this.fireSucceeded(ISVNCallListener.RESOLVE, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.RESOLVE, parameters);
		}
		catch (SubversionException ex) {
			this.handleSubversionException(ex, ISVNCallListener.RESOLVE, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}
	
	public void setConflictResolver(ISVNConflictResolutionCallback listener) {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("listener", listener);
		this.fireAsked(ISVNCallListener.SET_CONFLICT_RESOLVER, parameters);
		listener = (ISVNConflictResolutionCallback)parameters.get("listener");

		this.conflictResolver = listener;
		
		this.client.setConflictResolver(ConversionUtility.convert(listener));
		
		this.fireSucceeded(ISVNCallListener.SET_CONFLICT_RESOLVER, parameters, null);
	}
	
	public ISVNConflictResolutionCallback getConflictResolver() {
		this.fireAsked(ISVNCallListener.GET_CONFLICT_RESOLVER, null);
		this.fireSucceeded(ISVNCallListener.GET_CONFLICT_RESOLVER, null, this.conflictResolver);
		return this.conflictResolver;
	}
	
    public void addToChangeList(String[] paths, String changelist, SVNDepth depth, String[] changeLists, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("paths", paths);
		parameters.put("changelist", changelist);
		parameters.put("depth", depth);
		parameters.put("changeLists", changeLists);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.ADD_TO_CHANGE_LIST, parameters);
		paths = (String [])parameters.get("paths");
		changelist = (String)parameters.get("changelist");
		depth = (SVNDepth)parameters.get("depth");
		changeLists = (String [])parameters.get("changeLists");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.addToChangelist(new HashSet<String>(Arrays.asList(paths)), changelist, ConversionUtility.convertDepth(depth), changeLists == null ? null : Arrays.asList(changeLists));
			
			this.fireSucceeded(ISVNCallListener.ADD_TO_CHANGE_LIST, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.ADD_TO_CHANGE_LIST, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
    }
    
    public void removeFromChangeLists(String[] paths, SVNDepth depth, String[] changeLists, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("paths", paths);
		parameters.put("depth", depth);
		parameters.put("changeLists", changeLists);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.REMOVE_FROM_CHANGE_LISTS, parameters);
		paths = (String [])parameters.get("paths");
		depth = (SVNDepth)parameters.get("depth");
		changeLists = (String [])parameters.get("changeLists");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.removeFromChangelists(new HashSet<String>(Arrays.asList(paths)), ConversionUtility.convertDepth(depth), changeLists == null ? null : Arrays.asList(changeLists));
			
			this.fireSucceeded(ISVNCallListener.REMOVE_FROM_CHANGE_LISTS, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.REMOVE_FROM_CHANGE_LISTS, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
    }
    
    public void dumpChangeLists(String[] changeLists, String rootPath, SVNDepth depth, ISVNChangeListCallback cb, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("changeLists", changeLists);
		parameters.put("rootPath", rootPath);
		parameters.put("depth", depth);
		parameters.put("cb", cb);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.DUMP_CHANGE_LISTS, parameters);
		changeLists = (String [])parameters.get("changeLists");
		rootPath = (String)parameters.get("rootPath");
		depth = (SVNDepth)parameters.get("depth");
		final ISVNChangeListCallback cb1 = (ISVNChangeListCallback)parameters.get("cb");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.getChangelists(rootPath, Arrays.asList(changeLists), ConversionUtility.convertDepth(depth), new org.apache.subversion.javahl.callback.ChangelistCallback() {
				public void doChangelist(String path, String changelist) {
					cb1.next(path, changelist);
				}
			});
			
			this.fireSucceeded(ISVNCallListener.DUMP_CHANGE_LISTS, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.DUMP_CHANGE_LISTS, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
    }

	public void importTo(String path, String url, String message, SVNDepth depth, long options, Map revProps, ISVNImportFilterCallback filter, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("url", url);
		parameters.put("message", message);
		parameters.put("depth", depth);
		parameters.put("options", Long.valueOf(options));
		parameters.put("revProps", revProps);
		parameters.put("filter", filter);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.IMPORT, parameters);
		path = (String)parameters.get("path");
		url = (String)parameters.get("url");
		message = (String)parameters.get("message");
		depth = (SVNDepth)parameters.get("depth");
		options = ((Long)parameters.get("options")).longValue();
		revProps = (Map)parameters.get("revProps");
		filter = (ISVNImportFilterCallback)parameters.get("filter");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.doImport(path, url, ConversionUtility.convertDepth(depth), (options & Options.INCLUDE_IGNORED) != 0, (options & Options.IGNORE_AUTOPROPS) != 0, (options & Options.IGNORE_UNKNOWN_NODE_TYPES) != 0, ConversionUtility.convertRevPropsToSVN(revProps), ConversionUtility.convert(filter), new CommitMessage(message), new CommitInfo(monitor));
			
			this.fireSucceeded(ISVNCallListener.IMPORT, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.IMPORT, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public long exportTo(SVNEntryRevisionReference fromReference, String destPath, String nativeEOL, SVNDepth depth, long options, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("fromReference", fromReference);
		parameters.put("destPath", destPath);
		parameters.put("nativeEOL", nativeEOL);
		parameters.put("depth", depth);
		parameters.put("options", Long.valueOf(options));
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.EXPORT, parameters);
		fromReference = (SVNEntryRevisionReference)parameters.get("fromReference");
		destPath = (String)parameters.get("destPath");
		nativeEOL = (String)parameters.get("nativeEOL");
		depth = (SVNDepth)parameters.get("depth");
		options = ((Long)parameters.get("options")).longValue();
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			long retVal = this.client.doExport(fromReference.path, destPath, ConversionUtility.convert(fromReference.revision), ConversionUtility.convert(fromReference.pegRevision), (options & Options.FORCE) != 0, (options & Options.IGNORE_EXTERNALS) != 0, ConversionUtility.convertDepth(depth), nativeEOL);
			
			this.fireSucceeded(ISVNCallListener.EXPORT, parameters, Long.valueOf(retVal));
			
			return retVal;
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.EXPORT, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
		// unreachable code
		return 0;
	}

	public void diffTwo(SVNEntryRevisionReference reference1, SVNEntryRevisionReference reference2, String relativeToDir, String fileName, SVNDepth depth, long options, String[] changeLists, long outputOptions, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("reference1", reference1);
		parameters.put("reference2", reference2);
		parameters.put("relativeToDir", relativeToDir);
		parameters.put("fileName", fileName);
		parameters.put("depth", depth);
		parameters.put("options", Long.valueOf(options));
		parameters.put("changeLists", changeLists);
		parameters.put("outputOptions", outputOptions);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.DIFF_TWO_FILE, parameters);
		reference1 = (SVNEntryRevisionReference)parameters.get("reference1");
		reference2 = (SVNEntryRevisionReference)parameters.get("reference2");
		relativeToDir = (String)parameters.get("relativeToDir");
		fileName = (String)parameters.get("fileName");
		depth = (SVNDepth)parameters.get("depth");
		options = ((Long)parameters.get("options")).longValue();
		changeLists = (String [])parameters.get("changeLists");
		outputOptions = ((Long)parameters.get("outputOptions")).longValue();
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();

			reference1 = SVNUtility.convertRevisionReference(this, reference1, monitor);
			reference2 = SVNUtility.convertRevisionReference(this, reference2, monitor);
			
			this.client.diff(reference1.path, ConversionUtility.convert(reference1.revision), reference2.path, ConversionUtility.convert(reference2.revision), relativeToDir, fileName, ConversionUtility.convertDepth(depth), changeLists == null ? null : Arrays.asList(changeLists), (options & Options.IGNORE_ANCESTRY) != 0, (options & Options.SKIP_DELETED) != 0, (options & Options.FORCE) != 0, (options & Options.COPIES_AS_ADDITIONS) != 0, (options & Options.IGNORE_PROPERTY_CHANGES) != 0, (options & Options.IGNORE_CONTENT_CHANGES) != 0, ConversionUtility.convertDiffOptions(outputOptions));
			
			this.fireSucceeded(ISVNCallListener.DIFF_TWO_FILE, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.DIFF_TWO_FILE, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public void diff(SVNEntryReference reference, SVNRevisionRange range, String relativeToDir, String fileName, SVNDepth depth, long options, String[] changeLists, long outputOptions, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("reference", reference);
		parameters.put("range", range);
		parameters.put("relativeToDir", relativeToDir);
		parameters.put("fileName", fileName);
		parameters.put("depth", depth);
		parameters.put("options", Long.valueOf(options));
		parameters.put("changeLists", changeLists);
		parameters.put("outputOptions", outputOptions);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.DIFF_FILE, parameters);
		reference = (SVNEntryReference)parameters.get("reference");
		range = (SVNRevisionRange)parameters.get("range");
		relativeToDir = (String)parameters.get("relativeToDir");
		fileName = (String)parameters.get("fileName");
		depth = (SVNDepth)parameters.get("depth");
		options = ((Long)parameters.get("options")).longValue();
		changeLists = (String [])parameters.get("changeLists");
		outputOptions = ((Long)parameters.get("outputOptions")).longValue();
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.diff(reference.path, ConversionUtility.convert(reference.pegRevision), ConversionUtility.convert(range.from), ConversionUtility.convert(range.to), relativeToDir, fileName, ConversionUtility.convertDepth(depth), changeLists == null ? null : Arrays.asList(changeLists), (options & Options.IGNORE_ANCESTRY) != 0, (options & Options.SKIP_DELETED) != 0, (options & Options.FORCE) != 0, (options & Options.COPIES_AS_ADDITIONS) != 0, (options & Options.IGNORE_PROPERTY_CHANGES) != 0, (options & Options.IGNORE_CONTENT_CHANGES) != 0, ConversionUtility.convertDiffOptions(outputOptions));
			
			this.fireSucceeded(ISVNCallListener.DIFF_FILE, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.DIFF_FILE, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}
	
	public void diffTwo(SVNEntryRevisionReference reference1, SVNEntryRevisionReference reference2, String relativeToDir, OutputStream stream, SVNDepth depth, long options, String[] changeLists, long outputOptions, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("reference1", reference1);
		parameters.put("reference2", reference2);
		parameters.put("relativeToDir", relativeToDir);
		parameters.put("stream", stream);
		parameters.put("depth", depth);
		parameters.put("options", Long.valueOf(options));
		parameters.put("changeLists", changeLists);
		parameters.put("outputOptions", Long.valueOf(outputOptions));
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.DIFF_TWO_STREAM, parameters);
		reference1 = (SVNEntryRevisionReference)parameters.get("reference1");
		reference2 = (SVNEntryRevisionReference)parameters.get("reference2");
		relativeToDir = (String)parameters.get("relativeToDir");
		stream = (OutputStream)parameters.get("stream");
		depth = (SVNDepth)parameters.get("depth");
		options = ((Long)parameters.get("options")).longValue();
		changeLists = (String [])parameters.get("changeLists");
		outputOptions = ((Long)parameters.get("outputOptions")).longValue();
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();

			reference1 = SVNUtility.convertRevisionReference(this, reference1, monitor);
			reference2 = SVNUtility.convertRevisionReference(this, reference2, monitor);
			
			this.client.diff(reference1.path, ConversionUtility.convert(reference1.revision), reference2.path, ConversionUtility.convert(reference2.revision), relativeToDir, stream, ConversionUtility.convertDepth(depth), changeLists == null ? null : Arrays.asList(changeLists), (options & Options.IGNORE_ANCESTRY) != 0, (options & Options.SKIP_DELETED) != 0, (options & Options.FORCE) != 0, (options & Options.COPIES_AS_ADDITIONS) != 0, (options & Options.IGNORE_PROPERTY_CHANGES) != 0, (options & Options.IGNORE_CONTENT_CHANGES) != 0, ConversionUtility.convertDiffOptions(outputOptions));
			
			this.fireSucceeded(ISVNCallListener.DIFF_TWO_STREAM, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.DIFF_TWO_STREAM, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public void diff(SVNEntryReference reference, SVNRevisionRange range, String relativeToDir, OutputStream stream, SVNDepth depth, long options, String[] changeLists, long outputOptions, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("reference", reference);
		parameters.put("range", range);
		parameters.put("relativeToDir", relativeToDir);
		parameters.put("stream", stream);
		parameters.put("depth", depth);
		parameters.put("options", Long.valueOf(options));
		parameters.put("changeLists", changeLists);
		parameters.put("outputOptions", Long.valueOf(outputOptions));
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.DIFF_STREAM, parameters);
		reference = (SVNEntryReference)parameters.get("reference");
		range = (SVNRevisionRange)parameters.get("range");
		relativeToDir = (String)parameters.get("relativeToDir");
		stream = (OutputStream)parameters.get("stream");
		depth = (SVNDepth)parameters.get("depth");
		options = ((Long)parameters.get("options")).longValue();
		changeLists = (String [])parameters.get("changeLists");
		outputOptions = ((Long)parameters.get("outputOptions")).longValue();
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.diff(reference.path, ConversionUtility.convert(reference.pegRevision), ConversionUtility.convert(range.from), ConversionUtility.convert(range.to), relativeToDir, stream, ConversionUtility.convertDepth(depth), changeLists == null ? null : Arrays.asList(changeLists), (options & Options.IGNORE_ANCESTRY) != 0, (options & Options.SKIP_DELETED) != 0, (options & Options.FORCE) != 0, (options & Options.COPIES_AS_ADDITIONS) != 0, (options & Options.IGNORE_PROPERTY_CHANGES) != 0, (options & Options.IGNORE_CONTENT_CHANGES) != 0, ConversionUtility.convertDiffOptions(outputOptions));
			
			this.fireSucceeded(ISVNCallListener.DIFF_STREAM, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.DIFF_STREAM, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}
	
	public void diffStatusTwo(SVNEntryRevisionReference reference1, SVNEntryRevisionReference reference2, SVNDepth depth, long options, String[] changeLists, ISVNDiffStatusCallback cb, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("reference1", reference1);
		parameters.put("reference2", reference2);
		parameters.put("depth", depth);
		parameters.put("options", Long.valueOf(options));
		parameters.put("changeLists", changeLists);
		parameters.put("cb", cb);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.DIFF_STATUS_TWO, parameters);
		reference1 = (SVNEntryRevisionReference)parameters.get("reference1");
		reference2 = (SVNEntryRevisionReference)parameters.get("reference2");
		depth = (SVNDepth)parameters.get("depth");
		options = ((Long)parameters.get("options")).longValue();
		changeLists = (String [])parameters.get("changeLists");
		cb = (ISVNDiffStatusCallback)parameters.get("cb");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		SVNEntryInfo []infos = SVNUtility.info(this, reference1, SVNDepth.EMPTY, monitor);
		boolean isFile = infos.length > 0 && infos[0] != null && infos[0].kind == SVNEntry.Kind.FILE;
		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();

			reference1 = SVNUtility.convertRevisionReference(this, reference1, monitor);
			reference2 = SVNUtility.convertRevisionReference(this, reference2, monitor);
			
			DiffCallback callback = new DiffCallback(reference1.path, reference2.path, isFile, cb);
			this.client.diffSummarize(reference1.path, ConversionUtility.convert(reference1.revision), reference2.path, ConversionUtility.convert(reference2.revision), ConversionUtility.convertDepth(depth), changeLists == null ? null : Arrays.asList(changeLists), (options & Options.IGNORE_ANCESTRY) != 0, callback);
			callback.doLastDiff();
			
			this.fireSucceeded(ISVNCallListener.DIFF_STATUS_TWO, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.DIFF_STATUS_TWO, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public void diffStatus(SVNEntryReference reference, SVNRevisionRange range, SVNDepth depth, long options, String[] changeLists, ISVNDiffStatusCallback cb, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("reference", reference);
		parameters.put("range", range);
		parameters.put("depth", depth);
		parameters.put("options", Long.valueOf(options));
		parameters.put("changeLists", changeLists);
		parameters.put("cb", cb);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.DIFF_STATUS, parameters);
		reference = (SVNEntryReference)parameters.get("reference");
		range = (SVNRevisionRange)parameters.get("range");
		depth = (SVNDepth)parameters.get("depth");
		options = ((Long)parameters.get("options")).longValue();
		changeLists = (String [])parameters.get("changeLists");
		cb = (ISVNDiffStatusCallback)parameters.get("cb");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		SVNEntryInfo []infos = SVNUtility.info(this, new SVNEntryRevisionReference(reference, range.from), SVNDepth.EMPTY, monitor);
		boolean isFile = infos.length > 0 && infos[0] != null && infos[0].kind == SVNEntry.Kind.FILE;
		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			
			DiffCallback callback = new DiffCallback(reference.path, reference.path, isFile, cb);
			this.client.diffSummarize(reference.path, ConversionUtility.convert(reference.pegRevision), ConversionUtility.convert(range.from), ConversionUtility.convert(range.to), ConversionUtility.convertDepth(depth), changeLists == null ? null : Arrays.asList(changeLists), (options & Options.IGNORE_ANCESTRY) != 0, callback);
			callback.doLastDiff();
			
			this.fireSucceeded(ISVNCallListener.DIFF_STATUS, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.DIFF_STATUS, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public void getInfo(SVNEntryRevisionReference reference, SVNDepth depth, long options, String []changeLists, ISVNEntryInfoCallback cb, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("reference", reference);
		parameters.put("depth", depth);
		parameters.put("options", Long.valueOf(options));
		parameters.put("changeLists", changeLists);
		parameters.put("cb", cb);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.GET_INFO, parameters);
		reference = (SVNEntryRevisionReference)parameters.get("reference");
		depth = (SVNDepth)parameters.get("depth");
		options = ((Long)parameters.get("options")).longValue();
		changeLists = (String [])parameters.get("changeLists");
		cb = (ISVNEntryInfoCallback)parameters.get("cb");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.info2(reference.path, ConversionUtility.convert(reference.revision), ConversionUtility.convert(reference.pegRevision), ConversionUtility.convertDepth(depth), changeLists == null ? null : Arrays.asList(changeLists), ConversionUtility.convert(cb));
			
			this.fireSucceeded(ISVNCallListener.GET_INFO, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.GET_INFO, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public SVNProperty []streamFileContent(SVNEntryRevisionReference reference, long options, OutputStream stream, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("reference", reference);
		parameters.put("options", Long.valueOf(options));
		parameters.put("stream", stream);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.STREAM_FILE_CONTENT, parameters);
		reference = (SVNEntryRevisionReference)parameters.get("reference");
		options = ((Long)parameters.get("options")).longValue();
		stream = (OutputStream)parameters.get("stream");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.streamFileContent(reference.path, ConversionUtility.convert(reference.revision), ConversionUtility.convert(reference.pegRevision), stream);
			
			this.fireSucceeded(ISVNCallListener.STREAM_FILE_CONTENT, parameters, null);
			return null;
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.STREAM_FILE_CONTENT, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
		// unreachable code
		return null;
	}
	
	public void mkdir(String []path, String message, long options, Map revProps, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("message", message);
		parameters.put("options", Long.valueOf(options));
		parameters.put("revProps", revProps);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.MKDIR, parameters);
		path = (String [])parameters.get("path");
		message = (String)parameters.get("message");
		options = ((Long)parameters.get("options")).longValue();
		revProps = (Map)parameters.get("revProps");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.mkdir(new HashSet<String>(Arrays.asList(path)), (options & Options.INCLUDE_PARENTS) != 0, ConversionUtility.convertRevPropsToSVN(revProps), new CommitMessage(message), new CommitInfo(monitor));
			
			this.fireSucceeded(ISVNCallListener.MKDIR, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.MKDIR, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public void moveLocal(String[] srcPaths, String dstPath, long options, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("srcPaths", srcPaths);
		parameters.put("dstPath", dstPath);
		parameters.put("options", Long.valueOf(options));
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.MOVE_LOCAL, parameters);
		srcPaths = (String [])parameters.get("srcPaths");
		dstPath = (String)parameters.get("dstPath");
		options = ((Long)parameters.get("options")).longValue();
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.move(new HashSet<String>(Arrays.asList(srcPaths)), dstPath, (options & Options.FORCE) != 0, true, false, (options & Options.METADATA_ONLY) != 0, (options & Options.ALLOW_MIXED_REVISIONS) != 0, null, null, null);
			
			this.fireSucceeded(ISVNCallListener.MOVE_LOCAL, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.MOVE_LOCAL, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public void moveRemote(String[] srcPaths, String dstPath, String message, long options, Map revProps, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("srcPaths", srcPaths);
		parameters.put("dstPath", dstPath);
		parameters.put("message", message);
		parameters.put("options", Long.valueOf(options));
		parameters.put("revProps", revProps);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.MOVE_REMOTE, parameters);
		srcPaths = (String[])parameters.get("srcPaths");
		dstPath = (String)parameters.get("dstPath");
		message = (String)parameters.get("message");
		options = ((Long)parameters.get("options")).longValue();
		revProps = (Map)parameters.get("revProps");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.move(new HashSet<String>(Arrays.asList(srcPaths)), dstPath, (options & Options.FORCE) != 0, (options & Options.INTERPRET_AS_CHILD) != 0, (options & Options.INCLUDE_PARENTS) != 0, false, true, ConversionUtility.convertRevPropsToSVN(revProps), new CommitMessage(message), new CommitInfo(monitor));
			
			this.fireSucceeded(ISVNCallListener.MOVE_REMOTE, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.MOVE_REMOTE, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public void copyLocal(SVNEntryRevisionReference []srcPaths, String destPath, long options, Map<String, List<SVNExternalReference>> externalsToPin, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("srcPaths", srcPaths);
		parameters.put("destPath", destPath);
		parameters.put("options", Long.valueOf(options));
		parameters.put("externalsToPin", externalsToPin);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.COPY_LOCAL, parameters);
		srcPaths = (SVNEntryRevisionReference [])parameters.get("srcPaths");
		destPath = (String)parameters.get("destPath");
		options = ((Long)parameters.get("options")).longValue();
		externalsToPin = (Map<String, List<SVNExternalReference>>)parameters.get("externalsToPin");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.copy(Arrays.asList(ConversionUtility.convert(srcPaths)), destPath, true, false, (options & Options.IGNORE_EXTERNALS) != 0, null, null, null);
			
			this.fireSucceeded(ISVNCallListener.COPY_LOCAL, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.COPY_LOCAL, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}
	
	public void copyRemote(SVNEntryRevisionReference []srcPaths, String destPath, String message, long options, Map revProps, Map<String, List<SVNExternalReference>> externalsToPin, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("srcPaths", srcPaths);
		parameters.put("destPath", destPath);
		parameters.put("message", message);
		parameters.put("options", Long.valueOf(options));
		parameters.put("revProps", revProps);
		parameters.put("externalsToPin", externalsToPin);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.COPY_REMOTE, parameters);
		srcPaths = (SVNEntryRevisionReference[])parameters.get("srcPaths");
		destPath = (String)parameters.get("destPath");
		message = (String)parameters.get("message");
		options = ((Long)parameters.get("options")).longValue();
		revProps = (Map)parameters.get("revProps");
		externalsToPin = (Map<String, List<SVNExternalReference>>)parameters.get("externalsToPin");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.copy(Arrays.asList(ConversionUtility.convert(srcPaths)), destPath, (options & Options.INTERPRET_AS_CHILD) != 0, (options & Options.INCLUDE_PARENTS) != 0, false, ConversionUtility.convertRevPropsToSVN(revProps), new CommitMessage(message), new CommitInfo(monitor));
			
			this.fireSucceeded(ISVNCallListener.COPY_REMOTE, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.COPY_REMOTE, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}
	
	public void removeRemote(String []path, String message, long options, Map revProps, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("message", message);
		parameters.put("options", Long.valueOf(options));
		parameters.put("revProps", revProps);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.REMOVE_REMOTE, parameters);
		path = (String[])parameters.get("path");
		message = (String)parameters.get("message");
		options = ((Long)parameters.get("options")).longValue();
		revProps = (Map)parameters.get("revProps");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.remove(new HashSet<String>(Arrays.asList(path)), (options & Options.FORCE) != 0, false, ConversionUtility.convertRevPropsToSVN(revProps), new CommitMessage(message), new CommitInfo(monitor));
			
			this.fireSucceeded(ISVNCallListener.REMOVE_REMOTE, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.REMOVE_REMOTE, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}
	
	public void removeLocal(String []path, long options, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("options", Long.valueOf(options));
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.REMOVE_LOCAL, parameters);
		path = (String[])parameters.get("path");
		options = ((Long)parameters.get("options")).longValue();
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.remove(new HashSet<String>(Arrays.asList(path)), (options & Options.FORCE) != 0, (options & Options.KEEP_LOCAL) != 0, null, null, null);
			
			this.fireSucceeded(ISVNCallListener.REMOVE_LOCAL, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.REMOVE_LOCAL, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public void listHistoryLog(SVNEntryReference reference, SVNRevisionRange []revisionRanges, String[] revProps, long limit, long options, ISVNLogEntryCallback cb, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("reference", reference);
		parameters.put("revisionRanges", revisionRanges);
		parameters.put("revProps", revProps);
		parameters.put("limit", Long.valueOf(limit));
		parameters.put("options", Long.valueOf(options));
		parameters.put("cb", cb);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.LIST_HISTORY_LOG, parameters);
		reference = (SVNEntryReference)parameters.get("reference");
		revisionRanges = (SVNRevisionRange[])parameters.get("revisionRanges");
		revProps = (String[])parameters.get("revProps");
		limit = ((Long)parameters.get("limit")).longValue();
		options = ((Long)parameters.get("options")).longValue();
		cb = (ISVNLogEntryCallback)parameters.get("cb");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.logMessages(reference.path, ConversionUtility.convert(reference.pegRevision), Arrays.asList(ConversionUtility.convert(revisionRanges)), (options & Options.STOP_ON_COPY) != 0, (options & Options.DISCOVER_PATHS) != 0, (options & Options.INCLUDE_MERGED_REVISIONS) != 0, new HashSet<String>(Arrays.asList(revProps)), limit, ConversionUtility.convert(cb));
			
			this.fireSucceeded(ISVNCallListener.LIST_HISTORY_LOG, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.LIST_HISTORY_LOG, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public void annotate(SVNEntryReference reference, SVNRevisionRange revisionRange, long options, long diffOptions, ISVNAnnotationCallback callback, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("reference", reference);
		parameters.put("revisionRange", revisionRange);
		parameters.put("options", Long.valueOf(options));
		parameters.put("diffOptions", Long.valueOf(diffOptions));
		parameters.put("callback", callback);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.ANNOTATE, parameters);
		reference = (SVNEntryReference)parameters.get("reference");
		revisionRange = (SVNRevisionRange)parameters.get("revisionRange");
		options = ((Long)parameters.get("options")).longValue();
		diffOptions = ((Long)parameters.get("diffOptions")).longValue();
		callback = (ISVNAnnotationCallback)parameters.get("callback");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.blame(reference.path, ConversionUtility.convert(reference.pegRevision), ConversionUtility.convert(revisionRange.from), ConversionUtility.convert(revisionRange.to), (options & Options.IGNORE_MIME_TYPE) != 0, (options & Options.INCLUDE_MERGED_REVISIONS) != 0, ConversionUtility.convert(callback));
			
			this.fireSucceeded(ISVNCallListener.ANNOTATE, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.ANNOTATE, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public void listEntries(SVNEntryRevisionReference reference, SVNDepth depth, int direntFields, long options, ISVNEntryCallback cb, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("reference", reference);
		parameters.put("depth", depth);
		parameters.put("direntFields", Integer.valueOf(direntFields));
		parameters.put("options", Long.valueOf(options));
		parameters.put("cb", cb);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.LIST, parameters);
		reference = (SVNEntryRevisionReference)parameters.get("reference");
		depth = (SVNDepth)parameters.get("depth");
		direntFields = ((Integer)parameters.get("direntFields")).intValue();
		options = ((Long)parameters.get("options")).longValue();
		final ISVNEntryCallback cb1 = (ISVNEntryCallback)parameters.get("cb");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.list(reference.path, ConversionUtility.convert(reference.revision), ConversionUtility.convert(reference.pegRevision), ConversionUtility.convertDepth(depth), direntFields, (options & Options.FETCH_LOCKS) != 0, new org.apache.subversion.javahl.callback.ListCallback() {
				public void doEntry(org.apache.subversion.javahl.types.DirEntry dirent, org.apache.subversion.javahl.types.Lock lock) {
					String path = dirent.getPath();
					if (path != null && path.length() != 0 || dirent.getNodeKind() == org.apache.subversion.javahl.types.NodeKind.file) {
						Date date = dirent.getLastChanged();
						cb1.next(new SVNEntry(path, dirent.getLastChangedRevisionNumber(), date == null ? 0 : date.getTime(), dirent.getLastAuthor(), dirent.getHasProps(), ConversionUtility.convert(dirent.getNodeKind()), dirent.getSize(), ConversionUtility.convert(lock)));
					}
				}
			});
			
			this.fireSucceeded(ISVNCallListener.LIST, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.LIST, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public void listProperties(SVNEntryRevisionReference reference, SVNDepth depth, String[] changeLists, long options, ISVNPropertyCallback callback, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("reference", reference);
		parameters.put("depth", depth);
		parameters.put("changeLists", changeLists);
		parameters.put("callback", callback);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.GET_PROPERTIES, parameters);
		reference = (SVNEntryRevisionReference)parameters.get("reference");
		depth = (SVNDepth)parameters.get("depth");
		changeLists = (String [])parameters.get("changeLists");
		callback = (ISVNPropertyCallback)parameters.get("callback");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			if ((options & Options.INHERIT_PROPERTIES) != 0) {
				this.client.properties(reference.path, ConversionUtility.convert(reference.revision), ConversionUtility.convert(reference.pegRevision), ConversionUtility.convertDepth(depth), changeLists == null ? null : Arrays.asList(changeLists), ConversionUtility.convertInherited(callback));
			}
			else {
				this.client.properties(reference.path, ConversionUtility.convert(reference.revision), ConversionUtility.convert(reference.pegRevision), ConversionUtility.convertDepth(depth), changeLists == null ? null : Arrays.asList(changeLists), ConversionUtility.convert(callback));
			}
			
			this.fireSucceeded(ISVNCallListener.GET_PROPERTIES, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.GET_PROPERTIES, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}
	
	public SVNProperty getProperty(SVNEntryRevisionReference reference, String name, String[] changeLists, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("reference", reference);
		parameters.put("name", name);
		parameters.put("changeLists", changeLists);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.GET_PROPERTY, parameters);
		reference = (SVNEntryRevisionReference)parameters.get("reference");
		name = (String)parameters.get("name");
		changeLists = (String [])parameters.get("changeLists");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			byte []data = this.client.propertyGet(reference.path, name, ConversionUtility.convert(reference.revision), ConversionUtility.convert(reference.pegRevision), changeLists == null ? null : Arrays.asList(changeLists));
			SVNProperty retVal = data != null ? new SVNProperty(name, data) : null;
			
			this.fireSucceeded(ISVNCallListener.GET_PROPERTY, parameters, retVal);
			
			return retVal;
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.GET_PROPERTY, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
		// unreachable code
		return null;
	}

	public void setPropertyLocal(String []path, SVNProperty property, SVNDepth depth, long options, String[] changeLists, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("property", property);
		parameters.put("depth", depth);
		parameters.put("options", Long.valueOf(options));
		parameters.put("changeLists", changeLists);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.SET_PROPERTY_LOCAL, parameters);
		path = (String [])parameters.get("path");
		property = (SVNProperty)parameters.get("property");
		depth = (SVNDepth)parameters.get("depth");
		options = ((Long)parameters.get("options")).longValue();
		changeLists = (String [])parameters.get("changeLists");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.propertySetLocal(new HashSet<String>(Arrays.asList(path)), property.name, property.binValue, ConversionUtility.convertDepth(depth), changeLists == null ? null : Arrays.asList(changeLists), (options & Options.FORCE) != 0);
			
			this.fireSucceeded(ISVNCallListener.SET_PROPERTY_LOCAL, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.SET_PROPERTY_LOCAL, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}
	
	public void setPropertyRemote(SVNEntryReference reference, SVNProperty property, String message, long options, Map revProps, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("reference", reference);
		parameters.put("property", property);
		parameters.put("message", message);
		parameters.put("options", Long.valueOf(options));
		parameters.put("revProps", revProps);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.SET_PROPERTY_REMOTE, parameters);
		reference = (SVNEntryReference)parameters.get("reference");
		property = (SVNProperty)parameters.get("property");
		message = (String)parameters.get("message");
		options = ((Long)parameters.get("options")).longValue();
		revProps = (Map)parameters.get("revProps");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.propertySetRemote(reference.path, ((SVNRevision.Number)reference.pegRevision).getNumber(), property.name, property.binValue, new CommitMessage(message), (options & Options.FORCE) != 0, ConversionUtility.convertRevPropsToSVN(revProps), new CommitInfo(monitor));
			
			this.fireSucceeded(ISVNCallListener.SET_PROPERTY_REMOTE, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.SET_PROPERTY_REMOTE, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public SVNProperty []listRevisionProperties(SVNEntryReference reference, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("reference", reference);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.LIST_REVISION_PROPERTIES, parameters);
		reference = (SVNEntryReference)parameters.get("reference");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			
			SVNProperty []retVal = ConversionUtility.convertRevProps(this.client.revProperties(reference.path, ConversionUtility.convert(reference.pegRevision)));
			
			this.fireSucceeded(ISVNCallListener.LIST_REVISION_PROPERTIES, parameters, retVal);
			
			return retVal;
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.LIST_REVISION_PROPERTIES, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
		// unreachable code
		return null;
	}

	public SVNProperty getRevisionProperty(SVNEntryReference reference, String name, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("reference", reference);
		parameters.put("name", name);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.GET_REVISION_PROPERTY, parameters);
		reference = (SVNEntryReference)parameters.get("reference");
		name = (String)parameters.get("name");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			SVNProperty retVal = new SVNProperty(name, this.client.revProperty(reference.path, name, ConversionUtility.convert(reference.pegRevision)));
			
			this.fireSucceeded(ISVNCallListener.GET_REVISION_PROPERTY, parameters, retVal);
			
			return retVal;
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.GET_REVISION_PROPERTY, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
		// unreachable code
		return null;
	}

	public void setRevisionProperty(SVNEntryReference reference, SVNProperty property, String originalValue, long options, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("reference", reference);
		parameters.put("property", property);
		parameters.put("originalValue", originalValue);
		parameters.put("options", Long.valueOf(options));
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.SET_REVISION_PROPERTY, parameters);
		reference = (SVNEntryReference)parameters.get("reference");
		property = (SVNProperty)parameters.get("property");
		originalValue = (String)parameters.get("originalValue");
		options = ((Long)parameters.get("options")).longValue();
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			this.client.setRevProperty(reference.path, property.name, ConversionUtility.convert(reference.pegRevision), property.value, originalValue, (options & Options.FORCE) != 0);
			
			this.fireSucceeded(ISVNCallListener.SET_REVISION_PROPERTY, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.SET_REVISION_PROPERTY, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}
	
	public void upgrade(String path, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.UPGRADE, parameters);
		path = (String)parameters.get("path");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			
			this.client.upgrade(path);
			
			this.fireSucceeded(ISVNCallListener.UPGRADE, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.UPGRADE, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}
	
	public void patch(String patchPath, String targetPath, int stripCount, long options, ISVNPatchCallback callback, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("patchPath", patchPath);
		parameters.put("targetPath", targetPath);
		parameters.put("stripCount", Integer.valueOf(stripCount));
		parameters.put("options", Long.valueOf(options));
		parameters.put("callback", callback);
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.PATCH, parameters);
		patchPath = (String)parameters.get("patchPath");
		targetPath = (String)parameters.get("targetPath");
		stripCount = ((Integer)parameters.get("stripCount")).intValue();
		options = ((Long)parameters.get("options")).longValue();
		callback = (ISVNPatchCallback)parameters.get("callback");
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
		try {
			this.composite.add(wrapper);
			wrapper.start();
			
			this.client.patch(patchPath, targetPath, (options & Options.SIMULATE) != 0, stripCount, (options & Options.REVERSE) != 0, (options & Options.IGNORE_WHITESPACE) != 0, (options & Options.REMOVE_TEMPORARY_FILES) != 0, ConversionUtility.convert(callback));
			
			this.fireSucceeded(ISVNCallListener.PATCH, parameters, null);
		}
		catch (ClientException ex) {
			this.handleClientException(ex, ISVNCallListener.PATCH, parameters);
		}
		finally {
			wrapper.finish();
			this.composite.remove(wrapper);
		}
	}

	public void vacuum(String path, long options, ISVNProgressMonitor monitor) throws SVNConnectorException {
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("path", path);
		parameters.put("options", Long.valueOf(options));
		parameters.put("monitor", monitor);
		this.fireAsked(ISVNCallListener.VACUUM, parameters);
		path = (String)parameters.get("path");
		options = ((Long)parameters.get("options")).longValue();
		monitor = (ISVNProgressMonitor)parameters.get("monitor");

//		ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor);
//		try {
//			this.composite.add(wrapper);
//			wrapper.start();
			
			//this.client.vacuum(path, ...);
			
			this.fireSucceeded(ISVNCallListener.VACUUM, parameters, null);
//		}
//		catch (ClientException ex) {
//			this.handleClientException(ex, ISVNCallListener.VACUUM, parameters);
//		}
//		finally {
//			wrapper.finish();
//			this.composite.remove(wrapper);
//		}
	}
	
	public void dispose() {
		this.client.dispose();
	}
	
	protected void cancelOperation() throws Exception {
		this.client.cancelOperation();
	}

	private class DiffCallback implements org.apache.subversion.javahl.callback.DiffSummaryCallback {
		private String prev;
		private String next;
		private boolean isFile;
		private SVNDiffStatus savedDiff;
		private ISVNDiffStatusCallback cb;
		
		public DiffCallback(String prev, String next, boolean isFile, ISVNDiffStatusCallback cb) {
			this.prev = SVNUtility.decodeURL(prev);
			this.next = SVNUtility.decodeURL(next);
			this.isFile = isFile;
			this.cb = cb;
		}
		
		public void onSummary(org.apache.subversion.javahl.DiffSummary descriptor) {
			org.eclipse.team.svn.core.connector.SVNEntryStatus.Kind changeType = org.eclipse.team.svn.core.connector.SVNEntryStatus.Kind.NORMAL;
			if (descriptor.getDiffKind() == org.apache.subversion.javahl.DiffSummary.DiffKind.added) {
				changeType = org.eclipse.team.svn.core.connector.SVNEntryStatus.Kind.ADDED;
			}
			else if (descriptor.getDiffKind() == org.apache.subversion.javahl.DiffSummary.DiffKind.deleted) {
				changeType = org.eclipse.team.svn.core.connector.SVNEntryStatus.Kind.DELETED;
			}
			else if (descriptor.getDiffKind() == org.apache.subversion.javahl.DiffSummary.DiffKind.modified) {
				changeType = org.eclipse.team.svn.core.connector.SVNEntryStatus.Kind.MODIFIED;
			}
			org.eclipse.team.svn.core.connector.SVNEntryStatus.Kind propChangeType = descriptor.propsChanged() ? org.eclipse.team.svn.core.connector.SVNEntryStatus.Kind.MODIFIED : org.eclipse.team.svn.core.connector.SVNEntryStatus.Kind.NORMAL;
			if (changeType != org.eclipse.team.svn.core.connector.SVNEntryStatus.Kind.NORMAL || propChangeType != org.eclipse.team.svn.core.connector.SVNEntryStatus.Kind.NORMAL) {
				String tPath1 = descriptor.getPath();
				String tPath2 = tPath1;
				if (tPath1.length() == 0 || this.isFile) {
					tPath1 = this.prev;
					tPath2 = this.next;
				}
				else {
					// XXX LINA quick-n-dirty hack
					tPath1 = collapsePaths(this.prev, tPath1);
					tPath2 = collapsePaths(this.next, tPath2);
				}
				SVNDiffStatus status = new SVNDiffStatus(SVNUtility.encodeURL(tPath1), SVNUtility.encodeURL(tPath2), ConversionUtility.convert(descriptor.getNodeKind()), changeType, propChangeType);
				if (this.savedDiff != null) {
					if (this.savedDiff.pathPrev.equals(status.pathPrev) && this.savedDiff.pathNext.equals(status.pathNext) && 
						this.savedDiff.textStatus == org.eclipse.team.svn.core.connector.SVNEntryStatus.Kind.DELETED &&
						status.textStatus == org.eclipse.team.svn.core.connector.SVNEntryStatus.Kind.ADDED) {
						this.savedDiff = new SVNDiffStatus(SVNUtility.encodeURL(tPath1), SVNUtility.encodeURL(tPath2), ConversionUtility.convert(descriptor.getNodeKind()), org.eclipse.team.svn.core.connector.SVNEntryStatus.Kind.REPLACED, org.eclipse.team.svn.core.connector.SVNEntryStatus.Kind.NORMAL);
						status = null;
					}
					this.cb.next(this.savedDiff);
				}
				this.savedDiff = status;
			}
		}
		
		public void doLastDiff() {
			if (this.savedDiff != null) {
				this.cb.next(this.savedDiff);
			}
		}
	}
	
	/*
	 *  XXX LINA quick-n-dirty hack
	 */
	private static String collapsePaths(String lPath, String rPath) {
		String res = "";
		if (basename(lPath).equals(rootdirname(rPath))) {
			res = dirname(lPath) + "/" + rPath;
		} else {
			res = lPath + "/" + rPath;
		}
		//System.out.format("'%s' + '%s' = '%s' %n", lPath, rPath, res);
		return res;
	}
	
	private static String basename(String path) {
		int lastSepIndex = path.lastIndexOf('/');
		if (lastSepIndex != -1) {
			return path.substring(lastSepIndex + 1);
		} else {
			return path;
		}
	}
	
	private static String dirname(String path) {
		int lastSepIndex = path.lastIndexOf('/');
		if (lastSepIndex != -1) {
			return path.substring(0, lastSepIndex);
		} else {
			return path;
		}
	}
	
	private static String rootdirname(String path) {
		int firstSepIndex = path.indexOf('/');
		if (firstSepIndex != -1) {
			return path.substring(0, firstSepIndex);
		} else {
			return path;
		}
	}

	protected class CommitMessage implements org.apache.subversion.javahl.callback.CommitMessageCallback {
		private String message;
		
		public CommitMessage(String message) {
			this.message = message == null ? "" : message;
		}
		
		public String getLogMessage(Set<org.apache.subversion.javahl.CommitItem> elementsToBeCommitted) {
			return this.message;
		}
	}
	
	protected class CommitInfo implements org.apache.subversion.javahl.callback.CommitCallback {
		public org.apache.subversion.javahl.CommitInfo info;
		private ISVNProgressMonitor monitor;
		
		public CommitInfo(ISVNProgressMonitor monitor) {
			this.monitor = monitor;
		}
		
		public void commitInfo(org.apache.subversion.javahl.CommitInfo info) {
			this.info = info;
			this.monitor.commitStatus(ConversionUtility.convert(info));
		}
	}
	
	protected class RepositoryInfoPrompt implements UserPasswordSSHCallback, UserPasswordSSLCallback {
		protected ISVNCredentialsPrompt prompt;
		
		public RepositoryInfoPrompt(ISVNCredentialsPrompt prompt) {
			this.prompt = prompt;
		}

	    public boolean prompt(String realm, String username) {
	        return this.prompt.prompt(null, realm);
		}
		
	    public boolean prompt(String realm, String username, boolean maySave) {
	        return this.prompt.prompt(null, realm);
		}

	    public int askTrustSSLServer(String info, boolean allowPermanently) {
	    	SSLServerCertificateInfo certInfo;
			try {
				certInfo = SVNUtility.decodeCertificateData(SVNUtility.splitCertificateString(info));
			} 
			catch (ParseException e) {
				certInfo = new SSLServerCertificateInfo("", "", 0l, 0l, new byte[0], Arrays.asList(new String[] {""}), null);
			}
	    	return this.prompt.askTrustSSLServer(null, new SSLServerCertificateFailures(SSLServerCertificateFailures.OTHER), certInfo, allowPermanently).id;
		}

	    public String getUsername() {
	        return this.prompt.getUsername();
	    }
	    
	    public String getPassword() {
	        return this.prompt.getPassword();
	    }
	    
	    public boolean askYesNo(String realm, String question, boolean yesIsDefault) {
	        return true;
	    }
	    
	    public String askQuestion(String realm, String question, boolean showAnswer, boolean maySave) {
	    	return null;
	    }
	    
	    public String askQuestion(String realm, String question, boolean showAnswer) {
	        return null;
	    }
	    
	    public boolean userAllowedSave() {
	    	return false;
	    }

		public boolean promptSSL(String realm, boolean maySave) {
			return this.prompt.promptSSL(null, realm);
		}

		public String getSSLClientCertPath() {
			return this.prompt.getSSLClientCertPath();
		}

		public String getSSLClientCertPassword() {
			return this.prompt.getSSLClientCertPassword();
		}

		public boolean promptSSH(String realm, String username, int sshPort, boolean maySave) {
			return this.prompt.promptSSH(null, realm);
		}

		public String getSSHPrivateKeyPath() {
			return this.prompt.getSSHPrivateKeyPath();
		}

		public String getSSHPrivateKeyPassphrase() {
			return this.prompt.getSSHPrivateKeyPassphrase();
		}

		public int getSSHPort() {
			return this.prompt.getSSHPort();
		}
	    
	}
	
}
