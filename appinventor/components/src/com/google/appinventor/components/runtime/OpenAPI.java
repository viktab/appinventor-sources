// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.components.runtime;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.YaVersion;

import com.google.appinventor.components.runtime.collect.Lists;
import com.google.appinventor.components.runtime.collect.Maps;

import com.google.appinventor.components.runtime.errors.DispatchableError;
import com.google.appinventor.components.runtime.errors.PermissionException;
import com.google.appinventor.components.runtime.errors.RequestTimeoutException;

import com.google.appinventor.components.runtime.util.AsynchUtil;
import com.google.appinventor.components.runtime.util.ErrorMessages;
import com.google.appinventor.components.runtime.util.FileUtil;
import com.google.appinventor.components.runtime.util.YailList;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URL;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import com.google.appinventor.components.runtime.util.SdkLevel;

/**
 * Button with the ability to detect clicks. Many aspects of its appearance can be changed, as well
 * as whether it is clickable (`Enabled`). Its properties can be changed in the Designer or in the
 * Blocks Editor.
 */
@DesignerComponent(version = YaVersion.OPENAPI_COMPONENT_VERSION,
    category = ComponentCategory.API,
    nonVisible = true,
    showOnPalette = false,
    description = "OpenAPI component")
@SimpleObject
public final class OpenAPI extends AndroidNonvisibleComponent implements Component {

  /**
   * InvalidRequestHeadersException can be thrown from processRequestHeaders.
   * It is thrown if the list passed to processRequestHeaders contains an item that is not a list.
   * It is thrown if the list passed to processRequestHeaders contains an item that is a list whose
   * size is not 2.
   */
  private static class InvalidRequestHeadersException extends Exception {
    /*
     * errorNumber could be:
     * ErrorMessages.ERROR_WEB_REQUEST_HEADER_NOT_LIST
     * ErrorMessages.ERROR_WEB_REQUEST_HEADER_NOT_TWO_ELEMENTS
     */
    final int errorNumber;
    final int index;         // the index of the invalid header

    InvalidRequestHeadersException(int errorNumber, int index) {
      super();
      this.errorNumber = errorNumber;
      this.index = index;
    }
  }

    private int notifierLength = Component.TOAST_LENGTH_LONG;
    private final Activity activity;
    private final Handler handler;

  /**
   * Creates a new OpenAPI component.
   *
   * @param container container, component will be placed in
   */
  public OpenAPI(ComponentContainer container) {
    super(container.$form());
    activity = container.$context();
    handler = new Handler();
  }

  /**
   * The CapturedProperties class captures the current property values from a Web component before
   * an asynchronous request is made. This avoids concurrency problems if the user changes a
   * property value after initiating an asynchronous request.
   */
  private static class CapturedProperties {
    final String urlString;
    final URL url;
    final int timeout;
    final Map<String, List<String>> requestHeaders;

    CapturedProperties(String urlString, int timeout, Map<String, List<String>> requestHeaders) throws MalformedURLException, InvalidRequestHeadersException {
      this.urlString = urlString;
      this.url = new URL(urlString);
      this.timeout = timeout;
      this.requestHeaders = requestHeaders;
    }
  }

  private static final String LOG_TAG = "OpenAPI";

  @SimpleFunction
  public void invokeAPI(final String apiStr) {
    JSONObject apiJson = new JSONObject(apiStr);
    String serverURL = apiJson.getString("serverUrl");
    JSONObject functionJson = apiJson.getJSONObject("funcInfo");
    String path = functionJson.getString("path");
    String functionName = functionJson.getString("name");
    String url = serverURL + path;

    final String METHOD = "Get";
    Map<String, List<String>> requestHeadersMap = Maps.newHashMap();
    List<String> userAgentList = new ArrayList<>();
    userAgentList.add("AppInventor");
    requestHeadersMap.put("User-Agent", userAgentList);

    try {
      final CapturedProperties webProps = new CapturedProperties(url, 10000, requestHeadersMap);
      AsynchUtil.runAsynchronously(new Runnable() {
        @Override
        public void run() {
          performRequest(webProps, null, "GET", METHOD);
        }
      });
    } catch (MalformedURLException e) {
      form.dispatchErrorOccurredEvent(this, functionName,
          ErrorMessages.ERROR_WEB_MALFORMED_URL, url);
    } catch (InvalidRequestHeadersException e) {
      form.dispatchErrorOccurredEvent(this, functionName, e.errorNumber, e.index);
    }

    handler.post(new Runnable() {
        public void run() {
        toastNow(apiStr);
        }
    });
  }

  @SimpleEvent 
  public void GotResponse(String response) {
    EventDispatcher.dispatchEvent(this, "GotResponse", response);
  }

  /**
   * Event indicating that a request has timed out.
   *
   * @param url the URL used for the request
   */
  @SimpleEvent
  public void TimedOut(String url) {
    // invoke the application's "TimedOut" event handler.
    EventDispatcher.dispatchEvent(this, "TimedOut", url);
  }

  /*
   * Perform a HTTP GET or POST request.
   * This method is always run on a different thread than the event thread. It does not use any
   * property value fields because the properties may be changed while it is running. Instead, it
   * uses the parameters.
   * If either postData or postFile is non-null, then a post request is performed.
   * If both postData and postFile are non-null, postData takes precedence over postFile.
   * If postData and postFile are both null, then a get request is performed.
   * the GotText event will be triggered.
   *
   * This method can throw an IOException. The caller is responsible for catching it and
   * triggering the appropriate error event.
   *
   * @param webProps the captured property values needed for the request
   * @param postData the data for the post request if it is not coming from a file, can be null
   *
   * @throws IOException
   */
  private void performRequest(final CapturedProperties webProps, final byte[] postData,
      final String httpVerb, final String method) {

    try {
      // Open the connection.
      HttpURLConnection connection = openConnection(webProps, httpVerb);
      if (connection != null) {
        try {
          if (postData != null) {
            writeRequestData(connection, postData);
          }

          // Get the response.
          final int responseCode = connection.getResponseCode();
          final String responseType = getResponseType(connection);

          
          final String responseContent = getResponseContent(connection);

          // Dispatch the event.
          activity.runOnUiThread(new Runnable() {
              @Override
              public void run() {
                GotResponse(responseContent);
              }
            });

        } catch (SocketTimeoutException e) {
          // Dispatch timeout event.
          activity.runOnUiThread(new Runnable() {
              @Override
              public void run() {
                TimedOut(webProps.urlString);
              }
            });
          throw new RequestTimeoutException();
        } finally {
          connection.disconnect();
        }
      }
    } catch (PermissionException e) {
      form.dispatchPermissionDeniedEvent(OpenAPI.this, method, e);
    } catch (FileUtil.FileException e) {
      form.dispatchErrorOccurredEvent(OpenAPI.this, method,
          e.getErrorMessageNumber());
    } catch (DispatchableError e) {
      form.dispatchErrorOccurredEvent(OpenAPI.this, method, e.getErrorCode(), e.getArguments());
    } catch (RequestTimeoutException e) {
      form.dispatchErrorOccurredEvent(OpenAPI.this, method,
          ErrorMessages.ERROR_WEB_REQUEST_TIMED_OUT, webProps.urlString);
    } catch (Exception e) {
      int message;
      String[] args;
      //noinspection IfCanBeSwitch
      if (method.equals("Get")) {
        message = ErrorMessages.ERROR_WEB_UNABLE_TO_GET;
        args = new String[] { webProps.urlString };
      } else if (method.equals("Delete")) {
        message = ErrorMessages.ERROR_WEB_UNABLE_TO_DELETE;
        args = new String[] { webProps.urlString };
      } else {
        message = ErrorMessages.ERROR_WEB_UNABLE_TO_MODIFY_RESOURCE;
        String content = "";
        try {
          if (postData != null) {
            //noinspection CharsetObjectCanBeUsed
            content = new String(postData, "UTF-8");
          }
        } catch (UnsupportedEncodingException e1) {
          Log.e(LOG_TAG, "UTF-8 is the default charset for Android but not available???");
        }
        args = new String[] { content, webProps.urlString };
      }
      form.dispatchErrorOccurredEvent(OpenAPI.this, method,
          message, (Object[]) args);
    }
  }

  /**
   * Open a connection to the resource and set the HTTP action to PUT or DELETE if it is one of
   * them. GET would be the default, and POST is set in writeRequestData or writeRequestFile
   * @param webProps the properties of the connection, set as properties in the component
   * @param httpVerb One of GET/POST/PUT/DELETE
   * @return a HttpURL Connection
   * @throws IOException
   * @throws ClassCastException
   * @throws ProtocolException thrown if the method in setRequestMethod is not correct
   */
  private static HttpURLConnection openConnection(CapturedProperties webProps, String httpVerb)
      throws IOException, ClassCastException, ProtocolException {

    HttpURLConnection connection = (HttpURLConnection) webProps.url.openConnection();
    connection.setConnectTimeout(webProps.timeout);
    connection.setReadTimeout(webProps.timeout);

    if (httpVerb.equals("PUT") || httpVerb.equals("PATCH") || httpVerb.equals("DELETE")){
      // Set the Request Method; GET is the default, and if it is a POST, it will be marked as such
      // with setDoOutput in writeRequestFile or writeRequestData
      connection.setRequestMethod(httpVerb);
    }

    // Request Headers
    for (Map.Entry<String, List<String>> header : webProps.requestHeaders.entrySet()) {
      String name = header.getKey();
      for (String value : header.getValue()) {
        connection.addRequestProperty(name, value);
      }
    }

    return connection;
  }

  private static void writeRequestData(HttpURLConnection connection, byte[] postData)
      throws IOException {
    // According to the documentation at
    // http://developer.android.com/reference/java/net/HttpURLConnection.html
    // HttpURLConnection uses the GET method by default. It will use POST if setDoOutput(true) has
    // been called.
    connection.setDoOutput(true); // This makes it something other than a HTTP GET.
    // Write the data.
    connection.setFixedLengthStreamingMode(postData.length);
    BufferedOutputStream out = new BufferedOutputStream(connection.getOutputStream());
    try {
      out.write(postData, 0, postData.length);
      out.flush();
    } finally {
      out.close();
    }
  }

  private static String getResponseContent(HttpURLConnection connection) throws IOException {
    // Use the content encoding to convert bytes to characters.
    String encoding = connection.getContentEncoding();
    if (encoding == null) {
      encoding = "UTF-8";
    }
    InputStreamReader reader = new InputStreamReader(getConnectionStream(connection), encoding);
    try {
      int contentLength = connection.getContentLength();
      StringBuilder sb = (contentLength != -1)
          ? new StringBuilder(contentLength)
          : new StringBuilder();
      char[] buf = new char[1024];
      int read;
      while ((read = reader.read(buf)) != -1) {
        sb.append(buf, 0, read);
      }
      return sb.toString();
    } finally {
      reader.close();
    }
  }
  
  private static InputStream getConnectionStream(HttpURLConnection connection) throws SocketTimeoutException {
    // According to the Android reference documentation for HttpURLConnection: If the HTTP response
    // indicates that an error occurred, getInputStream() will throw an IOException. Use
    // getErrorStream() to read the error response.
    try {
      return connection.getInputStream();
    } catch (SocketTimeoutException e) {
      throw e; //Rethrow exception - should not attempt to read stream for timeouts
    } catch (IOException e1) {
      // Use the error response for all other IO Exceptions.
      return connection.getErrorStream();
    }
  }

  /*
   * Converts request headers (a YailList) into the structure that can be used with the Java API
   * (a Map<String, List<String>>). If the request headers contains an invalid element, an
   * InvalidRequestHeadersException will be thrown.
   */
  private static Map<String, List<String>> processRequestHeaders(YailList list)
      throws InvalidRequestHeadersException {
    Map<String, List<String>> requestHeadersMap = Maps.newHashMap();
    for (int i = 0; i < list.size(); i++) {
      Object item = list.getObject(i);
      // Each item must be a two-element sublist.
      if (item instanceof YailList) {
        YailList sublist = (YailList) item;
        if (sublist.size() == 2) {
          // The first element is the request header field name.
          String fieldName = sublist.getObject(0).toString();
          // The second element contains the request header field values.
          Object fieldValues = sublist.getObject(1);

          // Build an entry (key and values) for the requestHeadersMap.
          String key = fieldName;
          List<String> values = Lists.newArrayList();

          // If there is just one field value, it is specified as a single non-list item (for
          // example, it can be a text value). If there are multiple field values, they are
          // specified as a list.
          if (fieldValues instanceof YailList) {
            // It's a list. There are multiple field values.
            YailList multipleFieldsValues = (YailList) fieldValues;
            for (int j = 0; j < multipleFieldsValues.size(); j++) {
              Object value = multipleFieldsValues.getObject(j);
              values.add(value.toString());
            }
          } else {
            // It's a single non-list item. There is just one field value.
            Object singleFieldValue = fieldValues;
            values.add(singleFieldValue.toString());
          }
          // Put the entry into the requestHeadersMap.
          requestHeadersMap.put(key, values);
        } else {
          // The sublist doesn't contain two elements.
          throw new InvalidRequestHeadersException(
              ErrorMessages.ERROR_WEB_REQUEST_HEADER_NOT_TWO_ELEMENTS, i + 1);
        }
      } else {
        // The item isn't a sublist.
        throw new InvalidRequestHeadersException(
            ErrorMessages.ERROR_WEB_REQUEST_HEADER_NOT_LIST, i + 1);
      }
    }
    return requestHeadersMap;
  }

  private static String getResponseType(HttpURLConnection connection) {
    String responseType = connection.getContentType();
    return (responseType != null) ? responseType : "";
  }

  // show a toast using a TextView, which allows us to set the
  // font size.  The default toast is too small.
  private void toastNow (String message) {
    // The notifier font size for more recent releases seems too
    // small compared to early releases.
    // This sets the fontsize according to SDK level,  There is almost certainly
    // a better way to do this, with display metrics for example, but
    // I (Hal) can't figure it out.
    int fontsize = (SdkLevel.getLevel() >= SdkLevel.LEVEL_ICE_CREAM_SANDWICH)
        ? 22 : 15;
    Toast toast = Toast.makeText(activity, message, notifierLength);
    toast.setGravity(Gravity.CENTER, toast.getXOffset() / 2, toast.getYOffset() / 2);
    TextView textView = new TextView(activity);
    textView.setTextSize(fontsize);
    Typeface typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    textView.setTypeface(typeface);
    textView.setPadding(10, 10, 10, 10);
    // Note: The space added to the message below is a patch to work around a bug where,
    // in a message with multiple words, the trailing words do not appear.  Whether this
    // bug happens depends on the version of the OS and the exact characters in the message.
    // The cause of the bug is that the textView parameter are not being set correctly -- I don't know
    // why not.   But as a result, Android will sometimes compute the length of the required
    // textbox as one or two pixels short.  Then, when the message is displayed, the
    // wordwrap mechanism pushes the rest of the words to a "next line" that does not
    // exist.  Adding the space ensures that the width allocated for the text will be adequate.
    textView.setText(message + " ");
    toast.setView(textView);
    toast.show();
  }
}
