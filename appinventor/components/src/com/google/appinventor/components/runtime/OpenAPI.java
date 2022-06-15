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
   * Creates a new OpenAPI component.
   *
   * @param container container, component will be placed in
   */
  public OpenAPI(ComponentContainer container) {
    super(container.$form());
  }

  @SimpleFunction
  public void invokeAPI() {
      
  }
}
