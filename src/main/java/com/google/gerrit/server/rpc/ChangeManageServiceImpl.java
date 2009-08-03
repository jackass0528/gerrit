// Copyright (C) 2009 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.rpc;

import com.google.gerrit.client.changes.ChangeManageService;
import com.google.gerrit.client.data.ApprovalType;
import com.google.gerrit.client.data.GerritConfig;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.ChangeApproval;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.git.MergeQueue;
import com.google.gerrit.server.BaseServiceImplementation;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.workflow.CategoryFunction;
import com.google.gerrit.server.workflow.FunctionState;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.gwtorm.client.Transaction;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class ChangeManageServiceImpl extends BaseServiceImplementation implements
    ChangeManageService {
  private final MergeQueue merger;
  private final GerritConfig gerritConfig;

  @Inject
  ChangeManageServiceImpl(final SchemaFactory<ReviewDb> sf,
      final MergeQueue mq, final GerritConfig gc) {
    super(sf);
    merger = mq;
    gerritConfig = gc;
  }

  public void patchSetAction(final ApprovalCategoryValue.Id value,
      final PatchSet.Id patchSetId, final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException, Failure {
        final Change change = db.changes().get(patchSetId.getParentKey());
        if (change == null) {
          throw new Failure(new NoSuchEntityException());
        }

        if (!patchSetId.equals(change.currentPatchSetId())) {
          throw new Failure(new IllegalStateException("Patch set " + patchSetId
              + " not current"));
        }
        if (change.getStatus().isClosed()) {
          throw new Failure(new IllegalStateException("Change" + change.getId()
              + " is closed"));
        }

        final List<ChangeApproval> allApprovals =
            new ArrayList<ChangeApproval>(db.changeApprovals().byChange(
                change.getId()).toList());

        final Account.Id me = Common.getAccountId();
        final ChangeApproval.Key ak =
            new ChangeApproval.Key(change.getId(), me, value.getParentKey());
        ChangeApproval myAction = null;
        boolean isnew = true;
        for (final ChangeApproval ca : allApprovals) {
          if (ak.equals(ca.getKey())) {
            isnew = false;
            myAction = ca;
            myAction.setValue(value.get());
            myAction.setGranted();
            break;
          }
        }
        if (myAction == null) {
          myAction = new ChangeApproval(ak, value.get());
          allApprovals.add(myAction);
        }

        final ApprovalType actionType =
            gerritConfig.getApprovalType(myAction.getCategoryId());
        if (actionType == null || !actionType.getCategory().isAction()) {
          throw new Failure(new IllegalArgumentException(actionType
              .getCategory().getName()
              + " not an action"));
        }

        final FunctionState fs = new FunctionState(change, allApprovals);
        for (ApprovalType c : gerritConfig.getApprovalTypes()) {
          CategoryFunction.forCategory(c.getCategory()).run(c, fs);
        }
        if (!CategoryFunction.forCategory(actionType.getCategory()).isValid(me,
            actionType, fs)) {
          throw new Failure(new IllegalStateException(actionType.getCategory()
              .getName()
              + " not permitted"));
        }
        fs.normalize(actionType, myAction);
        if (myAction.getValue() <= 0) {
          throw new Failure(new IllegalStateException(actionType.getCategory()
              .getName()
              + " not permitted"));
        }

        if (ApprovalCategory.SUBMIT.equals(actionType.getCategory().getId())) {
          if (change.getStatus() == Change.Status.NEW) {
            change.setStatus(Change.Status.SUBMITTED);
            ChangeUtil.updated(change);
          }
        } else {
          throw new Failure(new IllegalArgumentException(actionType
              .getCategory().getName()
              + " cannot be perfomed by Gerrit"));
        }

        final Transaction txn = db.beginTransaction();
        db.changes().update(Collections.singleton(change), txn);
        if (change.getStatus().isClosed()) {
          db.changeApprovals().update(fs.getDirtyChangeApprovals(), txn);
        }
        if (isnew) {
          db.changeApprovals().insert(Collections.singleton(myAction), txn);
        } else {
          db.changeApprovals().update(Collections.singleton(myAction), txn);
        }
        txn.commit();

        if (change.getStatus() == Change.Status.SUBMITTED) {
          merger.merge(change.getDest());
        }

        return VoidResult.INSTANCE;
      }
    });
  }
}
