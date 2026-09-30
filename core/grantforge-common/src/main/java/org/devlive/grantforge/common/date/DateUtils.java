// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.date;

import org.devlive.grantforge.common.support.DateSuooprt;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * <p> DateUtils </p>
 * <p> Description : DateUtils </p>
 * <p> Author : qianmoQ </p>
 * <p> Version : 1.0 </p>
 * <p> Create Time : 2019-01-24 11:35 </p>
 * <p> Author Email: : <a href="mailTo:shichengoooo@163.com">qianmoQ</a> </p>
 */
public class DateUtils {

    /**
     * format date to yyyy-mm-dd hh:mm:ss
     *
     * @return formart date id
     */
    public static String formatYmdhms() {
        SimpleDateFormat format = new SimpleDateFormat();
        format.applyPattern(DateSuooprt.DATE_FORMAT_YYYY_MM_DD_HH_MM_SS);
        return format.format(new Date());
    }

}
