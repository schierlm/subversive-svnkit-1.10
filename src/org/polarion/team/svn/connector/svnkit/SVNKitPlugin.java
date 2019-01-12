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

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.team.svn.core.utility.FileUtility;

/**
 * The main plugin class to be used in the desktop.
 * 
 * @author Alexander Gurov
 */
public class SVNKitPlugin extends Plugin {
	private static SVNKitPlugin plugin;
	
	public SVNKitPlugin() {
		super();
		SVNKitPlugin.plugin = this;
		if (System.getProperty("svnkit.library.gnome-keyring.enabled") == null) // allows to redefine if required
		{
			System.setProperty("svnkit.library.gnome-keyring.enabled", "false");
		}
		// Workaround for bug #323418
		if (System.getProperty("svnkit.http.methods") == null) // allows to redefine if required
		{
			System.setProperty("svnkit.http.methods", "Basic");
		}
	}

    public String getResource(String key) {
        return FileUtility.getResource(Platform.getResourceBundle(this.getBundle()), key);
    }
    
    public String getVersionString() {
        return (String)this.getBundle().getHeaders().get(org.osgi.framework.Constants.BUNDLE_VERSION);
    }
    
	public static SVNKitPlugin instance() {
		return SVNKitPlugin.plugin;
	}

}
