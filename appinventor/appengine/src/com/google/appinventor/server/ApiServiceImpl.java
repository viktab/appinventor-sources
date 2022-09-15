package com.google.appinventor.server;

import com.google.appinventor.server.storage.StorageIo;
import com.google.appinventor.server.storage.StorageIoInstanceHolder;
import com.google.appinventor.shared.rpc.api.ApiService;
import com.google.appinventor.shared.rpc.api.ApiImportResponse;
import com.google.appinventor.shared.rpc.api.ApiImportResponse.Status;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidComponentNode;
import com.google.appinventor.shared.storage.StorageUtil;
import com.google.appinventor.shared.rpc.project.FileNode;
import com.google.appinventor.shared.rpc.project.ProjectNode;

import com.google.common.io.ByteStreams;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import org.yaml.snakeyaml.Yaml;

public class ApiServiceImpl extends OdeRemoteServiceServlet
    implements ApiService {

    private static final Logger LOG = Logger.getLogger(ApiServiceImpl.class.getName());

    private final transient StorageIo storageIo = StorageIoInstanceHolder.getInstance();

    private final FileImporter fileImporter = new FileImporterImpl();

    private final String apiFolder = "/api_comps";

    private String subDirectory = "";

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

    private static final Map<String, String> operationTypesPassedTense = new HashMap<String, String>();
    static {
        operationTypesPassedTense.put("get", "got");
        operationTypesPassedTense.put("put", "put");
        operationTypesPassedTense.put("post", "posted");
        operationTypesPassedTense.put("delete", "deleted");
        operationTypesPassedTense.put("options", "gotOptions");
        operationTypesPassedTense.put("head", "putHead");
        operationTypesPassedTense.put("patch", "patched");
        operationTypesPassedTense.put("trace", "gotTrace");
    }

    @Override
    public ApiImportResponse importApiToProject(String fileOrUrl, long projectId, String folderPath, String fileType) {

        ApiImportResponse response = new ApiImportResponse(ApiImportResponse.Status.FAILED);
        response.setProjectId(projectId);

        Map<String, byte[]> contents;
        String fileNameToDelete = null;
        try {
            if (fileOrUrl.startsWith("__TEMP__")) {
                fileNameToDelete = fileOrUrl;
                contents = extractContents(storageIo.openTempFile(fileOrUrl), fileType);
            } else {
                URL compUrl = new URL(fileOrUrl);
                contents = extractContents(compUrl.openStream(), fileType);
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

    private Map<String, byte[]> extractContents(InputStream inputStream, String fileType) throws IOException {
        Map<String, byte[]> contents = new HashMap<String, byte[]>();

        StringBuilder sb = new StringBuilder();
        for (int ch; (ch = inputStream.read()) != -1; ) {
            sb.append((char) ch);
        } if (fileType.equals("JSON")) {
            String jsonStr = sb.toString();
            JSONObject json = new JSONObject(jsonStr);
            byte[] components = defineJSONBlocks(json);
            contents.put("components.json", components);
        } else {
            String yamlStr = sb.toString();
            Yaml yaml = new Yaml();
            Map<String, Object> obj = yaml.load(yamlStr);
            byte[] components = defineYAMLBlocks(obj);
            contents.put("components.json", components);
        }

        // TODO fill this later
        byte[] build_info = new byte[0];
        contents.put("files/component_build_infos.json", build_info);

        return contents;
    }

    // converts the OpenAPI spec into a components.json
    // OpenAPI spec: https://swagger.io/specification/
    private byte[] defineJSONBlocks(JSONObject apiJSON) {
        JSONArray componentsJSON = new JSONArray();
        JSONObject componentJSON = new JSONObject();
        JSONObject functionJSON = new JSONObject();
        componentJSON.put("nonVisible", "true");
        componentJSON.put("version", "1");
        componentJSON.put("external", "true");
        componentJSON.put("categoryString", "API");
        componentJSON.put("helpString", "");
        componentJSON.put("showOnPalette", "true");
        componentJSON.put("iconName", "ball.png"); // TODO update
        componentJSON.put("type", "text"); // TODO make this API-specific
        componentJSON.put("isAPI", "true");

        JSONObject infoObj = apiJSON.getJSONObject("info");
        String name = infoObj.getString("title");
        componentJSON.put("name", name);

        JSONArray servers = apiJSON.getJSONArray("servers");
        JSONObject serverObj = servers.getJSONObject(0);
        String serverUrl = serverObj.getString("url");
        functionJSON.put("serverUrl", serverUrl);
        String[] urlParts = serverUrl.split("/");
        subDirectory = "/" + urlParts[2] + "/";
        LOG.info("subDirectory: " + subDirectory);

        // in theory every path could be its own component, I can try both options later
        JSONArray methods = new JSONArray();
        JSONArray events = new JSONArray();
        JSONArray methodsCode = new JSONArray();
        JSONObject pathsObj = apiJSON.getJSONObject("paths");
        Set pathSet = pathsObj.keySet();
        Iterator<String> pathItr = pathSet.iterator();
        while (pathItr.hasNext()) {
            String path = pathItr.next();
            String[] pathParts = path.split("/");
            JSONArray params = new JSONArray();
            int j = 0;
            for (String part : pathParts) {
                if (j == 0) {
                    j++;
                    continue;
                }
                if (part.charAt(0) == '{' && part.charAt(part.length()-1) == '}') {
                    String paramName = part.substring(1, part.length()-1);
                    JSONObject param = new JSONObject();
                    param.put("name", paramName);
                    param.put("type", "text");
                    params.put(param);
                }
            }
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
                JSONObject operationCode = new JSONObject();
                operationCode.put("OPType", key);
                operationCode.put("path", path);
                // operationID not required, might need to find another way to name operations without it
                String operationID = operationObj.getString("operationId");
                String operationName = key + "_" + operationID;
                operation.put("name", operationName);
                operationCode.put("name", operationName);
                String description = operationObj.getString("description");
                operation.put("description", description);
                // TODO add more info to the description
                try {
                    Boolean deprecated = operationObj.getBoolean("deprecated");
                    operation.put("deprecated", String.valueOf(deprecated).toLowerCase());
                } catch (Exception e) {
                    operation.put("deprecated", "false");
                }
                JSONArray allParams = new JSONArray(params.toString());
                try {
                    JSONArray paramsList = operationObj.getJSONArray("parameters");
                    int numParams = paramsList.length();
                    for (int i = 0; i < numParams; i++) {
                        JSONObject paramObj = paramsList.getJSONObject(i);
                        String paramName = paramObj.getString("name");
                        JSONObject param = new JSONObject();
                        param.put("name", paramName);
                        param.put("type", "text");
                        allParams.put(param);
                    }
                } catch (JSONException e) {
                }
                operation.put("params", allParams);
                methods.put(operation);
                JSONObject event = new JSONObject();
                String operationTypePassedTense = operationTypesPassedTense.get(key);
                String eventName = operationTypePassedTense + "_" + operationID;
                event.put("name", eventName);
                String eventDesc = "Got response from API call. API call description: \n" + description;
                event.put("description", eventDesc);
                String deprecated = operation.getString("deprecated");
                event.put("deprecated", deprecated);
                JSONArray eventParams = new JSONArray();
                JSONObject eventParam = new JSONObject();
                eventParam.put("name", "response");
                eventParam.put("type", "text");
                eventParams.put(eventParam);
                event.put("params", eventParams);
                events.put(event);
                operationCode.put("params", allParams);
                methodsCode.put(operationCode);
            }
        }
        componentJSON.put("methods", methods);
        componentJSON.put("events", events);

        JSONArray properties = new JSONArray();
        componentJSON.put("properties", properties);
        JSONArray blockProperties = new JSONArray();
        componentJSON.put("blockProperties", blockProperties);
        functionJSON.put("functions", methodsCode);
        String APICodeStr = functionJSON.toString();
        componentJSON.put("APICode", APICodeStr);

        componentsJSON.put(componentJSON);

        String componentsStr = componentsJSON.toString();
        LOG.info("component string");
        LOG.info(componentsStr);
        return componentsStr.getBytes();
    }

    private byte[] defineYAMLBlocks(Map<String, Object> map) {
        JSONObject json = new JSONObject(map);
        byte[] components = defineJSONBlocks(json);
        return components;
    }

    private void importToProject(Map<String, byte[]> contents, long projectId,
      String folderPath, ApiImportResponse response) throws FileImporterException, IOException {
        // TODO convert API to blocks?
        LOG.info("will import the API here eventually");
        Status status = Status.IMPORTED;
        final String userId = userInfoProvider.getUserId();
        final String basepath = folderPath + apiFolder + subDirectory;
         Map<String, String> nameMap = buildExtensionPathnameMap(contents.keySet());

        // Does the extension contain a file that could be a component descriptor file?
        if (!nameMap.containsKey("component.json") && !nameMap.containsKey("components.json")) {
            response.setMessage("Uploaded file does not contain any component definition files.");
            return;
        }

        JSONArray newComponents = readComponents(contents.get(nameMap.get("components.json")));
        if (newComponents == null || newComponents.length() == 0) {
            response.setMessage("No valid component descriptors found in the extension.");
            return;
        }

        // Write new extension files
        List<ProjectNode> compNodes = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : contents.entrySet()) {
            String dest = basepath + entry.getKey();
            FileNode fileNode = new YoungAndroidComponentNode(StorageUtil.basename(entry.getKey()), dest);
            fileImporter.importFile(userId, projectId, dest, new ByteArrayInputStream(entry.getValue()));
            compNodes.add(fileNode);
        }

        // Extract type map to send to clients
        Map<String, String> types = new TreeMap<>();
        for (int i = 0; i < newComponents.length(); i++) {
            JSONObject desc = newComponents.getJSONObject(i);
            types.put(desc.getString("type"), desc.getString("name"));
        }

        response.setStatus(status);
        response.setComponentTypes(types);
        response.setNodes(compNodes);
    }

    private static JSONArray readComponents(String content) {
        content = content.trim();  // remove extraneous whitespace
        if (content.startsWith("{") && content.endsWith("}")) {
            return new JSONArray("[" + content + "]");
        } else if (content.startsWith("[") && content.endsWith("]")) {
            return new JSONArray(content);
        } else {
            // content is neither a JSONObject {...} nor a JSONArray [...]. This is an error state.
            throw new IllegalArgumentException("Content was not a valid component descriptor file");
        }
    }
    
    private static Map<String, String> buildExtensionPathnameMap(Set<String> paths) {
        Map<String, String> result = new HashMap<>();
        for (String name : paths) {
            result.put(StorageUtil.basename(name), name);
        }
        return result;
    }

    private static JSONArray readComponents(byte[] content) throws UnsupportedEncodingException {
        return readComponents(new String(content, StorageUtil.DEFAULT_CHARSET));
    }

    private String collectImportErrorInfo(String path, long projectId) {
        return "Error importing " + path + " to project " + projectId;
    }

}