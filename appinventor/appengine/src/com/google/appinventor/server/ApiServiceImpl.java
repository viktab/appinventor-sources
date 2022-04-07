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
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import org.json.JSONObject;

public class ApiServiceImpl extends OdeRemoteServiceServlet
    implements ApiService {

    private static final Logger LOG = Logger.getLogger(ApiServiceImpl.class.getName());

    private final transient StorageIo storageIo = StorageIoInstanceHolder.getInstance();

    private final FileImporter fileImporter = new FileImporterImpl();

    private static final Set<String> operationTypes = new HashSet<String>();
    static {
        operationTypes.add("get");
        operationTypes.add("put");
        operationTypes.add("post");
        operationTypes.add("delete");
        operationTypes.add("options");
        operationTypes.add("head");
        operationTypes.add("patch");
        operationTypes.add("trace");
    }

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
        LOG.info("extracting contents");
        StringBuilder sb = new StringBuilder();
        for (int ch; (ch = inputStream.read()) != -1; ) {
            sb.append((char) ch);
        }
        String jsonStr = sb.toString();
        JSONObject json = new JSONObject(jsonStr);
        byte[] components = defineBlocks(json);

        Map<String, byte[]> contents = new HashMap<String, byte[]>();

        // assumption: the zip is non-empty
        ZipInputStream zip = new ZipInputStream(inputStream);
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            if (entry.isDirectory())  continue;
            ByteArrayOutputStream contentStream = new ByteArrayOutputStream();
            ByteStreams.copy(zip, contentStream);
            LOG.info("stream:");
            LOG.info(contentStream.toString());
            contents.put(entry.getName(), contentStream.toByteArray());
            }
        zip.close();

        return contents;
    }

    // converts the OpenAPI spec into a components.json
    // OpenAPI spec: https://swagger.io/specification/
    private byte[] defineBlocks(JSONObject apiJSON) {
        JSONObject componentsJSON = new JSONObject();
        componentsJSON.put("nonVisible", "true");

        JSONObject infoObj = apiJSON.getJSONObject("info");
        String name = infoObj.getString("title");
        componentsJSON.put("name", name);

        // in theory every path could be its own component, I can try both options later
        JSONArray methods = new JSONArray();
        JSONObject pathsObj = apiJSON.getJSONObject("paths");
        Set pathSet = pathsObj.keySet();
        Iterator<String> pathItr = pathSet.iterator();
        while (pathItr.hasNext()) {
            String path = pathItr.next();
            JSONObject pathObj = pathsObj.getJSONObject(path);

            Set keySet = pathObj.keySet();
            Iterator<String> keyItr = keySet.iterator();
            while (keyItr.hasNext()) {
                String key = keyItr.next();
                // Only handle operations for now, might add other path info later
                if (!operationTypes.contains(key)) {
                    continue;
                }
                JSONObject operation = new JSONObject();
                JSONObject operationObj = pathObj.getJSONObject(key);
                // operationID not required, might need to find another way to name operations without it
                String operationID = operationObj.getString("operationId");
                String operationName = key + "_" + operationID;
                operation.put("name", operationName);
                String description = operationObj.getString("description");
                operation.put("description", description);
                try {
                    Boolean deprecated = operationObj.getBoolean("deprecated");
                    operation.put("deprecated", String.valueOf(deprecated).toLowerCase());
                } catch (Exception e) {
                    operation.put("deprecated", "false");
                }
                // TODO: add more operation info (this is just enough to make some blocks for now)
                methods.put(operation);
            }
        }
        componentsJSON.put("methods", methods);

        String componentsStr = componentsJSON.toString();
        LOG.info("component string");
        LOG.info(componentsStr);
        return componentsStr.getBytes();
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