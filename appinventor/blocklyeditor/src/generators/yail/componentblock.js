// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2012 Massachusetts Institute of Technology. All rights reserved.

/**
 * @license
 * @fileoverview Component blocks yail generators for Blockly, modified for MIT App Inventor.
 * @author mckinney@mit.edu (Andrew F. McKinney)
 */

'use strict';

goog.provide('Blockly.Yail.componentblock');

/**
 * Lyn's History:
 * [lyn, 10/27/13] Modified event parameter names to begin with YAIL_LOCAL_VAR_TAG (currently '$').
 *     All setters/getters assume such a tag. At least on Kawa-legal first character is necessary to
 *     ensure AI identifiers satisfy Kawa's identifier rules.
 */

/**
 * Returns a function that takes no arguments, generates Yail for an event handler declaration block
 * and returns the generated code string.
 *
 * @param {String} instanceName the block's instance name, e.g., Button1
 * @param {String} eventName  the type of event, e.g., Click
 * @returns {Function} event code generation function with instanceName and eventName bound in
 */
Blockly.Yail.component_event = function() { // want to make a new block type for openapi?
                                            // "this" is the block - this.typename will tell you if its an openapi
                                            // method helper??
                                            // component type method or component method??
                                            // want to log in as administrator, can right click on block and click generate yail

  var preamble;
  var eventName = this.eventName;

  var componentDb = this.workspace.getComponentDatabase();
  var isAPI = componentDb['types_'][this.typeName]['componentInfo']['isAPI'];
  if (isAPI) {
    eventName = "GotResponse";
  }

  if (this.isGeneric) {
    preamble = Blockly.Yail.YAIL_DEFINE_GENERIC_EVENT
      + this.typeName
      + Blockly.Yail.YAIL_SPACER
      + eventName;
  } else {
    preamble = Blockly.Yail.YAIL_DEFINE_EVENT
      + this.getFieldValue("COMPONENT_SELECTOR")
      + Blockly.Yail.YAIL_SPACER
      + eventName;
  }

  var body = Blockly.Yail.statementToCode(this, 'DO');
  // TODO: handle deactivated block, null body
  if(body == ""){
    body = Blockly.Yail.YAIL_NULL;
  }


  var code = preamble
    + Blockly.Yail.YAIL_OPEN_COMBINATION
    // TODO: formal params go here
    // declaredNames gives us names in local language, but we want the default
    // + this.declaredNames()
    //       .map(function (name) {return Blockly.Yail.YAIL_LOCAL_VAR_TAG+name;})
    //       .join(' ')
    // So we do this instead:
    + this.getParameters()
          .map(function (param) {return Blockly.Yail.YAIL_LOCAL_VAR_TAG+param.name;})
          .join(' ')
    + Blockly.Yail.YAIL_CLOSE_COMBINATION
    + Blockly.Yail.YAIL_SET_THIS_FORM
    + Blockly.Yail.YAIL_SPACER
    + body
    + Blockly.Yail.YAIL_CLOSE_COMBINATION;
  return code;
}

Blockly.Yail.component_method = function() {
  var methodHelperYailString = Blockly.Yail.methodHelper(this, (this.isGeneric ? this.typeName : this.instanceName), this.methodName, this.isGeneric);
  //if the method returns a value
  if(this.getMethodTypeObject() && this.getMethodTypeObject().returnType) {
    return [methodHelperYailString, Blockly.Yail.ORDER_ATOMIC];
  } else {
    return methodHelperYailString;
  }
}

/**
 * Returns a function that generates Yail to call to a method with a return value. The generated
 * function takes no arguments and returns a 2-element Array with the method call code string
 * and the operation order Blockly.Yail.ORDER_ATOMIC.
 *
 * @param {String} instanceName
 * @param {String} methodName
 * @returns {Function} method call generation function with instanceName and methodName bound in
 */
Blockly.Yail.methodWithReturn = function(instanceName, methodName) {
  return function() {
    return [Blockly.Yail.methodHelper(this, instanceName, methodName, false),
            Blockly.Yail.ORDER_ATOMIC];
  }
}

/**
 * Returns a function that generates Yail to call to a method with no return value. The generated
 * function takes no arguments and returns the method call code string.
 *
 * @param {String} instanceName
 * @param {String} methodName
 * @returns {Function} method call generation function with instanceName and methodName bound in
 */
Blockly.Yail.methodNoReturn = function(instanceName, methodName) {
  return function() {
    return Blockly.Yail.methodHelper(this, instanceName, methodName, false);
  }
}

/**
 * Returns a function that generates Yail to call to a generic method with a return value.
 * The generated function takes no arguments and returns a 2-element Array with the method call
 * code string and the operation order Blockly.Yail.ORDER_ATOMIC.
 *
 * @param {String} instanceName
 * @param {String} methodName
 * @returns {Function} method call generation function with instanceName and methodName bound in
 */
Blockly.Yail.genericMethodWithReturn = function(typeName, methodName) {
  return function() {
    return [Blockly.Yail.methodHelper(this, typeName, methodName, true), Blockly.Yail.ORDER_ATOMIC];
  }
}

/**
 * Returns a function that generates Yail to call to a generic method with no return value.
 * The generated function takes no arguments and returns the method call code string.
 *
 * @param {String} instanceName
 * @param {String} methodName
 * @returns {Function} method call generation function with instanceName and methodName bound in
 */
Blockly.Yail.genericMethodNoReturn = function(typeName, methodName) {
  return function() {
    return Blockly.Yail.methodHelper(this, typeName, methodName, true);
  }
}

/**
 * Generate and return the code for a method call. The generated code is the same regardless of
 * whether the method returns a value or not.
 * @param {!Blockly.Blocks.component_method} methodBlock  block for which we're generating code
 * @param {String} name instance or type name
 * @param {String} methodName
 * @param {String} generic true if this is for a generic method block, false if for an instance
 * @returns {Function} method call generation function with instanceName and methodName bound in
 */
Blockly.Yail.methodHelper = function(methodBlock, name, methodName, generic) {
  var componentDb = methodBlock.workspace.getComponentDatabase(); ////

// TODO: the following line  may be a bit of a hack because it hard-codes "component" as the
// first argument type when we're generating yail for a generic block, instead of using
// type information associated with the socket. The component parameter is treated differently
// here than the other method parameters. This may be fine, but consider whether
// to get the type for the first socket in a more general way in this case.
  var methodObject = methodBlock.getMethodTypeObject();
  var continuation = methodObject['continuation'];
  var paramObjects = methodObject.parameters;
  var numOfParams = paramObjects.length;
  var yailTypes = [];
  if(generic) {
    yailTypes.push(Blockly.Yail.YAIL_COMPONENT_TYPE);
  }
  for(var i=0;i<paramObjects.length;i++) {
    yailTypes.push(paramObjects[i].type);
  }
  //var yailTypes = (generic ? [Blockly.Yail.YAIL_COMPONENT_TYPE] : []).concat(methodBlock.yailTypes);
  var callPrefix;
  if (generic) {
    console.log("componentblock 1");
    name = componentDb.getType(name).type;
    callPrefix = continuation ? Blockly.Yail.YAIL_CALL_COMPONENT_TYPE_METHOD_BLOCKING : Blockly.Yail.YAIL_CALL_COMPONENT_TYPE_METHOD
        // TODO(hal, andrew): check for empty socket and generate error if necessary
        + Blockly.Yail.valueToCode(methodBlock, 'COMPONENT', Blockly.Yail.ORDER_NONE)
        + Blockly.Yail.YAIL_SPACER;
  } else {
    callPrefix = continuation ? Blockly.Yail.YAIL_CALL_COMPONENT_METHOD_BLOCKING : Blockly.Yail.YAIL_CALL_COMPONENT_METHOD;
    name = methodBlock.getFieldValue("COMPONENT_SELECTOR");
    // special case for handling Clock.Add
    var timeUnit = methodBlock.getFieldValue("TIME_UNIT");
    if (timeUnit) {
      if (Blockly.ComponentBlock.isClockMethodName(methodName)) {
        methodName = "Add"+timeUnit; // For example, AddDays
      }
    }
  }

  var args = [];
  var paramList;
  var isAPI = componentDb['types_'][methodBlock.typeName]['componentInfo']['isAPI'];
  if (isAPI) {
    var apiCode = componentDb['types_'][methodBlock.typeName]['componentInfo']['APICode'];
    var blockCode = Blockly.Yail.getAPICode(methodName, apiCode);
    blockCode = " \"" + blockCode + "\"";
    args.push(blockCode);
    methodName = "invokeAPI";
    paramList = Blockly.Yail.YAIL_SPACER;
    paramList += Blockly.Yail.YAIL_CALL_YAIL_PRIMITIVE + "make-yail-list" + Blockly.Yail.YAIL_SPACER;
    paramList += Blockly.Yail.YAIL_OPEN_COMBINATION + Blockly.Yail.YAIL_LIST_CONSTRUCTOR;
    var itemsAdded = 0;
    for (var x = 0; x < numOfParams; x++) {
      var argument = Blockly.Yail.valueToCode(methodBlock, 'ARG' + x, Blockly.Yail.ORDER_NONE).replaceAll("\\\"", "\"") || null;
      if(argument != null){
        paramList += Blockly.Yail.YAIL_SPACER + argument;
        itemsAdded++;
      }
    }
    paramList += Blockly.Yail.YAIL_SPACER + Blockly.Yail.YAIL_CLOSE_COMBINATION + Blockly.Yail.YAIL_SPACER;
    paramList += Blockly.Yail.YAIL_QUOTE + Blockly.Yail.YAIL_OPEN_COMBINATION;
    for(var i=0;i<itemsAdded;i++) {
      paramList += "any" + Blockly.Yail.YAIL_SPACER;
    }
    paramList += Blockly.Yail.YAIL_CLOSE_COMBINATION;
    paramList += Blockly.Yail.YAIL_SPACER + Blockly.Yail.YAIL_DOUBLE_QUOTE + "make a list" + Blockly.Yail.YAIL_DOUBLE_QUOTE + Blockly.Yail.YAIL_CLOSE_COMBINATION;
    args.push(paramList);
    yailTypes = ["text", "list"];
  }
  else {
      for (var x = 0; x < numOfParams; x++) {
      // TODO(hal, andrew): check for empty socket and generate error if necessary
      args.push(Blockly.Yail.YAIL_SPACER
                + Blockly.Yail.valueToCode(methodBlock, 'ARG' + x, Blockly.Yail.ORDER_NONE));
    }
  }

  return callPrefix
    + Blockly.Yail.YAIL_QUOTE
    + name
    + Blockly.Yail.YAIL_SPACER
    + Blockly.Yail.YAIL_QUOTE
    + methodName
    + Blockly.Yail.YAIL_SPACER
    + Blockly.Yail.YAIL_OPEN_COMBINATION
    + Blockly.Yail.YAIL_LIST_CONSTRUCTOR
    + args.join(' ')
    + Blockly.Yail.YAIL_CLOSE_COMBINATION
    + Blockly.Yail.YAIL_SPACER
    + Blockly.Yail.YAIL_QUOTE
    + Blockly.Yail.YAIL_OPEN_COMBINATION
    + yailTypes.join(' ')
    + Blockly.Yail.YAIL_CLOSE_COMBINATION
    + Blockly.Yail.YAIL_CLOSE_COMBINATION;
};

Blockly.Yail.component_set_get = function() {
  if(this.setOrGet == "set") {
    if(this.isGeneric) {
      return Blockly.Yail.genericSetproperty.call(this);
    } else {
      return Blockly.Yail.setproperty.call(this);
    }
  } else {
    if(this.isGeneric) {
      return Blockly.Yail.genericGetproperty.call(this);
    } else {
      return Blockly.Yail.getproperty.call(this);
    }
  }
};

/**
 * Returns a function that takes no arguments, generates Yail code for setting a component property
 * and returns the code string.
 *
 * @param {String} instanceName
 * @returns {Function} property setter code generation function with instanceName bound in
 */
Blockly.Yail.setproperty = function() {
  var propertyName = this.getFieldValue("PROP");
  var propType = this.getPropertyObject(propertyName).type;
  var assignLabel = Blockly.Yail.YAIL_QUOTE + this.getFieldValue("COMPONENT_SELECTOR") + Blockly.Yail.YAIL_SPACER
    + Blockly.Yail.YAIL_QUOTE + propertyName;
  var code = Blockly.Yail.YAIL_SET_AND_COERCE_PROPERTY + assignLabel + Blockly.Yail.YAIL_SPACER;
  // TODO(hal, andrew): check for empty socket and generate error if necessary
  code = code.concat(Blockly.Yail.valueToCode(this, 'VALUE', Blockly.Yail.ORDER_NONE /*TODO:?*/));
  code = code.concat(Blockly.Yail.YAIL_SPACER + Blockly.Yail.YAIL_QUOTE
    + propType + Blockly.Yail.YAIL_CLOSE_COMBINATION);
  return code;
};


/**
 * Returns a function that takes no arguments, generates Yail code for setting a generic component's
 * property and returns the code string.
 *
 * @param {String} instanceName
 * @returns {string} property setter code generation function with instanceName bound in
 */
Blockly.Yail.genericSetproperty = function() {
  var propertyName = this.getFieldValue("PROP");
  var propType = this.getPropertyObject(propertyName).type;
  var assignLabel = Blockly.Yail.YAIL_QUOTE
  console.log("componentblock 2");
    + this.workspace.getComponentDatabase().getType(this.typeName).type + Blockly.Yail.YAIL_SPACER
    + Blockly.Yail.YAIL_QUOTE + propertyName;
  var code = Blockly.Yail.YAIL_SET_AND_COERCE_COMPONENT_TYPE_PROPERTY
    // TODO(hal, andrew): check for empty socket and generate error if necessary
    + Blockly.Yail.valueToCode(this, 'COMPONENT', Blockly.Yail.ORDER_NONE)
    + Blockly.Yail.YAIL_SPACER
    + assignLabel
    + Blockly.Yail.YAIL_SPACER;
  // TODO(hal, andrew): check for empty socket and generate error if necessary
  code = code.concat(Blockly.Yail.valueToCode(this, 'VALUE', Blockly.Yail.ORDER_NONE /*TODO:?*/));
  code = code.concat(Blockly.Yail.YAIL_SPACER + Blockly.Yail.YAIL_QUOTE
    + propType + Blockly.Yail.YAIL_CLOSE_COMBINATION);
  return code;
};


/**
 * Returns a function that takes no arguments, generates Yail code for getting a component's
 * property value and returns a 2-element array containing the property getter code string and the
 * operation order Blockly.Yail.ORDER_ATOMIC.
 *
 * @param {String} instanceName
 * @returns {Function} property getter code generation function with instanceName bound in
 */
Blockly.Yail.getproperty = function(instanceName) {
  var propertyName = this.getFieldValue("PROP");
  var propType = this.getPropertyObject(propertyName).type;
  var code = Blockly.Yail.YAIL_GET_PROPERTY
    + Blockly.Yail.YAIL_QUOTE
    + this.getFieldValue("COMPONENT_SELECTOR")
    + Blockly.Yail.YAIL_SPACER
    + Blockly.Yail.YAIL_QUOTE
    + propertyName
    + Blockly.Yail.YAIL_CLOSE_COMBINATION;
  return [code, Blockly.Yail.ORDER_ATOMIC];
};


/**
 * Returns a function that takes no arguments, generates Yail code for getting a generic component's
 * property value and returns a 2-element array containing the property getter code string and the
 * operation order Blockly.Yail.ORDER_ATOMIC.
 *
 * @param {String} instanceName
 * @returns {Function} property getter code generation function with instanceName bound in
 */
Blockly.Yail.genericGetproperty = function(typeName) {
  var propertyName = this.getFieldValue("PROP");
  var propType = this.getPropertyObject(propertyName).type;
  console.log("componentblock 3");
  var code = Blockly.Yail.YAIL_GET_COMPONENT_TYPE_PROPERTY
    // TODO(hal, andrew): check for empty socket and generate error if necessary
    + Blockly.Yail.valueToCode(this, 'COMPONENT', Blockly.Yail.ORDER_NONE)
    + Blockly.Yail.YAIL_SPACER
    + Blockly.Yail.YAIL_QUOTE
    + this.workspace.getComponentDatabase().getType(this.typeName).type
    + Blockly.Yail.YAIL_SPACER
    + Blockly.Yail.YAIL_QUOTE
    + propertyName
    + Blockly.Yail.YAIL_CLOSE_COMBINATION;
  return [code, Blockly.Yail.ORDER_ATOMIC];
};



/**
 * Returns a function that takes no arguments, generates Yail to get the value of a component
 * object, and returns a 2-element array containing the component getter code and the operation
 * order Blockly.Yail.ORDER_ATOMIC.
 *
 * @param {String} instanceName
 * @returns {Function} component getter code generation function with instanceName bound in
 */
Blockly.Yail.component_component_block = function() {
  return [Blockly.Yail.YAIL_GET_COMPONENT + this.getFieldValue("COMPONENT_SELECTOR") + Blockly.Yail.YAIL_CLOSE_COMBINATION,
          Blockly.Yail.ORDER_ATOMIC];
};

Blockly.Yail.getAPICode = function(methodName, apiCode) {
  var apiAsJSON = JSON.parse(apiCode);
  var allFuncs = apiAsJSON.functions;
  var myFunc;
  for (var i = 0; i < allFuncs.length; i++) {
    var currFunc = allFuncs[i];
    if (currFunc.name == methodName) {
      myFunc = currFunc;
      break;
    }
  }
  var funcAsJSON = {
    "serverUrl": apiAsJSON.serverUrl,
    "funcInfo": myFunc
  }
  var funcAsStr = JSON.stringify(funcAsJSON);
  return funcAsStr.replaceAll("\"", "\\\"");
}
