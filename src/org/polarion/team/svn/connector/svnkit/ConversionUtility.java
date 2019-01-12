/*******************************************************************************
 * Copyright (c) 2005-2008 Polarion Software.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Alexander Gurov (Polarion Software) - initial API and implementation
 *******************************************************************************/

package org.polarion.team.svn.connector.svnkit;

import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.apache.subversion.javahl.ClientException;
import org.apache.subversion.javahl.CommitInfo;
import org.apache.subversion.javahl.ISVNRepos.MessageReceiver;
import org.apache.subversion.javahl.ReposNotifyInformation;
import org.apache.subversion.javahl.SubversionException;
import org.apache.subversion.javahl.callback.ImportFilterCallback;
import org.apache.subversion.javahl.callback.ReposFreezeAction;
import org.apache.subversion.javahl.callback.ReposNotifyCallback;
import org.apache.subversion.javahl.types.Tristate;
import org.eclipse.team.svn.core.connector.ISVNAnnotationCallback;
import org.eclipse.team.svn.core.connector.ISVNConflictResolutionCallback;
import org.eclipse.team.svn.core.connector.ISVNConnector.DiffOptions;
import org.eclipse.team.svn.core.connector.ISVNConnector.Options;
import org.eclipse.team.svn.core.connector.ISVNEntryInfoCallback;
import org.eclipse.team.svn.core.connector.ISVNEntryStatusCallback;
import org.eclipse.team.svn.core.connector.ISVNImportFilterCallback;
import org.eclipse.team.svn.core.connector.ISVNLogEntryCallback;
import org.eclipse.team.svn.core.connector.ISVNNotificationCallback;
import org.eclipse.team.svn.core.connector.ISVNPatchCallback;
import org.eclipse.team.svn.core.connector.ISVNPropertyCallback;
import org.eclipse.team.svn.core.connector.ISVNRepositoryFreezeAction;
import org.eclipse.team.svn.core.connector.ISVNRepositoryMessageCallback;
import org.eclipse.team.svn.core.connector.ISVNRepositoryNotificationCallback;
import org.eclipse.team.svn.core.connector.SVNAnnotationData;
import org.eclipse.team.svn.core.connector.SVNChangeStatus;
import org.eclipse.team.svn.core.connector.SVNChecksum;
import org.eclipse.team.svn.core.connector.SVNCommitStatus;
import org.eclipse.team.svn.core.connector.SVNConflictDescriptor;
import org.eclipse.team.svn.core.connector.SVNConflictResolution;
import org.eclipse.team.svn.core.connector.SVNConflictVersion;
import org.eclipse.team.svn.core.connector.SVNConnectorException;
import org.eclipse.team.svn.core.connector.SVNDepth;
import org.eclipse.team.svn.core.connector.SVNEntry;
import org.eclipse.team.svn.core.connector.SVNEntryInfo;
import org.eclipse.team.svn.core.connector.SVNEntryRevisionReference;
import org.eclipse.team.svn.core.connector.SVNEntryStatus;
import org.eclipse.team.svn.core.connector.SVNLock;
import org.eclipse.team.svn.core.connector.SVNLogEntry;
import org.eclipse.team.svn.core.connector.SVNLogPath;
import org.eclipse.team.svn.core.connector.SVNMergeInfo;
import org.eclipse.team.svn.core.connector.SVNMergeInfo.LogKind;
import org.eclipse.team.svn.core.connector.SVNNotification;
import org.eclipse.team.svn.core.connector.SVNProperty;
import org.eclipse.team.svn.core.connector.SVNRepositoryNotification;
import org.eclipse.team.svn.core.connector.SVNRevision;
import org.eclipse.team.svn.core.connector.SVNRevisionRange;

/**
 * JavaHL <-> Subversive API conversions
 * 
 * @author Alexander Gurov
 */
public final class ConversionUtility {
	private static Map<org.apache.subversion.javahl.ClientNotifyInformation.Action, SVNNotification.PerformedAction> notifyInfoActionMap;
	private static Map<org.apache.subversion.javahl.ClientNotifyInformation.Status, SVNNotification.NodeStatus> notifyInfoStatusMap;
	private static Map<org.apache.subversion.javahl.ClientNotifyInformation.LockStatus, SVNNotification.NodeLock> notifyInfoLockStatusMap;
	private static Map<org.apache.subversion.javahl.types.NodeKind, SVNEntry.Kind> nodeKindMap;
	private static Map<org.apache.subversion.javahl.ConflictDescriptor.Reason, SVNConflictDescriptor.Reason> conflictReasonMap;
	private static Map<org.apache.subversion.javahl.types.Status.Kind, SVNEntryStatus.Kind> syncStatusMap;
	private static Map<org.apache.subversion.javahl.ReposNotifyInformation.NodeAction, SVNRepositoryNotification.NodeAction> repositoryNodeActionMap;
	private static Map<org.apache.subversion.javahl.ReposNotifyInformation.Action, SVNRepositoryNotification.Action> repositoryActionMap;
	
	static
	{
		ConversionUtility.syncStatusMap = new HashMap<org.apache.subversion.javahl.types.Status.Kind, SVNEntryStatus.Kind>();
		ConversionUtility.syncStatusMap.put(org.apache.subversion.javahl.types.Status.Kind.none, SVNEntryStatus.Kind.NONE);
		ConversionUtility.syncStatusMap.put(org.apache.subversion.javahl.types.Status.Kind.unversioned, SVNEntryStatus.Kind.UNVERSIONED);
		ConversionUtility.syncStatusMap.put(org.apache.subversion.javahl.types.Status.Kind.normal, SVNEntryStatus.Kind.NORMAL);
		ConversionUtility.syncStatusMap.put(org.apache.subversion.javahl.types.Status.Kind.added, SVNEntryStatus.Kind.ADDED);
		ConversionUtility.syncStatusMap.put(org.apache.subversion.javahl.types.Status.Kind.missing, SVNEntryStatus.Kind.MISSING);
		ConversionUtility.syncStatusMap.put(org.apache.subversion.javahl.types.Status.Kind.deleted, SVNEntryStatus.Kind.DELETED);
		ConversionUtility.syncStatusMap.put(org.apache.subversion.javahl.types.Status.Kind.replaced, SVNEntryStatus.Kind.REPLACED);
		ConversionUtility.syncStatusMap.put(org.apache.subversion.javahl.types.Status.Kind.modified, SVNEntryStatus.Kind.MODIFIED);
		ConversionUtility.syncStatusMap.put(org.apache.subversion.javahl.types.Status.Kind.merged, SVNEntryStatus.Kind.MERGED);
		ConversionUtility.syncStatusMap.put(org.apache.subversion.javahl.types.Status.Kind.conflicted, SVNEntryStatus.Kind.CONFLICTED);
		ConversionUtility.syncStatusMap.put(org.apache.subversion.javahl.types.Status.Kind.ignored, SVNEntryStatus.Kind.IGNORED);
		ConversionUtility.syncStatusMap.put(org.apache.subversion.javahl.types.Status.Kind.obstructed, SVNEntryStatus.Kind.OBSTRUCTED);
		ConversionUtility.syncStatusMap.put(org.apache.subversion.javahl.types.Status.Kind.external, SVNEntryStatus.Kind.EXTERNAL);
		ConversionUtility.syncStatusMap.put(org.apache.subversion.javahl.types.Status.Kind.incomplete, SVNEntryStatus.Kind.INCOMPLETE);
		
		ConversionUtility.conflictReasonMap = new HashMap<org.apache.subversion.javahl.ConflictDescriptor.Reason, SVNConflictDescriptor.Reason>();
		ConversionUtility.conflictReasonMap.put(org.apache.subversion.javahl.ConflictDescriptor.Reason.edited, SVNConflictDescriptor.Reason.MODIFIED);
		ConversionUtility.conflictReasonMap.put(org.apache.subversion.javahl.ConflictDescriptor.Reason.obstructed, SVNConflictDescriptor.Reason.OBSTRUCTED);
		ConversionUtility.conflictReasonMap.put(org.apache.subversion.javahl.ConflictDescriptor.Reason.deleted, SVNConflictDescriptor.Reason.DELETED);
		ConversionUtility.conflictReasonMap.put(org.apache.subversion.javahl.ConflictDescriptor.Reason.missing, SVNConflictDescriptor.Reason.MISSING);
		ConversionUtility.conflictReasonMap.put(org.apache.subversion.javahl.ConflictDescriptor.Reason.unversioned, SVNConflictDescriptor.Reason.UNVERSIONED);
		ConversionUtility.conflictReasonMap.put(org.apache.subversion.javahl.ConflictDescriptor.Reason.added, SVNConflictDescriptor.Reason.ADDED);
		ConversionUtility.conflictReasonMap.put(org.apache.subversion.javahl.ConflictDescriptor.Reason.replaced, SVNConflictDescriptor.Reason.REPLACED);
		ConversionUtility.conflictReasonMap.put(org.apache.subversion.javahl.ConflictDescriptor.Reason.moved_away, SVNConflictDescriptor.Reason.MOVED_AWAY);
		ConversionUtility.conflictReasonMap.put(org.apache.subversion.javahl.ConflictDescriptor.Reason.moved_here, SVNConflictDescriptor.Reason.MOVED_HERE);

		ConversionUtility.nodeKindMap = new HashMap<org.apache.subversion.javahl.types.NodeKind, SVNEntry.Kind>();
		ConversionUtility.nodeKindMap.put(org.apache.subversion.javahl.types.NodeKind.none, SVNEntry.Kind.NONE);
		ConversionUtility.nodeKindMap.put(org.apache.subversion.javahl.types.NodeKind.file, SVNEntry.Kind.FILE);
		ConversionUtility.nodeKindMap.put(org.apache.subversion.javahl.types.NodeKind.dir, SVNEntry.Kind.DIR);
		ConversionUtility.nodeKindMap.put(org.apache.subversion.javahl.types.NodeKind.unknown, SVNEntry.Kind.UNKNOWN);
		ConversionUtility.nodeKindMap.put(org.apache.subversion.javahl.types.NodeKind.symlink, SVNEntry.Kind.SYMLINK);
		
		ConversionUtility.notifyInfoStatusMap = new HashMap<org.apache.subversion.javahl.ClientNotifyInformation.Status, SVNNotification.NodeStatus>();
		ConversionUtility.notifyInfoStatusMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Status.inapplicable, SVNNotification.NodeStatus.INAPPLICABLE);
		ConversionUtility.notifyInfoStatusMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Status.unknown, SVNNotification.NodeStatus.UNKNOWN);
		ConversionUtility.notifyInfoStatusMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Status.unchanged, SVNNotification.NodeStatus.UNCHANGED);
		ConversionUtility.notifyInfoStatusMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Status.missing, SVNNotification.NodeStatus.MISSING);
		ConversionUtility.notifyInfoStatusMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Status.obstructed, SVNNotification.NodeStatus.OBSTRUCTED);
		ConversionUtility.notifyInfoStatusMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Status.changed, SVNNotification.NodeStatus.CHANGED);
		ConversionUtility.notifyInfoStatusMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Status.merged, SVNNotification.NodeStatus.MERGED);
		ConversionUtility.notifyInfoStatusMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Status.conflicted, SVNNotification.NodeStatus.CONFLICTED);
		
		ConversionUtility.notifyInfoLockStatusMap = new HashMap<org.apache.subversion.javahl.ClientNotifyInformation.LockStatus, SVNNotification.NodeLock>();
		ConversionUtility.notifyInfoLockStatusMap.put(org.apache.subversion.javahl.ClientNotifyInformation.LockStatus.inapplicable, SVNNotification.NodeLock.INAPPLICABLE);
		ConversionUtility.notifyInfoLockStatusMap.put(org.apache.subversion.javahl.ClientNotifyInformation.LockStatus.unknown, SVNNotification.NodeLock.UNKNOWN);
		ConversionUtility.notifyInfoLockStatusMap.put(org.apache.subversion.javahl.ClientNotifyInformation.LockStatus.unchanged, SVNNotification.NodeLock.UNCHANGED);
		ConversionUtility.notifyInfoLockStatusMap.put(org.apache.subversion.javahl.ClientNotifyInformation.LockStatus.locked, SVNNotification.NodeLock.LOCKED);
		ConversionUtility.notifyInfoLockStatusMap.put(org.apache.subversion.javahl.ClientNotifyInformation.LockStatus.unlocked, SVNNotification.NodeLock.UNLOCKED);
		
		ConversionUtility.notifyInfoActionMap = new HashMap<org.apache.subversion.javahl.ClientNotifyInformation.Action, SVNNotification.PerformedAction>();
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.add, SVNNotification.PerformedAction.ADD);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.copy, SVNNotification.PerformedAction.COPY);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.delete, SVNNotification.PerformedAction.DELETE);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.restore, SVNNotification.PerformedAction.RESTORE);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.revert, SVNNotification.PerformedAction.REVERT);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.failed_revert, SVNNotification.PerformedAction.FAILED_REVERT);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.resolved, SVNNotification.PerformedAction.RESOLVED);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.skip, SVNNotification.PerformedAction.SKIP);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.update_delete, SVNNotification.PerformedAction.UPDATE_DELETE);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.update_add, SVNNotification.PerformedAction.UPDATE_ADD);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.update_update, SVNNotification.PerformedAction.UPDATE_UPDATE);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.update_completed, SVNNotification.PerformedAction.UPDATE_COMPLETED);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.update_external, SVNNotification.PerformedAction.UPDATE_EXTERNAL);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.status_completed, SVNNotification.PerformedAction.STATUS_COMPLETED);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.status_external, SVNNotification.PerformedAction.STATUS_EXTERNAL);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.commit_modified, SVNNotification.PerformedAction.COMMIT_MODIFIED);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.commit_added, SVNNotification.PerformedAction.COMMIT_ADDED);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.commit_deleted, SVNNotification.PerformedAction.COMMIT_DELETED);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.commit_replaced, SVNNotification.PerformedAction.COMMIT_REPLACED);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.commit_postfix_txdelta, SVNNotification.PerformedAction.COMMIT_POSTFIX_TXDELTA);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.blame_revision, SVNNotification.PerformedAction.BLAME_REVISION);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.locked, SVNNotification.PerformedAction.LOCKED);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.unlocked, SVNNotification.PerformedAction.UNLOCKED);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.failed_lock, SVNNotification.PerformedAction.FAILED_LOCK);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.failed_unlock, SVNNotification.PerformedAction.FAILED_UNLOCK);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.exists, SVNNotification.PerformedAction.EXISTS);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.changelist_set, SVNNotification.PerformedAction.CHANGELIST_SET);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.changelist_clear, SVNNotification.PerformedAction.CHANGELIST_CLEAR);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.changelist_moved, SVNNotification.PerformedAction.CHANGELIST_MOVED);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.merge_begin, SVNNotification.PerformedAction.MERGE_BEGIN);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.foreign_merge_begin, SVNNotification.PerformedAction.FOREIGN_MERGE_BEGIN);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.update_replaced, SVNNotification.PerformedAction.UPDATE_REPLACED);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.property_added, SVNNotification.PerformedAction.PROPERTY_ADDED);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.property_modified, SVNNotification.PerformedAction.PROPERTY_MODIFIED);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.property_deleted, SVNNotification.PerformedAction.PROPERTY_DELETED);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.property_deleted_nonexistent, SVNNotification.PerformedAction.PROPERTY_DELETED_NONEXISTENT);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.revprop_set, SVNNotification.PerformedAction.REVPROP_SET);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.revprop_deleted, SVNNotification.PerformedAction.REVPROP_DELETE);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.merge_completed, SVNNotification.PerformedAction.MERGE_COMPLETED);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.tree_conflict, SVNNotification.PerformedAction.TREE_CONFLICT);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.failed_external, SVNNotification.PerformedAction.FAILED_EXTERNAL);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.update_started, SVNNotification.PerformedAction.UPDATE_STARTED);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.update_skip_obstruction, SVNNotification.PerformedAction.UPDATE_SKIP_OBSTRUCTION);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.update_skip_working_only, SVNNotification.PerformedAction.UPDATE_SKIP_WORKING_ONLY);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.update_skip_access_denied, SVNNotification.PerformedAction.UPDATE_SKIP_ACCESS_DENIED);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.update_external_removed, SVNNotification.PerformedAction.UPDATE_EXTERNAL_REMOVED);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.update_shadowed_add, SVNNotification.PerformedAction.UPDATE_SHADOWED_ADD);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.update_shadowed_update, SVNNotification.PerformedAction.UPDATE_SHADOWED_UPDATE);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.update_shadowed_delete, SVNNotification.PerformedAction.UPDATE_SHADOWED_DELETE);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.merge_record_info, SVNNotification.PerformedAction.MERGE_RECORD_INFO);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.upgraded_path, SVNNotification.PerformedAction.UPGRADED_PATH);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.merge_record_info_begin, SVNNotification.PerformedAction.MERGE_RECORD_INFO_BEGIN);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.merge_elide_info, SVNNotification.PerformedAction.MERGE_ELIDE_INFO);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.patch, SVNNotification.PerformedAction.PATCH);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.patch_applied_hunk, SVNNotification.PerformedAction.PATCH_APPLIED_HUNK);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.patch_rejected_hunk, SVNNotification.PerformedAction.PATCH_REJECTED_HUNK);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.patch_hunk_already_applied, SVNNotification.PerformedAction.PATCH_HUNK_ALREADY_APPLIED);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.commit_copied, SVNNotification.PerformedAction.COMMIT_COPIED);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.commit_copied_replaced, SVNNotification.PerformedAction.COMMIT_COPIED_REPLACED);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.url_redirect, SVNNotification.PerformedAction.URL_REDIRECT);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.path_nonexistent, SVNNotification.PerformedAction.PATH_NONEXISTENT);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.exclude, SVNNotification.PerformedAction.EXCLUDE);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.failed_conflict, SVNNotification.PerformedAction.FAILED_CONFLICT);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.failed_missing, SVNNotification.PerformedAction.FAILED_MISSING);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.failed_out_of_date, SVNNotification.PerformedAction.FAILED_OUT_OF_DATE);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.failed_no_parent, SVNNotification.PerformedAction.FAILED_NO_PARENT);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.failed_locked, SVNNotification.PerformedAction.FAILED_LOCKED);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.failed_forbidden_by_server, SVNNotification.PerformedAction.FAILED_FORBIDDEN_BY_SERVER);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.skip_conflicted, SVNNotification.PerformedAction.SKIP_CONFLICTED);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.update_broken_lock, SVNNotification.PerformedAction.UPDATE_BROKEN_LOCK);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.failed_obstructed, SVNNotification.PerformedAction.FAILED_OBSTRUCTED);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.conflict_resolver_starting, SVNNotification.PerformedAction.CONFLICT_RESOLVER_STARTING);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.conflict_resolver_done, SVNNotification.PerformedAction.CONFLICT_RESOLVER_DONE);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.left_local_modifications, SVNNotification.PerformedAction.LEFT_LOCAL_MODIFICATIONS);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.foreign_copy_begin, SVNNotification.PerformedAction.FOREIGN_COPY_BEGIN);
		ConversionUtility.notifyInfoActionMap.put(org.apache.subversion.javahl.ClientNotifyInformation.Action.move_broken, SVNNotification.PerformedAction.MOVE_BROKEN);
		
		ConversionUtility.repositoryNodeActionMap = new HashMap<org.apache.subversion.javahl.ReposNotifyInformation.NodeAction, SVNRepositoryNotification.NodeAction>();
		ConversionUtility.repositoryNodeActionMap.put(org.apache.subversion.javahl.ReposNotifyInformation.NodeAction.change, SVNRepositoryNotification.NodeAction.CHANGE);
		ConversionUtility.repositoryNodeActionMap.put(org.apache.subversion.javahl.ReposNotifyInformation.NodeAction.add, SVNRepositoryNotification.NodeAction.ADD);
		ConversionUtility.repositoryNodeActionMap.put(org.apache.subversion.javahl.ReposNotifyInformation.NodeAction.deleted, SVNRepositoryNotification.NodeAction.DELETE);
		ConversionUtility.repositoryNodeActionMap.put(org.apache.subversion.javahl.ReposNotifyInformation.NodeAction.replace, SVNRepositoryNotification.NodeAction.REPLACE);
		
		ConversionUtility.repositoryActionMap = new HashMap<org.apache.subversion.javahl.ReposNotifyInformation.Action, SVNRepositoryNotification.Action>();
		ConversionUtility.repositoryActionMap.put(org.apache.subversion.javahl.ReposNotifyInformation.Action.warning, SVNRepositoryNotification.Action.WARNING);
		ConversionUtility.repositoryActionMap.put(org.apache.subversion.javahl.ReposNotifyInformation.Action.dump_rev_end, SVNRepositoryNotification.Action.DUMP_REV_END);
		ConversionUtility.repositoryActionMap.put(org.apache.subversion.javahl.ReposNotifyInformation.Action.verify_rev_end, SVNRepositoryNotification.Action.VERIFY_REV_END);
		ConversionUtility.repositoryActionMap.put(org.apache.subversion.javahl.ReposNotifyInformation.Action.pack_shard_start, SVNRepositoryNotification.Action.PACK_SHARD_START);
		ConversionUtility.repositoryActionMap.put(org.apache.subversion.javahl.ReposNotifyInformation.Action.pack_shard_end, SVNRepositoryNotification.Action.PACK_SHARD_END);
		ConversionUtility.repositoryActionMap.put(org.apache.subversion.javahl.ReposNotifyInformation.Action.pack_shard_start_revprop, SVNRepositoryNotification.Action.PACK_SHARD_START_REVPROP);
		ConversionUtility.repositoryActionMap.put(org.apache.subversion.javahl.ReposNotifyInformation.Action.pack_shard_end_revprop, SVNRepositoryNotification.Action.PACK_SHARD_END_REVPROP);
		ConversionUtility.repositoryActionMap.put(org.apache.subversion.javahl.ReposNotifyInformation.Action.load_txn_start, SVNRepositoryNotification.Action.LOAD_TXN_START);
		ConversionUtility.repositoryActionMap.put(org.apache.subversion.javahl.ReposNotifyInformation.Action.load_txn_committed, SVNRepositoryNotification.Action.LOAD_TXN_COMMITTED);
		ConversionUtility.repositoryActionMap.put(org.apache.subversion.javahl.ReposNotifyInformation.Action.load_node_start, SVNRepositoryNotification.Action.LOAD_NODE_START);
		ConversionUtility.repositoryActionMap.put(org.apache.subversion.javahl.ReposNotifyInformation.Action.load_node_done, SVNRepositoryNotification.Action.LOAD_NODE_END);
		ConversionUtility.repositoryActionMap.put(org.apache.subversion.javahl.ReposNotifyInformation.Action.load_copied_node, SVNRepositoryNotification.Action.LOAD_COPIED_NODE);
		ConversionUtility.repositoryActionMap.put(org.apache.subversion.javahl.ReposNotifyInformation.Action.load_normalized_mergeinfo, SVNRepositoryNotification.Action.LOAD_NORMALIZED_MERGEINFO);
		ConversionUtility.repositoryActionMap.put(org.apache.subversion.javahl.ReposNotifyInformation.Action.mutex_acquired, SVNRepositoryNotification.Action.MUTEX_ACQUIRED);
		ConversionUtility.repositoryActionMap.put(org.apache.subversion.javahl.ReposNotifyInformation.Action.recover_start, SVNRepositoryNotification.Action.RECOVER_START);
		ConversionUtility.repositoryActionMap.put(org.apache.subversion.javahl.ReposNotifyInformation.Action.upgrade_start, SVNRepositoryNotification.Action.UPGRADE_START);
		ConversionUtility.repositoryActionMap.put(org.apache.subversion.javahl.ReposNotifyInformation.Action.load_skipped_rev, SVNRepositoryNotification.Action.LOAD_SKIPPED_REV);
		ConversionUtility.repositoryActionMap.put(org.apache.subversion.javahl.ReposNotifyInformation.Action.verify_rev_structure, SVNRepositoryNotification.Action.VERIFY_REV_STRUCTURE);
	}
	
	
	public static ReposFreezeAction convert(final ISVNRepositoryFreezeAction cb) {
		return cb == null ? null : new ReposFreezeAction() {
			public void invoke() {
				cb.run();
			}
		};
	}
	
	public static MessageReceiver convert(final ISVNRepositoryMessageCallback cb) {
		return cb == null ? null : new MessageReceiver() {
			public void receiveMessageLine(String message) {
				cb.nextMessage(message);
			}
		};
	}
	
	public static ReposNotifyCallback convert(final ISVNRepositoryNotificationCallback cb) {
		return cb == null ? null : new ReposNotifyCallback() {
			public void onNotify(ReposNotifyInformation info) {
				cb.notify(ConversionUtility.convert(info));
			}
		};
	}
	
	public static SVNRepositoryNotification convert(ReposNotifyInformation info) {
		SVNRepositoryNotification.NodeAction nodeAction = ConversionUtility.repositoryNodeActionMap.get(info.getNodeAction());
		SVNRepositoryNotification.Action action = ConversionUtility.repositoryActionMap.get(info.getAction());
		return new SVNRepositoryNotification(info.getPath(), nodeAction == null ? SVNRepositoryNotification.NodeAction.UNKNOWN : nodeAction, action == null ? SVNRepositoryNotification.Action.UNKNOWN : action, info.getRevision(), info.getWarning(), info.getShard(), info.getNewRevision(), info.getOldRevision());
	}
	
	public static org.apache.subversion.javahl.types.DiffOptions convertDiffOptions(long diffOptions) {
		org.apache.subversion.javahl.types.DiffOptions retVal = null;
		if (diffOptions != Options.NONE) {
			ArrayList flags = new ArrayList();
			if ((diffOptions & DiffOptions.IGNORE_WHITESPACE) != 0) {
				flags.add(org.apache.subversion.javahl.types.DiffOptions.Flag.IgnoreWhitespace);
			}
			if ((diffOptions & DiffOptions.IGNORE_SPACE_CHANGE) != 0) {
				flags.add(org.apache.subversion.javahl.types.DiffOptions.Flag.IgnoreSpaceChange);
			}
			if ((diffOptions & DiffOptions.IGNORE_EOL_STYLE) != 0) {
				flags.add(org.apache.subversion.javahl.types.DiffOptions.Flag.IgnoreEOLStyle);
			}
			if ((diffOptions & DiffOptions.SHOW_FUNCTION) != 0) {
				flags.add(org.apache.subversion.javahl.types.DiffOptions.Flag.ShowFunction);
			}
			if ((diffOptions & DiffOptions.GIT_FORMAT) != 0) {
				flags.add(org.apache.subversion.javahl.types.DiffOptions.Flag.GitFormat);
			}
			retVal = new org.apache.subversion.javahl.types.DiffOptions((org.apache.subversion.javahl.types.DiffOptions.Flag [])flags.toArray(new org.apache.subversion.javahl.types.DiffOptions.Flag[flags.size()]));
		}
		return retVal;
	}
	
	public static ImportFilterCallback convert(final ISVNImportFilterCallback filter) {
		return filter == null ? null : new ImportFilterCallback() {
			public boolean filter(String path, org.apache.subversion.javahl.types.NodeKind kind, boolean special) {
				return filter.filterOut(path, ConversionUtility.convert(kind), special);
			}
		};
	}
	
	public static SVNCommitStatus convert(CommitInfo info) {
		return new SVNCommitStatus(info.getPostCommitError(), info.getReposRoot(), info.getRevision(), info.getDate() != null ? info.getDate().getTime() : 0, info.getAuthor());
	}
	
	public static org.apache.subversion.javahl.callback.PatchCallback convert(final ISVNPatchCallback callback) {
		if (callback == null) {
			return null;
		}
		return new org.apache.subversion.javahl.callback.PatchCallback() {
			public boolean singlePatch(String pathFromPatchfile, String patchPath, String rejectPath) {
				return callback.singlePatch(pathFromPatchfile, patchPath, rejectPath);
			}
		};
	}
	
	public static org.apache.subversion.javahl.callback.ProplistCallback convert(final ISVNPropertyCallback callback) {
		if (callback == null) {
			return null;
		}
		return new org.apache.subversion.javahl.callback.ProplistCallback() {
			public void singlePath(String path, Map<String, byte[]> properties) {
				SVNProperty []data = new SVNProperty[properties.size()];
				int i = 0;
				for (Iterator<Map.Entry<String, byte []>> it = properties.entrySet().iterator(); it.hasNext(); i++) {
					Map.Entry<String, byte []> entry = it.next();
					data[i] = new SVNProperty(entry.getKey(), entry.getValue());
				}
				callback.next(new ISVNPropertyCallback.Pair(path, data), new ISVNPropertyCallback.Pair[0]);
			}
		};
	}
	
	public static org.apache.subversion.javahl.callback.InheritedProplistCallback convertInherited(final ISVNPropertyCallback callback) {
		if (callback == null) {
			return null;
		}
		return new org.apache.subversion.javahl.callback.InheritedProplistCallback() {
			public void singlePath(String path, Map<String, byte[]> properties, Collection<InheritedItem> inherited_properties) {
				ISVNPropertyCallback.Pair personalData = this.makePair(path, properties);
				ISVNPropertyCallback.Pair []inheritedData = new ISVNPropertyCallback.Pair[inherited_properties == null ? 0 : inherited_properties.size()];
				if (inherited_properties != null) {
					int i = 0;
					for (InheritedItem item : inherited_properties) {
						inheritedData[i++] = this.makePair(item.path_or_url, item.properties);
					}
				}
				callback.next(personalData, inheritedData);
			}
			
			private ISVNPropertyCallback.Pair makePair(String path, Map<String, byte[]> properties) {
				SVNProperty []data = new SVNProperty[properties.size()];
				int i = 0;
				for (Iterator<Map.Entry<String, byte []>> it = properties.entrySet().iterator(); it.hasNext(); i++) {
					Map.Entry<String, byte []> entry = it.next();
					data[i] = new SVNProperty(entry.getKey(), entry.getValue());
				}
				return new ISVNPropertyCallback.Pair(path, data);
			}
		};
	}
	
	public static Map<String, String> convertRevPropsToSVN(Map revProps) {
		if (revProps == null) {
			return null;
		}
		Map<String, String> retVal = new HashMap<String, String>();
		for (Iterator<Map.Entry> it = revProps.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry entry = (Map.Entry)it.next();
			if (entry.getKey().equals(SVNProperty.BuiltIn.REV_DATE)) {
				Date date = (Date)entry.getValue();
				retVal.put(SVNProperty.BuiltIn.REV_DATE, date == null ? null : new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS z").format(date));
			}
			else {
				retVal.put((String)entry.getKey(), (String)entry.getValue());
			}
		}
		return retVal;
	}

	public static Map convertRevPropsFromSVN(Map<String, byte []> revProps, DateFormat formatter, Calendar tDate) {
		if (revProps == null) {
			return null;
		}
		Map retVal = new HashMap();
		for (Iterator<Map.Entry<String, byte []>> it = revProps.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<String, byte []> entry = it.next();
			if (entry.getKey().equals(SVNProperty.BuiltIn.REV_DATE)) {
				String dateStr = new String(entry.getValue());
		        if (dateStr.length() == 27 && dateStr.charAt(26) == 'Z')
		        {
					try {
						formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS z");
						tDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
						tDate.setTime(formatter.parse(dateStr.substring(0, 23) + " UTC"));
						retVal.put(SVNProperty.BuiltIn.REV_DATE, tDate.getTime());
					}
					catch (ParseException e) {
						// uninteresting in this context
					}
					catch (NumberFormatException e) {
						// uninteresting in this context
					} 
		        }
			}
			else {
				String str = null;
				try {
					str = new String(entry.getValue(), "UTF-8");
				}
				catch (UnsupportedEncodingException ex) {
					str = new String(entry.getValue());
				}
				retVal.put(entry.getKey(), str);
			}
		}
		return retVal;
	}

	public static org.apache.subversion.javahl.callback.LogMessageCallback convert(final ISVNLogEntryCallback callback) {
		if (callback == null) {
			return null;
		}
		return new org.apache.subversion.javahl.callback.LogMessageCallback() {
			/*
			 * Copied from the LogDate class in order to avoid unsafe usage of a static DateFormat instance in the multi-threaded environment.
			 */
			private DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS z");
			private Calendar date = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
			
			public void singleMessage(Set<org.apache.subversion.javahl.types.ChangePath> changedPaths, long revision, Map<String, byte[]> revprops, boolean hasChildren) {
				SVNLogEntry entry = this.convert(changedPaths == null ? null : changedPaths.toArray(new org.apache.subversion.javahl.types.ChangePath[changedPaths.size()]), revision, revprops, hasChildren);
				callback.next(entry);
			}
			
			private SVNLogEntry convert(org.apache.subversion.javahl.types.ChangePath[] changedPaths, long revision, Map<String, byte[]> revProps, boolean hasChildren) {
				if (revProps == null) {
					// null if no access rights...
					return new SVNLogEntry(revision, 0l, null, null, ConversionUtility.convert(changedPaths), hasChildren);
				}
		    	Map tRevProps = ConversionUtility.convertRevPropsFromSVN(revProps, this.formatter, this.date);
				Date date = tRevProps == null ? null : (Date)tRevProps.get(SVNProperty.BuiltIn.REV_DATE);
				return new SVNLogEntry(revision, date == null ? 0 : date.getTime(), tRevProps == null ? null : (String)tRevProps.get(SVNProperty.BuiltIn.REV_AUTHOR), tRevProps == null ? null : (String)tRevProps.get(SVNProperty.BuiltIn.REV_LOG), ConversionUtility.convert(changedPaths), hasChildren);
			}
		};
	}

	public static org.apache.subversion.javahl.types.CopySource []convert(SVNEntryRevisionReference []info) {
		if (info == null) {
			return null;
		}
		org.apache.subversion.javahl.types.CopySource []retVal = new org.apache.subversion.javahl.types.CopySource[info.length];
		for (int i = 0; i < info.length; i++) {
			retVal[i] = ConversionUtility.convert(info[i]);
		}
		return retVal;
	}

	public static org.apache.subversion.javahl.types.CopySource convert(SVNEntryRevisionReference info) {
		return info == null ? null : new org.apache.subversion.javahl.types.CopySource(info.path, ConversionUtility.convert(info.revision), ConversionUtility.convert(info.pegRevision));
	}

	public static org.apache.subversion.javahl.callback.InfoCallback convert(final ISVNEntryInfoCallback callback) {
		if (callback == null) {
			return null;
		}
		return new org.apache.subversion.javahl.callback.InfoCallback() {
			public void singleInfo(org.apache.subversion.javahl.types.Info info) {
				callback.next(ConversionUtility.convert(info));
			}
		};
	}

	public static org.apache.subversion.javahl.callback.StatusCallback convert(final ISVNEntryStatusCallback cb) {
		if (cb == null) {
			return null;
		}
		return new org.apache.subversion.javahl.callback.StatusCallback() {
			public void doStatus(String path, org.apache.subversion.javahl.types.Status st) {
				cb.next(ConversionUtility.convert(st));
			}
		};
	}
	
	public static org.apache.subversion.javahl.callback.ConflictResolverCallback convert(final ISVNConflictResolutionCallback callback) {
		if (callback == null) {
			return null;
		}
		return new org.apache.subversion.javahl.callback.ConflictResolverCallback() {
			public org.apache.subversion.javahl.ConflictResult resolve(org.apache.subversion.javahl.ConflictDescriptor descrip) throws SubversionException {
				try {
					return ConversionUtility.convert(callback.resolve(ConversionUtility.convert(descrip)));
				}
				catch (SVNConnectorException ex) {
					throw ClientException.fromException(ex);
				}
			}
		};
	}

	public static SVNConflictDescriptor []convert(Set<org.apache.subversion.javahl.ConflictDescriptor> descr) {
		SVNConflictDescriptor []retVal = null;
		if (descr != null) {
			retVal = new SVNConflictDescriptor[descr.size()];
			int i = 0;
			for (org.apache.subversion.javahl.ConflictDescriptor svnConflictDescriptor : descr) {
				retVal[i++] = ConversionUtility.convert(svnConflictDescriptor);
			}
		}
		return retVal;
	}
	
	public static SVNConflictDescriptor convert(org.apache.subversion.javahl.ConflictDescriptor descr) {
		if (descr == null) {
			return null;
		}
		org.apache.subversion.javahl.ConflictDescriptor.Kind tKind = descr.getKind();
		SVNConflictDescriptor.Kind kind = SVNConflictDescriptor.Kind.CONTENT;
		if (tKind == org.apache.subversion.javahl.ConflictDescriptor.Kind.property) {
			kind = SVNConflictDescriptor.Kind.PROPERTIES;
		}
		else if (tKind == org.apache.subversion.javahl.ConflictDescriptor.Kind.tree) {
			kind = SVNConflictDescriptor.Kind.TREE;
		}
		org.apache.subversion.javahl.ConflictDescriptor.Action tAction = descr.getAction();
		SVNConflictDescriptor.Action action = SVNConflictDescriptor.Action.ADD;
		if (tAction == org.apache.subversion.javahl.ConflictDescriptor.Action.edit) {
			action = SVNConflictDescriptor.Action.MODIFY;
		}
		else if (tAction == org.apache.subversion.javahl.ConflictDescriptor.Action.delete) {
			action = SVNConflictDescriptor.Action.DELETE;
		}
		else if (tAction == org.apache.subversion.javahl.ConflictDescriptor.Action.replace) {
			action = SVNConflictDescriptor.Action.REPLACE;
		}
		SVNConflictDescriptor.Reason reason = ConversionUtility.conflictReasonMap.get(descr.getReason());
		org.apache.subversion.javahl.ConflictDescriptor.Operation tOperation = descr.getOperation();
		SVNConflictDescriptor.Operation operation = SVNConflictDescriptor.Operation.NONE;
		if (tOperation == org.apache.subversion.javahl.ConflictDescriptor.Operation.merge) {
			operation = SVNConflictDescriptor.Operation.MERGE;
		}
		else if (tOperation == org.apache.subversion.javahl.ConflictDescriptor.Operation.switched) {
			operation = SVNConflictDescriptor.Operation.SWITCHED;
		}
		else if (tOperation == org.apache.subversion.javahl.ConflictDescriptor.Operation.update) {
			operation = SVNConflictDescriptor.Operation.UPDATE;
		}
		return descr == null ? null : new SVNConflictDescriptor(
			descr.getPath(), kind, ConversionUtility.convert(descr.getNodeKind()),
			descr.getPropertyName(), descr.isBinary(), descr.getMIMEType(),
			action, reason == null ? SVNConflictDescriptor.Reason.MODIFIED : reason, operation, descr.getBasePath(),
			descr.getTheirPath(), descr.getMyPath(), descr.getMergedPath(),
			ConversionUtility.convert(descr.getSrcLeftVersion()), 
			ConversionUtility.convert(descr.getSrcRightVersion()));
	}

	public static SVNConflictVersion convert(org.apache.subversion.javahl.types.ConflictVersion conflictVersion) {
		return conflictVersion == null ? null : new SVNConflictVersion(conflictVersion.getReposURL(), conflictVersion.getPegRevision(), conflictVersion.getPathInRepos(), ConversionUtility.convert(conflictVersion.getNodeKind()));
	}
	
	public static org.apache.subversion.javahl.ConflictResult convert(SVNConflictResolution result) {
		return result == null ? null : new org.apache.subversion.javahl.ConflictResult(ConversionUtility.convertConflictChoice(result.choice), result.mergedPath);
	}

	public static SVNMergeInfo convert(org.apache.subversion.javahl.types.Mergeinfo info) {
		if (info == null) {
			return null;
		}
		SVNMergeInfo retVal = new SVNMergeInfo();
		String []paths = info.getPaths() == null ? null : info.getPaths().toArray(new String[info.getPaths().size()]);
		if (paths != null) {
			for (int i = 0; i < paths.length; i++) {
				retVal.addRevisions(paths[i], ConversionUtility.convert(info.getRevisionRange(paths[i]).toArray(new org.apache.subversion.javahl.types.RevisionRange[0])));
			}
		}
		return retVal;
	}

	public static org.apache.subversion.javahl.types.RevisionRange []convert(SVNRevisionRange []ranges) {
		if (ranges == null) {
			return null;
		}
		org.apache.subversion.javahl.types.RevisionRange []retVal = new org.apache.subversion.javahl.types.RevisionRange[ranges.length];
		for (int i = 0; i < ranges.length; i++) {
			retVal[i] = ConversionUtility.convert(ranges[i]);
		}
		return retVal;
	}
	
	public static SVNRevisionRange []convert(org.apache.subversion.javahl.types.RevisionRange []ranges) {
		if (ranges == null) {
			return null;
		}
		SVNRevisionRange []retVal = new SVNRevisionRange[ranges.length];
		for (int i = 0; i < ranges.length; i++) {
			retVal[i] = ConversionUtility.convert(ranges[i]);
		}
		return retVal;
	}
	
	public static SVNRevisionRange convert(org.apache.subversion.javahl.types.RevisionRange range) {
		return range == null ? null : new SVNRevisionRange(ConversionUtility.convert(range.getFromRevision()), ConversionUtility.convert(range.getToRevision()));
	}
	
	public static org.apache.subversion.javahl.types.RevisionRange convert(SVNRevisionRange range) {
		return range == null ? null : new org.apache.subversion.javahl.types.RevisionRange(ConversionUtility.convert(range.from), ConversionUtility.convert(range.to));
	}
	
	public static SVNEntryInfo []convert(org.apache.subversion.javahl.types.Info []infos) {
		if (infos == null) {
			return null;
		}
		SVNEntryInfo []retVal = new SVNEntryInfo[infos.length];
		for (int i = 0; i < infos.length; i++) {
			retVal[i] = ConversionUtility.convert(infos[i]);
		}
		return retVal;
	}
	
	public static SVNChecksum convert(org.apache.subversion.javahl.types.Checksum checksum) {
		return checksum == null ? null : new SVNChecksum(checksum.getKind() == org.apache.subversion.javahl.types.Checksum.Kind.MD5 ? SVNChecksum.Kind.MD5 : SVNChecksum.Kind.SHA1, checksum.getDigest());
	}
	
	public static SVNEntryInfo convert(org.apache.subversion.javahl.types.Info info) {
		if (info == null) {
			return null;
		}
		org.apache.subversion.javahl.types.Info.ScheduleKind tScheduleKind = info.getSchedule();
		SVNEntryInfo.ScheduledOperation scheduleKind = SVNEntryInfo.ScheduledOperation.NORMAL;
		if (tScheduleKind == org.apache.subversion.javahl.types.Info.ScheduleKind.add) {
			scheduleKind = SVNEntryInfo.ScheduledOperation.ADD;
		}
		else if (tScheduleKind == org.apache.subversion.javahl.types.Info.ScheduleKind.delete) {
			scheduleKind = SVNEntryInfo.ScheduledOperation.DELETE;
		}
		else if (tScheduleKind == org.apache.subversion.javahl.types.Info.ScheduleKind.replace) {
			scheduleKind = SVNEntryInfo.ScheduledOperation.REPLACE;
		}
		long changeTime = info.getTextTime() == null ? 0 : info.getTextTime().getTime();
		return new SVNEntryInfo(
				info.getPath(), info.getWcroot(), info.getUrl(), info.getRev(), ConversionUtility.convert(info.getKind()), info.getReposRootUrl(), info.getReposUUID(), 
				info.getLastChangedRev(), info.getLastChangedDate() == null ? 0 : info.getLastChangedDate().getTime(), 
				info.getLastChangedAuthor(), ConversionUtility.convert(info.getLock()), info.isHasWcInfo(), scheduleKind,  
				info.getCopyFromUrl(), info.getCopyFromRev(), changeTime, changeTime, ConversionUtility.convert(info.getChecksum()), info.getChangelistName(), info.getWorkingSize(), 
				info.getReposSize(), ConversionUtility.convertDepth(info.getDepth()), ConversionUtility.convert(info.getConflicts()));
	}
	
	public static SVNLogPath []convert(org.apache.subversion.javahl.types.ChangePath []paths) {
		if (paths == null) {
			return null;
		}
		SVNLogPath []retVal = new SVNLogPath[paths.length];
		for (int i = 0; i < paths.length; i++) {
			retVal[i] = ConversionUtility.convert(paths[i]);
		}
		return retVal;
	}
	
	public static SVNLogPath convert(org.apache.subversion.javahl.types.ChangePath path) {
		if (path == null) {
			return null;
		}
		org.apache.subversion.javahl.types.ChangePath.Action tAction = path.getAction();
		SVNLogPath.ChangeType action = SVNLogPath.ChangeType.ADDED;
		if (tAction == org.apache.subversion.javahl.types.ChangePath.Action.delete) {
			action = SVNLogPath.ChangeType.DELETED;
		}
		else if (tAction == org.apache.subversion.javahl.types.ChangePath.Action.modify) {
			action = SVNLogPath.ChangeType.MODIFIED;
		}
		else if (tAction == org.apache.subversion.javahl.types.ChangePath.Action.replace) {
			action = SVNLogPath.ChangeType.REPLACED;
		}
		return new SVNLogPath(path.getPath(), action, path.getCopySrcPath(), path.getCopySrcRevision(), ConversionUtility.convert(path.getTextMods()), ConversionUtility.convert(path.getPropMods()));
	}
	
	public static Boolean convert(Tristate ts) {
		return ts == Tristate.True ? Boolean.TRUE : (ts == Tristate.False ? Boolean.FALSE : null);
	}
	
	public static SVNProperty []convertRevProps(Map<String, byte []> data) {
		if (data == null) {
			return null;
		}
		SVNProperty []retVal = new SVNProperty[data.size()];
		int i = 0;
		for (Iterator<Map.Entry<String, byte []>> it = data.entrySet().iterator(); it.hasNext(); i++) {
			Map.Entry<String, byte []> entry = it.next();
			retVal[i] = new SVNProperty(entry.getKey(), entry.getValue());
		}
		return retVal;
	}
	
	public static SVNChangeStatus []convert(org.apache.subversion.javahl.types.Status []st) {
		if (st == null) {
			return null;
		}
		SVNChangeStatus []retVal = new SVNChangeStatus[st.length];
		for (int i = 0; i < st.length; i++) {
			retVal[i] = ConversionUtility.convert(st[i]);
		}
		return retVal;
	}
	
	public static SVNChangeStatus convert(org.apache.subversion.javahl.types.Status st) {
		if (st == null) {
			return null;
		}
		SVNEntryStatus.Kind textStatus = ConversionUtility.syncStatusMap.get(st.getTextStatus());
		SVNEntryStatus.Kind propStatus = ConversionUtility.syncStatusMap.get(st.getPropStatus());
		SVNEntryStatus.Kind repoTextStatus = ConversionUtility.syncStatusMap.get(st.getRepositoryTextStatus());
		SVNEntryStatus.Kind repoPropStatus = ConversionUtility.syncStatusMap.get(st.getRepositoryPropStatus());
		return new SVNChangeStatus(
				st.getPath(), st.getUrl(), ConversionUtility.convert(st.getNodeKind()), 
				st.getRevisionNumber(), st.getLastChangedRevisionNumber(), 
				st.getLastChangedDate() == null ? 0 : st.getLastChangedDate().getTime(), 
				st.getLastCommitAuthor(), textStatus == null ? SVNEntryStatus.Kind.NONE : textStatus, propStatus == null ? SVNEntryStatus.Kind.NONE : propStatus, 
				repoTextStatus == null ? SVNEntryStatus.Kind.NONE : repoTextStatus, repoPropStatus == null ? SVNEntryStatus.Kind.NONE : repoPropStatus, st.isLocked(), 
				st.isCopied(), st.isSwitched(), ConversionUtility.convert(st.getLocalLock()), ConversionUtility.convert(st.getReposLock()), 
				st.getReposLastCmtRevisionNumber(), st.getReposLastCmtDate() == null ? 0 : st.getReposLastCmtDate().getTime(), 
				ConversionUtility.convert(st.getReposKind()), st.getReposLastCmtAuthor(), st.isFileExternal(), st.isConflicted(), null, st.getChangelist());
	}
	
	public static SVNEntry.Kind convert(org.apache.subversion.javahl.types.NodeKind kind) {
		SVNEntry.Kind retVal = ConversionUtility.nodeKindMap.get(kind);
		return retVal == null ? SVNEntry.Kind.UNKNOWN : retVal;
	}
	
	public static SVNNotification.PerformedAction convert(org.apache.subversion.javahl.ClientNotifyInformation.Action action) {
		SVNNotification.PerformedAction retVal = ConversionUtility.notifyInfoActionMap.get(action);
		return retVal == null ? SVNNotification.PerformedAction._UNKNOWN_ACTION : retVal;
	}
	
	public static SVNNotification.NodeStatus convert(org.apache.subversion.javahl.ClientNotifyInformation.Status status) {
		SVNNotification.NodeStatus retVal = ConversionUtility.notifyInfoStatusMap.get(status);
		return retVal == null ? SVNNotification.NodeStatus.UNKNOWN : retVal;
	}
	
	public static SVNNotification.NodeLock convert(org.apache.subversion.javahl.ClientNotifyInformation.LockStatus status) {
		SVNNotification.NodeLock retVal = ConversionUtility.notifyInfoLockStatusMap.get(status);
		return retVal == null ? SVNNotification.NodeLock.UNKNOWN : retVal;
	}
	
	public static SVNLock convert(org.apache.subversion.javahl.types.Lock lock) {
		return lock == null ? null : new SVNLock(
				lock.getOwner(), lock.getPath(), lock.getToken(), lock.getComment(), 
				lock.getCreationDate() == null ? 0 : lock.getCreationDate().getTime(), 
				lock.getExpirationDate() == null ? 0 : lock.getExpirationDate().getTime());
	}
	
	public static org.apache.subversion.javahl.callback.BlameCallback convert(final ISVNAnnotationCallback cb) {
		if (cb == null) {
			return null;
		}
		return new org.apache.subversion.javahl.callback.BlameCallback() {
			/*
			 * Copied from the LogDate class in order to avoid unsafe usage of a static DateFormat instance in the multi-threaded environment.
			 */
			private DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS z");
			private Calendar date = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
			
		    public void singleLine(long lineNum, long revision, Map<String, byte[]> revProps, long mergedRevision, Map<String, byte[]> mergedRevProps, String mergedPath, String line, boolean localChange) throws ClientException {
		    	Map tRevProps = ConversionUtility.convertRevPropsFromSVN(revProps, this.formatter, this.date);
		    	Map tMergedRevProps = ConversionUtility.convertRevPropsFromSVN(mergedRevProps, this.formatter, this.date);
		    	Date date = tRevProps == null ? null : (Date)tRevProps.get(SVNProperty.BuiltIn.REV_DATE);
		    	String author = tRevProps == null ? null : (String)tRevProps.get(SVNProperty.BuiltIn.REV_AUTHOR);
		    	Date mergedDate = tMergedRevProps == null ? null : (Date)tMergedRevProps.get(SVNProperty.BuiltIn.REV_DATE);
		    	String mergedAuthor = tMergedRevProps == null ? null : (String)tMergedRevProps.get(SVNProperty.BuiltIn.REV_AUTHOR);
				cb.annotate(line, new SVNAnnotationData(lineNum, localChange, revision, date == null ? 0 : date.getTime(), author, mergedRevision, mergedDate == null ? 0 : mergedDate.getTime(), mergedAuthor, mergedPath));
			}
		};
	}
	
	public static SVNRevision convert(org.apache.subversion.javahl.types.Revision rev) {
		if (rev != null) {
			org.apache.subversion.javahl.types.Revision.Kind kind = rev.getKind();
			if (kind == org.apache.subversion.javahl.types.Revision.Kind.base) {
				return SVNRevision.BASE;
			}
			else if (kind == org.apache.subversion.javahl.types.Revision.Kind.committed) {
				return SVNRevision.COMMITTED;
			}
			else if (kind == org.apache.subversion.javahl.types.Revision.Kind.head) {
				return SVNRevision.HEAD;
			}
			else if (kind == org.apache.subversion.javahl.types.Revision.Kind.previous) {
				return SVNRevision.PREVIOUS;
			}
			else if (kind == org.apache.subversion.javahl.types.Revision.Kind.working) {
				return SVNRevision.WORKING;
			}
			else if (kind == org.apache.subversion.javahl.types.Revision.Kind.unspecified) {
				return SVNRevision.START;
			}
			else if (kind == org.apache.subversion.javahl.types.Revision.Kind.number) {
				return SVNRevision.fromNumber(((org.apache.subversion.javahl.types.Revision.Number)rev).getNumber());
			}
			else {
				return SVNRevision.fromDate(((org.apache.subversion.javahl.types.Revision.DateSpec)rev).getDate().getTime());
			}
		}
		return null;
	}
	
	public static org.apache.subversion.javahl.types.Mergeinfo.LogKind convertLogKind(LogKind logKind) {
		return logKind == LogKind.ELIGIBLE ? org.apache.subversion.javahl.types.Mergeinfo.LogKind.eligible : org.apache.subversion.javahl.types.Mergeinfo.LogKind.merged;
	}
	
	public static SVNDepth convertDepth(org.apache.subversion.javahl.types.Depth depth) {
		if (depth == org.apache.subversion.javahl.types.Depth.exclude) {
			return SVNDepth.EXCLUDE;
		}
		if (depth == org.apache.subversion.javahl.types.Depth.empty) {
			return SVNDepth.EMPTY;
		}
		if (depth == org.apache.subversion.javahl.types.Depth.files) {
			return SVNDepth.FILES;
		}
		if (depth == org.apache.subversion.javahl.types.Depth.immediates) {
			return SVNDepth.IMMEDIATES;
		}
		if (depth == org.apache.subversion.javahl.types.Depth.infinity) {
			return SVNDepth.INFINITY;
		}
		return SVNDepth.UNKNOWN;
	}
	
	public static org.apache.subversion.javahl.types.Depth convertDepth(SVNDepth depth) {
		switch (depth) {
			case EXCLUDE: return org.apache.subversion.javahl.types.Depth.exclude;
			case EMPTY: return org.apache.subversion.javahl.types.Depth.empty;
			case FILES: return org.apache.subversion.javahl.types.Depth.files;
			case IMMEDIATES: return org.apache.subversion.javahl.types.Depth.immediates;
			case INFINITY: return org.apache.subversion.javahl.types.Depth.infinity;
			default:
		}
		return org.apache.subversion.javahl.types.Depth.unknown;
	}
	
	public static SVNConflictResolution.Choice convertConflictChoice(org.apache.subversion.javahl.ConflictResult.Choice choice) {
		if (choice == org.apache.subversion.javahl.ConflictResult.Choice.chooseBase) {
			return SVNConflictResolution.Choice.CHOOSE_BASE;
		}
		if (choice == org.apache.subversion.javahl.ConflictResult.Choice.chooseTheirsFull) {
			return SVNConflictResolution.Choice.CHOOSE_REMOTE_FULL;
		}
		if (choice == org.apache.subversion.javahl.ConflictResult.Choice.chooseMineFull) {
			return SVNConflictResolution.Choice.CHOOSE_LOCAL_FULL;
		}
		if (choice == org.apache.subversion.javahl.ConflictResult.Choice.chooseTheirsConflict) {
			return SVNConflictResolution.Choice.CHOOSE_REMOTE;
		}
		if (choice == org.apache.subversion.javahl.ConflictResult.Choice.chooseMineConflict) {
			return SVNConflictResolution.Choice.CHOOSE_LOCAL;
		}
		if (choice == org.apache.subversion.javahl.ConflictResult.Choice.chooseMerged) {
			return SVNConflictResolution.Choice.CHOOSE_MERGED;
		}
		return SVNConflictResolution.Choice.POSTPONE;
	}
	
	public static org.apache.subversion.javahl.ConflictResult.Choice convertConflictChoice(SVNConflictResolution.Choice choice) {
		switch (choice) {
			case CHOOSE_BASE: return org.apache.subversion.javahl.ConflictResult.Choice.chooseBase;
			case CHOOSE_REMOTE_FULL: return org.apache.subversion.javahl.ConflictResult.Choice.chooseTheirsFull;
			case CHOOSE_LOCAL_FULL: return org.apache.subversion.javahl.ConflictResult.Choice.chooseMineFull;
			case CHOOSE_REMOTE: return org.apache.subversion.javahl.ConflictResult.Choice.chooseTheirsConflict;
			case CHOOSE_LOCAL: return org.apache.subversion.javahl.ConflictResult.Choice.chooseMineConflict;
			case CHOOSE_MERGED: return org.apache.subversion.javahl.ConflictResult.Choice.chooseMerged;
			default:
		}
		return org.apache.subversion.javahl.ConflictResult.Choice.postpone;
	}
	
	public static org.apache.subversion.javahl.types.Revision convert(SVNRevision rev) {
		if (rev != null) {
			switch (rev.getKind()) {
	            case BASE: return org.apache.subversion.javahl.types.Revision.BASE;
	            case COMMITTED: return org.apache.subversion.javahl.types.Revision.COMMITTED;
	            case HEAD: return org.apache.subversion.javahl.types.Revision.HEAD;
	            case PREVIOUS: return org.apache.subversion.javahl.types.Revision.PREVIOUS;
	            case WORKING: return org.apache.subversion.javahl.types.Revision.WORKING;
	            case START: return org.apache.subversion.javahl.types.Revision.START;
	            case NUMBER: return org.apache.subversion.javahl.types.Revision.getInstance(((SVNRevision.Number)rev).getNumber());
	            case DATE:
	            default:
	            	return org.apache.subversion.javahl.types.Revision.getInstance(new Date(((SVNRevision.Date)rev).getDate()));
			}
		}
		return null;
	}
	
	public static SVNNotification convert(org.apache.subversion.javahl.ClientNotifyInformation info) {
		return info == null ? null : new SVNNotification(info.getPath(), ConversionUtility.convert(info.getAction()), ConversionUtility.convert(info.getKind()), info.getMimeType(), ConversionUtility.convert(info.getLock()), info.getErrMsg(), ConversionUtility.convert(info.getContentState()), ConversionUtility.convert(info.getPropState()), ConversionUtility.convert(info.getLockState()), info.getRevision());
	}
	
	public static ISVNNotificationCallback convert(org.apache.subversion.javahl.callback.ClientNotifyCallback notify2) {
		return notify2 == null ? null : ((Notify2Wrapper)notify2).getNotify2();
	}
	
	public static org.apache.subversion.javahl.callback.ClientNotifyCallback convert(ISVNNotificationCallback notify2) {
		return notify2 == null ? null : new Notify2Wrapper(notify2);
	}
	
	public static class Notify2Wrapper implements org.apache.subversion.javahl.callback.ClientNotifyCallback {
		protected ISVNNotificationCallback notify;
		
		public Notify2Wrapper(ISVNNotificationCallback notify) {
			this.notify = notify;
		}
		
		public ISVNNotificationCallback getNotify2() {
			return this.notify;
		}
		
		public void onNotify(org.apache.subversion.javahl.ClientNotifyInformation info) {
			this.notify.notify(ConversionUtility.convert(info));
		}
	}
	
	public static String convertZeroCodedLine(String source) {
		if (source != null) {
			byte []data = new byte[source.length()];
			for (int i = 0; i < data.length; i++) {
				data[i] = (byte)source.charAt(i);
			}
			source = new String(data);
		}
		return source;
	}
	
	private ConversionUtility() {
		
	}
}
