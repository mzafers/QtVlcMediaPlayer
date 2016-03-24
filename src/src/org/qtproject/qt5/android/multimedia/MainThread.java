// Eski kod:
////package android.support.annotation;
//package org.videolan.libvlc;

//import java.lang.annotation.Annotation;
//import java.lang.annotation.Retention;
//import java.lang.annotation.RetentionPolicy;
//import java.lang.annotation.Target;

//@Retention(RetentionPolicy.CLASS)
//@Target({java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.CONSTRUCTOR, java.lang.annotation.ElementType.TYPE})
//public @interface MainThread {}


    //------------------------------------------

// Android-23 yeni kod:

/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
//package android.annotation;
package org.videolan.libvlc;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Denotes that the annotated method should only be called on the main thread.
 * If the annotated element is a class, then all methods in the class should be called
 * on the main thread.
 * <p>
 * Example:
 * <pre>{@code
 *  &#64;MainThread
 *  public void deliverResult(D data) { ... }
 * }</pre>
 *
 * {@hide}
 */
@Retention(SOURCE)
@Target({METHOD,CONSTRUCTOR,TYPE})
public @interface MainThread {
}
