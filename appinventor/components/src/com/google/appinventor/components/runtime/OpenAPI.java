// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.components.runtime;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.YaVersion;

import android.app.Activity;
import android.view.Gravity;
import android.os.Handler;
import android.graphics.Typeface;
import android.widget.TextView;
import android.widget.Toast;

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

  @SimpleFunction
  public void invokeAPI() {
    handler.post(new Runnable() {
        public void run() {
        toastNow("yay it did something");
        }
    });
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
