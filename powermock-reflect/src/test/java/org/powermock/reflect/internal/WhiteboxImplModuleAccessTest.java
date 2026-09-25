/*
 * Copyright 2008 the original author or authors.
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
package org.powermock.reflect.internal;

import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

/**
 * WhiteboxImpl must not force access to members of JDK packages that are not opened.
 */
public class WhiteboxImplModuleAccessTest {

    @Test
    public void getAllMethodsOfClosedJdkClassDoesNotThrow() {
        assumeTrue("module system exists only on Java 9+",
                !System.getProperty("java.specification.version").startsWith("1."));

        Method[] methods = WhiteboxImpl.getAllMethods(ConcurrentHashMap.class);

        assertThat(methods).isNotEmpty();
    }

    @Test
    public void privateMethodOfUserClassIsStillAccessibleAndInvocable() throws Exception {
        Method secret = null;
        for (Method method : WhiteboxImpl.getAllMethods(UserClass.class)) {
            if ("secret".equals(method.getName())) {
                secret = method;
            }
        }

        assertThat(secret).isNotNull();
        assertThat(secret.isAccessible()).isTrue();
        assertThat((String) Whitebox.invokeMethod(new UserClass(), "secret")).isEqualTo("hidden");
    }

    private static class UserClass {
        private String secret() {
            return "hidden";
        }
    }
}
