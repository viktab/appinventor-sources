// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2015 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.shared.rpc.api;

import com.google.appinventor.shared.rpc.project.ProjectNode;
import com.google.gwt.user.client.rpc.IsSerializable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API Import Response to Send Failure Reasons
 */
public class ApiImportResponse implements IsSerializable{

  public enum Status {
    IMPORTED,
    NOT_GCS,
    UNKNOWN_URL,
    FAILED,

    /**
     * BUNDLE_DOWNGRADE is a special failure case when the user attempts to upload an extension
     * that would be an upgrade but the set of extensions in the new bundle is not a superset of
     * the set of extensions in the old bundle.
     *
     * For example, the currently loaded bundle has classes A, B, C and the newly uploaded bundle
     * contains B, C, D. This is an error state because we don't want to trigger a delete of all
     * of the user's blocks associated with class A.
     */
    BUNDLE_DOWNGRADE
  }

  private Status status;
  private long projectId; // necessary to ensure right project
  private List<ProjectNode> nodes; // Added Nodes
  private Map<String, String> types;
  private String message;

  public ApiImportResponse(Status status, long projectId, Map<String, String> types, List<ProjectNode> nodes) {
    this.status = status;
    this.projectId = projectId;
    this.nodes = nodes;
    this.types = types;
  }

  public ApiImportResponse(Status status) {
    this(status, 0, new HashMap<String, String>(), null);
  }

  private ApiImportResponse() {
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public long getProjectId() {
    return projectId;
  }

  public void setProjectId(long projectId){
    this.projectId = projectId;
  }

  public List<ProjectNode> getNodes() {
    if (nodes == null) {
      nodes = new ArrayList<ProjectNode>();
    }
    return nodes;
  }

  public void setNodes(List<ProjectNode> nodes) {
    this.nodes = nodes;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

}
