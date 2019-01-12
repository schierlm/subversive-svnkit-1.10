/*
 * Created on 26.06.2005
 */
package org.polarion.team.svn.connector.svnkit;

import java.io.File;

import org.tmatesoft.svn.core.internal.wc2.compat.SvnCodec;
import org.tmatesoft.svn.core.javahl17.SVNClientImpl;
import org.tmatesoft.svn.core.wc.DefaultSVNCommitParameters;
import org.tmatesoft.svn.core.wc2.ISvnCommitParameters;


public class SVNClientEx17 extends SVNClientImpl {
    private boolean myIsCommitMissingFiles;
    private String myProxyPassword;
    private String myProxyUserName;
    private int myProxyPort;
    private String myProxyHost;
    private int mySSHPort;
    private String mySSHUserName;
    private String mySSHPassphrase;
    private String mySSHKeyPath;
    private String mySSLPassphrase;
    private String mySSLCertPath;
    private String mySSH2Password;
    private String mySSH2UserName;
    private int mySSH2Port;
    protected boolean myIsStoreSSL, credentialsCacheEnabled;
    protected ISvnCommitParameters commitParams;

    public SVNClientEx17() {
        super(null);
    }
    
    public void setSSLCertificateCacheEnabled(boolean enabled) {
        if (enabled != isSSLCertificateCacheEnabled()) {
            myIsStoreSSL = enabled;
            // TODO updateSvnOperationsFactory();
        }
    }

    public boolean isSSLCertificateCacheEnabled() {
        return myIsStoreSSL;
    }

    public boolean isCredentialsCacheEnabled() {
        return credentialsCacheEnabled;
    }    

    public void setCredentialsCacheEnabled(boolean cacheCredentials) {
        if (cacheCredentials != isCredentialsCacheEnabled()) {
        	credentialsCacheEnabled = cacheCredentials;            
        	// TODO updateSvnOperationsFactory();
        }
        return;
    }
    
    public void setCommitMissedFiles(boolean commitMissingFiles) {
        myIsCommitMissingFiles = commitMissingFiles;
        commitParams = SvnCodec.commitParameters(new DefaultSVNCommitParameters() {
            public Action onMissingFile(File file) {
                return myIsCommitMissingFiles ? DELETE : SKIP;
            }
            public Action onMissingDirectory(File file) {
                return myIsCommitMissingFiles ? DELETE : ERROR;
            }
        });
    }
    
    public boolean isCommitMissingFile() {
        return myIsCommitMissingFiles;
    }

    public void setClientSSLCertificate(String certPath, String passphrase) {
        mySSLCertPath = certPath;
        mySSLPassphrase = passphrase;
        // TODO updateSvnOperationsFactory();
    }

    public void setSSHCredentials(String userName, String privateKeyPath, String passphrase, int port) {
        mySSHKeyPath = privateKeyPath;
        mySSHPassphrase = passphrase;
        mySSHUserName = userName;
        mySSHPort = port;
        //TODO updateSvnOperationsFactory();
    }

    public void setSSHCredentials(String userName, String password, int port) {
        mySSH2Password = password;
        mySSH2UserName = userName;
        mySSH2Port = port;
        // TODO updateSvnOperationsFactory();
    }

    public void setProxy(String host, int port, String userName, String password) {
        myProxyHost = host;
        myProxyPort = port;
        myProxyUserName = userName;
        myProxyPassword = password;
        // TODO updateSvnOperationsFactory();
    }
}
