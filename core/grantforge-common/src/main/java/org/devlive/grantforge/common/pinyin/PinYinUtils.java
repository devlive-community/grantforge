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
package org.devlive.grantforge.common.pinyin;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

/**
 * <p> PinYinUtils </p>
 * <p> Description : PinYinUtils </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-01-26 16:25 </p>
 * <p> Author Email: <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
@Slf4j
public class PinYinUtils {

    private final static String EMPTY = "";

    /**
     * Get the Chinese character string pinyin initials, English characters unchanged
     *
     * @param chinese The Chinese character string
     * @return Chinese phonetic alphabet
     */
    public static String getFirstOneSpell(String chinese) {
        String result = getFirstSpell(chinese);
        if (StringUtils.isEmpty(result)) {
            return EMPTY;
        }
        return result.substring(0, 1).toUpperCase();
    }

    public static String getFirstOneSpellToUpper(String chinese) {
        return getFirstOneSpell(chinese).toUpperCase();
    }

    /**
     * Get the Chinese character string pinyin initials, English characters unchanged
     *
     * @param chinese The Chinese character string
     * @return Chinese phonetic alphabet
     */
    public static String getFirstSpell(String chinese) {
        StringBuilder stringBuilder = new StringBuilder();
        char[] chars = chinese.toCharArray();
        HanyuPinyinOutputFormat defaultFormat = new HanyuPinyinOutputFormat();
        defaultFormat.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        defaultFormat.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        for (char c : chars) {
            if (c > 128) {
                try {
                    String[] temp = PinyinHelper.toHanyuPinyinStringArray(c, defaultFormat);
                    if (temp != null) {
                        stringBuilder.append(temp[0].charAt(0));
                    }
                } catch (BadHanyuPinyinOutputFormatCombination e) {
                    log.error("format pinyin error", e.getMessage());
                }
            } else {
                stringBuilder.append(c);
            }
        }
        return stringBuilder.toString().replaceAll("\\W", "").trim();
    }

    /**
     * Get Chinese string pinyin, English characters unchanged
     *
     * @param chinese The Chinese character string
     * @return Chinese phonetic alphabet
     */
    public static String getFullSpell(String chinese) {
        StringBuilder stringBuilder = new StringBuilder();
        char[] chars = chinese.toCharArray();
        HanyuPinyinOutputFormat defaultFormat = new HanyuPinyinOutputFormat();
        defaultFormat.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        defaultFormat.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        for (char c : chars) {
            if (c > 128) {
                try {
                    stringBuilder.append(PinyinHelper.toHanyuPinyinStringArray(c, defaultFormat)[0]);
                } catch (BadHanyuPinyinOutputFormatCombination e) {
                    log.error("format pinyin error", e.getMessage());
                }
            } else {
                stringBuilder.append(c);
            }
        }
        return stringBuilder.toString();
    }

    public static String getFullSpellToUpper(String chinese) {
        return getFullSpell(chinese).toUpperCase();
    }

    /**
     * Gets the first letter of all Chinese character strings
     *
     * @param chinese The Chinese character string
     * @return Chinese phonetic alphabet
     */
    public static String getFullFirstToUpper(String chinese) {
        StringBuilder builder = new StringBuilder();
        Arrays.asList(chinese.split("")).forEach(v -> builder.append(PinYinUtils.getFirstOneSpellToUpper(v)));
        return builder.toString();
    }

}
