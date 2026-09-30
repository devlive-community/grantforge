package org.devlive.grantforge.common.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.util.ObjectUtils;

public class JsonUtils
{
    private static JsonParser jsonParser;

    private JsonUtils()
    {
    }

    /**
     * 格式化json数据
     *
     * @param source json元数据
     * @return 格式化后的json数据
     */
    public static String formatPretty(String source)
    {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        return gson.toJson(new JsonParser().parse(source).getAsJsonObject());
    }

    /**
     * 压缩json数据
     *
     * @param source json元数据
     * @return 压缩后的json数据
     */
    public static String compression(String source)
    {
        Gson gson = new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .create();
        return gson.toJson(formatToJsonObject(source));
    }

    /**
     * 格式化数据为JsonObject
     *
     * @param source json元数据
     * @return 格式化后的JsonObject
     */
    public static JsonObject formatToJsonObject(String source)
    {
        return buildJsonParser().parse(source).getAsJsonObject();
    }

    /**
     * 构建JsonParser
     *
     * @return JsonParser实例
     */
    private static JsonParser buildJsonParser()
    {
        if (ObjectUtils.isEmpty(jsonParser)) {
            jsonParser = new JsonParser();
        }
        return jsonParser;
    }

}
