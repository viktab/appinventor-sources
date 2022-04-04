// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2020 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.client.wizards;

import static com.google.appinventor.client.Ode.MESSAGES;

import com.google.appinventor.client.Ode;
import com.google.appinventor.client.OdeAsyncCallback;
import com.google.appinventor.client.editor.youngandroid.YaProjectEditor;
import com.google.appinventor.client.explorer.project.Project;
import com.google.appinventor.common.utils.StringUtils;
import com.google.appinventor.client.utils.Uploader;
import com.google.appinventor.shared.rpc.ServerLayout;
import com.google.appinventor.shared.rpc.UploadResponse;
import com.google.appinventor.shared.rpc.api.ApiImportResponse;
import com.google.appinventor.shared.rpc.project.ProjectNode;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidAssetsFolder;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidComponentsFolder;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidProjectNode;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.Command;
import com.google.gwt.cell.client.CheckboxCell;
import com.google.gwt.cell.client.NumberCell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.FileUpload;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TabPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.view.client.ListDataProvider;
import com.google.gwt.view.client.SingleSelectionModel;

import java.util.List;

public class ApiImportWizard extends Wizard {

  public static class ImportApiCallback extends OdeAsyncCallback<ApiImportResponse> {
    @Override
    public void onSuccess(ApiImportResponse response) {
      if (response.getStatus() == ApiImportResponse.Status.FAILED){
        Window.alert(MESSAGES.apiImportError() + "\n" + response.getMessage());
        return;
      }
      else if (response.getStatus() != ApiImportResponse.Status.IMPORTED) {
        Window.alert(MESSAGES.apiImportError());
        return;
      }
      else if (response.getStatus() == ApiImportResponse.Status.UNKNOWN_URL) {
        Window.alert(MESSAGES.componentImportUnknownURLError());
      }

      List<ProjectNode> compNodes = response.getNodes();
      long destinationProjectId = response.getProjectId();
      long currentProjectId = ode.getCurrentYoungAndroidProjectId();
      if (currentProjectId != destinationProjectId) {
        return; // User switched project early!
      }
      Project project = ode.getProjectManager().getProject(destinationProjectId);
      if (project == null) {
        return; // Project does not exist!
      }
      if (response.getStatus() == ApiImportResponse.Status.IMPORTED) {
        // TODO : add the API??
        Ode.CLog("successfuly imported!!");

        // YoungAndroidComponentsFolder componentsFolder = ((YoungAndroidProjectNode) project.getRootNode()).getComponentsFolder();
        // YaProjectEditor projectEditor = (YaProjectEditor) ode.getEditorManager().getOpenProjectEditor(destinationProjectId);
        // if (projectEditor == null) {
        //   return; // Project is not open!
        // }
        // for (ProjectNode node : compNodes) {
        //   project.addNode(componentsFolder, node);
        //   if ((node.getName().equals("component.json") || node.getName().equals("components.json"))
        //       && StringUtils.countMatches(node.getFileId(), "/") == 3) {
        //     projectEditor.addComponent(node, null);
        //   }
        // }
      }
    }
  }

  private static int FROM_MY_COMPUTER_TAB = 0;
  private static int URL_TAB = 1;

  private static final String API_FILE_EXTENSION = ".txt,.pdf";

  private static final Ode ode = Ode.getInstance();

  public ApiImportWizard() {
    super(MESSAGES.apiImportWizardCaption(), true, false);

    // final CellTable compTable = createCompTable();
    final FileUpload fileUpload = createFileUpload();
    final Grid urlGrid = createUrlGrid();
    final TabPanel tabPanel = new TabPanel();
    tabPanel.add(fileUpload, MESSAGES.componentImportFromComputer());
    tabPanel.add(urlGrid, MESSAGES.componentImportFromURL());
    tabPanel.selectTab(FROM_MY_COMPUTER_TAB);
    tabPanel.addStyleName("ode-Tabpanel");

    VerticalPanel panel = new VerticalPanel();
    panel.add(tabPanel);

    addPage(panel);

    getConfirmButton().setText("Import");

    setPagePanelHeight(150);
    setPixelSize(200, 150);
    setStylePrimaryName("ode-DialogBox");

    initFinishCommand(new Command() {
      @Override
      public void execute() {
        final long projectId = ode.getCurrentYoungAndroidProjectId();
        final Project project = ode.getProjectManager().getProject(projectId);
        final YoungAndroidAssetsFolder assetsFolderNode =
            ((YoungAndroidProjectNode) project.getRootNode()).getAssetsFolder();

        if (tabPanel.getTabBar().getSelectedTab() == URL_TAB) {
          TextBox urlTextBox = (TextBox) urlGrid.getWidget(1, 0);
          String url = urlTextBox.getText();

          if (url.trim().isEmpty()) {
            Window.alert(MESSAGES.noUrlError());
            return;
          }

          // TODO : import the API!!
          Ode.CLog("got url but haven't done anything with it");
          Ode.CLog(url);
          ode.getApiService().importApiToProject(url, projectId,
            assetsFolderNode.getFileId(), new ImportApiCallback());
        } else if (tabPanel.getTabBar().getSelectedTab() == FROM_MY_COMPUTER_TAB) {
        //   if (!fileUpload.getFilename().endsWith(COMPONENT_ARCHIVE_EXTENSION)) {
        //     Window.alert(MESSAGES.notComponentArchiveError());
        //     return;
        //   }

          String url = GWT.getModuleBaseURL() +
            ServerLayout.UPLOAD_SERVLET + "/" +
            ServerLayout.UPLOAD_API + "/" +
            trimLeadingPath(fileUpload.getFilename());
          
          Ode.CLog("got file... uploading!");
          Ode.CLog(url);

          Uploader.getInstance().upload(fileUpload, url,
            new OdeAsyncCallback<UploadResponse>() {
              @Override
              public void onSuccess(UploadResponse uploadResponse) {
                String toImport = uploadResponse.getInfo();
                Ode.CLog("upload response");
                Ode.CLog(toImport);
                ode.getApiService().importApiToProject(toImport, projectId,
                    assetsFolderNode.getFileId(), new ImportApiCallback());
              }
            });
          return;
        }
      }

      private String trimLeadingPath(String filename) {
        // Strip leading path off filename.
        // We need to support both Unix ('/') and Windows ('\\') separators.
        return filename.substring(Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\')) + 1);
      }
    });
  }

  private Grid createUrlGrid() {
    TextBox urlTextBox = new TextBox();
    urlTextBox.setWidth("100%");
    Grid grid = new Grid(2, 1);
    grid.setWidget(0, 0, new Label("Url:"));
    grid.setWidget(1, 0, urlTextBox);
    return grid;
  }

  private FileUpload createFileUpload() {
    FileUpload upload = new FileUpload();
    upload.setName(ServerLayout.UPLOAD_API_ARCHIVE_FORM_ELEMENT);
    upload.getElement().setAttribute("accept", API_FILE_EXTENSION);
    return upload;
  }

}
