/*******************************************************************************
 * Copyright (c) 2005-2008 Polarion Software.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Alexander Gurov - Initial API and implementation
 *******************************************************************************/

package org.polarion.team.svn.connector.svnkit;

import org.eclipse.team.svn.core.connector.ISVNConnector;
import org.eclipse.team.svn.core.connector.ISVNManager;
import org.eclipse.team.svn.core.extension.factory.ISVNConnectorFactory;
import org.tmatesoft.svn.core.javahl.SVNClientImpl;
import org.tmatesoft.svn.util.Version;

/**
 * Default implementation. Works with SVN Kit SVN connector.
 * 
 * @author <A HREF="www.polarion.org">Alexander Gurov</A>, POLARION.ORG
 * 
 * @version $Revision: $ $Date: $
 */
public class SVNKitConnectorFactory implements ISVNConnectorFactory {
	public static final String CLIENT_ID = "org.eclipse.team.svn.connector.svnkit110";
	
	public ISVNConnector createConnector() {
		return new SVNKitConnector();
	}

	public ISVNManager createManager() {
		return new SVNKitManager();
	}
	
	public String getName() {
		String format = "%1$s %2$s %3$s (SVN %4$s compatible, all platforms)";
		return String.format(format, SVNKitPlugin.instance().getResource("ClientName"), Version.getShortVersionString(), Version.getRevisionString(), Version.getSVNVersion());	
	}
	
	public String getId() {
		return SVNKitConnectorFactory.CLIENT_ID;
	}

	public String getClientVersion() {
		return SVNClientImpl.version();
	}

	public String getVersion() {
		return SVNKitPlugin.instance().getVersionString();
	}
	
	public String getCompatibilityVersion() {
		return "4.0.0.I20160427-1700";
	}
	
	public int getSupportedFeatures() {
		return OptionalFeatures.SSH_SETTINGS | OptionalFeatures.PROXY_SETTINGS | OptionalFeatures.ATOMIC_X_COMMIT | OptionalFeatures.CREATE_REPOSITORY_FSFS;
	}
	
	public int getSVNAPIVersion() {
		return APICompatibility.SVNAPI_1_8_x;
	}

	public String toString() {
		return this.getId();
	}

}
