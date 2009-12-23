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

package com.google.gerrit.server.schema;

import com.google.gerrit.reviewdb.CurrentSchemaVersion;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

class Schema_19 extends SchemaVersion {
  @Inject
  Schema_19() {
    super(new Provider<SchemaVersion>() {
      public SchemaVersion get() {
        throw new ProvisionException("Cannot upgrade from 18");
      }
    });
  }

  @Override
  protected void upgradeFrom(UpdateUI ui, CurrentSchemaVersion curr,
      ReviewDb db) throws OrmException {
    throw new OrmException("Cannot upgrade from " + curr.versionNbr
        + "; manually run scripts from"
        + " http://gerrit.googlecode.com/files/schema-upgrades003_019.zip"
        + " and restart.");
  }
}
