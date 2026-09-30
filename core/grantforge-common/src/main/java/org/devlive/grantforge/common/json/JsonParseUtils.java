package org.devlive.grantforge.common.json;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.*;

@Slf4j
public class JsonParseUtils {

    private static final Class<?> JSON_OBJECT = JSONObject.class;
    private static final Class<?> JSON_ARRAY = JSONArray.class;

    /**
     * 根据URI地址解析JSON默认编码UTF-8
     *
     * @param uri JSON地址
     * @return 解析的json数据
     */
    public static List<Map<String, String>> parseJson(URI uri) {
        return parseJson(uri, "UTF-8");
    }

    /**
     * 根据URI地址解析JSON
     *
     * @param uri      JSON地址
     * @param encoding 编码
     * @return 解析的json数据
     */
    public static List<Map<String, String>> parseJson(URI uri, String encoding) {
        List<Map<String, String>> flatJson = null;
        String json;
        try {
            json = IOUtils.toString(uri, encoding);
            flatJson = parseJson(json);
        } catch (IOException e) {
            log.error("JsonParseUtils#ParseJson(uri, encoding) IOException: ", e);
        } catch (Exception ex) {
            log.error("JsonParseUtils#ParseJson(uri, encoding) Exception: ", ex);
        }
        return flatJson;
    }

    /**
     * 根据文件解析JSON默认编码UTF-8
     *
     * @param file json文件
     * @return 解析的json数据
     */
    public static List<Map<String, String>> parseJson(File file) {
        return parseJson(file, "UTF-8");
    }

    /**
     * 根据文件解析JSON
     *
     * @param file     json文件
     * @param encoding 编码
     * @return 解析的json数据
     */
    public static List<Map<String, String>> parseJson(File file, String encoding) {
        List<Map<String, String>> flatJson = null;
        String json;
        try {
            json = FileUtils.readFileToString(file, encoding);
            flatJson = parseJson(json);
        } catch (IOException e) {
            log.error("JsonParseUtils#ParseJson(file, encoding) IOException: ", e);
        } catch (Exception ex) {
            log.error("JsonParseUtils#ParseJson(file, encoding) Exception: ", ex);
        }
        return flatJson;
    }

    /**
     * 解析JSON
     *
     * @param json json数据
     * @return 解析的json数据
     */
    public static List<Map<String, String>> parseJson(String json) {
        List<Map<String, String>> flatJson;
        try {
            JSONObject jsonObject = new JSONObject(json);
            flatJson = new ArrayList<>();
            flatJson.add(parse(jsonObject));
        } catch (JSONException je) {
            log.info("Handle the JSON String as JSON Array");
            flatJson = handleAsArray(json);
        }
        return flatJson;
    }

    /**
     * 解析JSONObject数据
     *
     * @param jsonObject json实体
     * @return 解析的json数据
     */
    public static Map<String, String> parse(JSONObject jsonObject) {
        Map<String, String> flatJson = new LinkedHashMap<>();
        flatten(jsonObject, flatJson, "");
        return flatJson;
    }

    /**
     * 解析JSON Array
     *
     * @param jsonArray json数组
     * @return 解析的json数据
     */
    public static List<Map<String, String>> parse(JSONArray jsonArray) {
        JSONObject jsonObject;
        List<Map<String, String>> flatJson = new ArrayList<>();
        int length = jsonArray.length();
        for (int i = 0; i < length; i++) {
            jsonObject = jsonArray.getJSONObject(i);
            Map<String, String> stringMap = parse(jsonObject);
            flatJson.add(stringMap);
        }
        return flatJson;
    }

    /**
     * 将JSON字符串作为数组处理
     *
     * @param json json数据
     * @return 处理后的数据
     */
    private static List<Map<String, String>> handleAsArray(String json) {
        List<Map<String, String>> flatJson = null;
        try {
            JSONArray jsonArray = new JSONArray(json);
            flatJson = parse(jsonArray);
        } catch (Exception e) {
            log.error("JSON might be malformed, Please verify that your JSON is valid");
        }
        return flatJson;
    }

    /**
     * 将给定的JSON对象扁平化
     * 将JSON对象转换为的映射,字符串键和值
     *
     * @param obj      json对象
     * @param flatJson 格式化后的数据
     * @param prefix   前缀
     */
    private static void flatten(JSONObject obj, Map<String, String> flatJson, String prefix) {
        Iterator<?> iterator = obj.keys();
        String _prefix = prefix != "" ? prefix + "." : "";
        while (iterator.hasNext()) {
            String key = iterator.next().toString();
            if (obj.get(key).getClass() == JSON_OBJECT) {
                JSONObject jsonObject = (JSONObject) obj.get(key);
                flatten(jsonObject, flatJson, _prefix + key);
            } else if (obj.get(key).getClass() == JSON_ARRAY) {
                JSONArray jsonArray = (JSONArray) obj.get(key);
                if (jsonArray.length() < 1) {
                    continue;
                }
                flatten(jsonArray, flatJson, _prefix + key);
            } else {
                String value = obj.get(key).toString();
                if (value != null && !value.equals("null")) {
                    flatJson.put(_prefix + key, value);
                }
            }
        }
    }

    /**
     * 将给定的JSON数组扁平化
     *
     * @param obj      json数组
     * @param flatJson 格式化后的数据
     * @param prefix   前缀
     */
    private static void flatten(JSONArray obj, Map<String, String> flatJson, String prefix) {
        int length = obj.length();
        for (int i = 0; i < length; i++) {
            if (obj.get(i).getClass() == JSON_ARRAY) {
                JSONArray jsonArray = (JSONArray) obj.get(i);
                if (jsonArray.length() < 1) {
                    continue;
                }
                flatten(jsonArray, flatJson, prefix + "[" + i + "]");
            } else if (obj.get(i).getClass() == JSON_OBJECT) {
                JSONObject jsonObject = (JSONObject) obj.get(i);
                flatten(jsonObject, flatJson, prefix + "[" + (i + 1) + "]");
            } else {
                String value = obj.get(i).toString();
                if (value != null) {
                    flatJson.put(prefix + "[" + (i + 1) + "]", value);
                }
            }
        }
    }

}
