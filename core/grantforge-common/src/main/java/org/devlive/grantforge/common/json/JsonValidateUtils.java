package org.devlive.grantforge.common.json;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

public class JsonValidateUtils {

    /**
     * 校验是否是json数据
     *
     * @param json json数据
     * @return 校验结果
     */
    public static boolean isJSON(String json) {
        try {
            new Gson().fromJson(json, JsonObject.class);
            return true;
        } catch (JsonSyntaxException e) {
            return false;
        }
    }

}
