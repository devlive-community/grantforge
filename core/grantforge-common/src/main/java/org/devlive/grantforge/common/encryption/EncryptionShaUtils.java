/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.devlive.grantforge.common.encryption;

import org.devlive.grantforge.common.support.EncryptionSupport;
import lombok.extern.slf4j.Slf4j;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * <p> Encryption </p>
 * <p> Description : Encryption </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-01-24 11:48 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@Slf4j
public class EncryptionShaUtils {

    /**
     * encryption str to hash256
     *
     * @param data encryption str
     * @return encryption hash256 data
     */
    public static String hash256(String data) {
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance(EncryptionSupport.ENCRYPTION_SHA_256);
        } catch (NoSuchAlgorithmException e) {
            log.error("encryption error", e.getMessage());
        }
        assert digest != null;
        digest.update(data.getBytes());
        return bytesToHex(digest.digest());
    }

    /**
     * convert bytes to hex
     *
     * @param bytes current data bytes
     * @return hex data
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte v : bytes) {
            result.append(Integer.toString((v & 0xff) + 0x100, 16).substring(1));
        }
        return result.toString();
    }

}
