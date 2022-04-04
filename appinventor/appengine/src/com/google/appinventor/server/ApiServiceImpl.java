package com.google.appinventor.server;

import com.google.appinventor.server.storage.StorageIo;
import com.google.appinventor.server.storage.StorageIoInstanceHolder;
import com.google.appinventor.shared.rpc.api.ApiService;
import com.google.appinventor.shared.rpc.api.ApiImportResponse;
import com.google.appinventor.shared.rpc.api.ApiImportResponse.Status;

import com.google.common.io.ByteStreams;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ApiServiceImpl extends OdeRemoteServiceServlet
    implements ApiService {

    private static final Logger LOG = Logger.getLogger(ApiServiceImpl.class.getName());

    private final transient StorageIo storageIo = StorageIoInstanceHolder.getInstance();

    private final FileImporter fileImporter = new FileImporterImpl();

    @Override
    public ApiImportResponse importApiToProject(String fileOrUrl, long projectId, String folderPath) {

        ApiImportResponse response = new ApiImportResponse(ApiImportResponse.Status.FAILED);
        response.setProjectId(projectId);

        Map<String, byte[]> contents;
        String fileNameToDelete = null;
        try {
            if (fileOrUrl.startsWith("__TEMP__")) {
                fileNameToDelete = fileOrUrl;
                contents = extractContents(storageIo.openTempFile(fileOrUrl));
            } else {
                URL compUrl = new URL(fileOrUrl);
                contents = extractContents(compUrl.openStream());
            }
            importToProject(contents, projectId, folderPath, response);
            return response;
        } catch (FileImporterException | IOException | JSONException | IllegalArgumentException e) {
            response.setStatus(Status.FAILED);
            response.setMessage(e.getMessage());
            return response;
        } finally {
            if (fileNameToDelete != null) {
                try {
                    storageIo.deleteTempFile(fileNameToDelete);
                } catch (Exception e) {
                    throw CrashReport.createAndLogError(LOG, null,
                    collectImportErrorInfo(fileOrUrl, projectId), e);
                }
            }
        }
    }

    @Override
    public void deleteImportedApi(String fullyQualifiedName, long projectId) {
        // TODO: delete Api??
        LOG.info("delete API unimplemented");
    }

    @Override
    public void renameImportedApi(String fullyQualifiedName, String newName, long projectId) {
        // TODO: rename Api??
        LOG.info("rename API unimplemented");
    }

    private Map<String, byte[]> extractContents(InputStream inputStream)
      throws IOException {
        Map<String, byte[]> contents = new HashMap<String, byte[]>();

        // assumption: the zip is non-empty
        ZipInputStream zip = new ZipInputStream(inputStream);
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            if (entry.isDirectory())  continue;
            ByteArrayOutputStream contentStream = new ByteArrayOutputStream();
            ByteStreams.copy(zip, contentStream);
            contents.put(entry.getName(), contentStream.toByteArray());
            }
        zip.close();

        return contents;
    }

    private void importToProject(Map<String, byte[]> contents, long projectId,
      String folderPath, ApiImportResponse response) throws FileImporterException, IOException {
        // TODO convert API to blocks?
        LOG.info("will import the API here eventually");
        Status status = Status.IMPORTED;
        response.setStatus(status);
    }

    private String collectImportErrorInfo(String path, long projectId) {
        return "Error importing " + path + " to project " + projectId;
    }

}