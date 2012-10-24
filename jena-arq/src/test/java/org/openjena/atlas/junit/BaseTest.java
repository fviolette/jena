/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openjena.atlas.junit;

import java.util.ArrayDeque ;
import java.util.Deque ;

import org.junit.Assert ;
import org.openjena.atlas.logging.Log ;
import org.openjena.riot.ErrorHandler ;
import org.openjena.riot.ErrorHandlerFactory ;

public class BaseTest extends Assert
{
    private static Deque<ErrorHandler> errorHandlers = new ArrayDeque<ErrorHandler>() ;
    
    static public void setTestLogging(ErrorHandler errorhandler)
    {
        errorHandlers.push(ErrorHandlerFactory.getDefaultErrorHandler()) ;
        ErrorHandlerFactory.setDefaultErrorHandler(errorhandler) ;
    }
    
    static public void setTestLogging()
    {
//        if ( errorHandlers.size() != 0 )
//            Log.warn(BaseTest.class, "ErrorHandler already set for testing") ;
        setTestLogging(ErrorHandlerFactory.errorHandlerNoLogging) ;
    }

    static public void unsetTestLogging()
    {
        if ( errorHandlers.size() == 0 )
        {
            Log.warn(BaseTest.class, "ErrorHandler not set for testing") ;
            ErrorHandlerFactory.setDefaultErrorHandler(ErrorHandlerFactory.errorHandlerStd) ;  // Panic measures
            return ;
        }
        ErrorHandler errHandler = errorHandlers.pop();
        ErrorHandlerFactory.setDefaultErrorHandler(errHandler) ;
    }

    
    public static void assertNotEquals(Object a, Object b)
    {
         assertFalse(a.equals(b)) ;
    }

    public static void assertNotEquals(String msg, Object a, Object b)
    {
         assertFalse(msg, a.equals(b)) ;
    }
    
    public static void assertNotEquals(long a, long b)
    {
         assertFalse(a == b ) ;
    }
    
}