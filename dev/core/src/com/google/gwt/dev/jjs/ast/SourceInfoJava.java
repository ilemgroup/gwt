/*
 * Copyright 2008 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.gwt.dev.jjs.ast;

import com.google.gwt.dev.jjs.SourceInfo;

/**
 * An implementation of SourceInfo representing SourceInfo nodes derived from
 * the Java AST. Instances of this class should only be constructed by JProgram.
 */
class SourceInfoJava extends SourceInfo {
  /**
   * Indicates that an AST element is an intrinsic element of the AST and has no
   * meaningful source location. This is typically used by singleton AST
   * elements or for literal values.
   */
  public static final SourceInfo INTRINSIC = new SourceInfoJava(0, 0, 0,
      "Java intrinsics", true);

  /**
   * Called only from JProgram.
   */
  SourceInfoJava(int startPos, int endPos, int startLine, String fileName,
      boolean createDescendants) {
    super(startPos, endPos, startLine, fileName, createDescendants);
  }
}
