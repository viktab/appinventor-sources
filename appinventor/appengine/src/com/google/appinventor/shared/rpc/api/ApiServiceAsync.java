// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2015 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.shared.rpc.api;

import com.google.gwt.user.client.rpc.AsyncCallback;

import java.util.List;

public interface ApiServiceAsync {

  /**
   * @see ApiService#importApiToProject(String, long, String, String)
   */
  void importApiToProject(String forOrUrl, long projectId, String folderPath, String fileType,
      AsyncCallback<ApiImportResponse> callback);

  /**
   * @see ApiService#renameImportedApi(String, String, long)
   */
  void renameImportedApi(String fullyQualifiedName, String newName,
      long projectId, AsyncCallback<Void> callback);

  /**
   * @see ApiService#deleteImportedApi(String, long)
   */
   void deleteImportedApi(String fullyQualifiedName, long projectId, AsyncCallback<Void> callback);
}
