package org.elasticsearch.hadoop.hive.pushdown.parse.query;

import java.util.List;

/**
 * the manager functions for JsonObj
 */
public class JsonObjManager {

    /**
     * using or operator to splice elements
     *
     * @param isES50
     * @param list
     * @return
     */
    public static JsonObj or(boolean isES50, List<JsonObj> list) {
        if (list == null || list.isEmpty())
            return null;

        if (isES50) {
            //sing bool-should after 5.X.
            BoolJson boolJson = new BoolJson();
            for (JsonObj obj : list) {
                if (!obj.isEmpty()) {
                    BoolJson filterWrapper = new BoolJson().filter(obj);
                    boolJson.should(filterWrapper);
                }
            }
            return boolJson;
        } else {
            //using filter-or before 5.X.
            OrJson orJson = new OrJson();
            for (JsonObj obj : list) {
                if (!obj.isEmpty()) {
                    orJson.filters(obj);
                }
            }
            return orJson;
        }
    }

    /**
     * using and operator to splice elements
     *
     * @param isES50
     * @param list
     * @return
     */
    public static JsonObj and(boolean isES50, List<JsonObj> list) {
        if (list == null || list.isEmpty())
            return null;

        if (isES50) {
            //using bool-filter after 5.X.
            BoolJson boolJson = new BoolJson();
            for (JsonObj obj : list) {
                if (!obj.isEmpty())
                    if (obj instanceof BoolJson && obj.containsKey("filter")) {
                        for (JsonObj obj2 : ((BoolJson) obj).getList("filter"))
                            boolJson.filter(obj2);
                    } else {

                        boolJson.filter(obj);
                    }
            }
            return boolJson;
        } else {
            //using filter-and after 5.X.
            AndJson andJson = new AndJson();
            for (JsonObj obj : list) {
                if (!obj.isEmpty())
                    andJson.filters(obj);
            }
            return andJson;
        }
    }

    /**
     * using not operator to splice elements.
     * actually usually there's only one argument with not function.
     *
     * @param isES50
     * @param jsonObj
     * @return
     */
    public static JsonObj not(boolean isES50, JsonObj jsonObj) {
        if (jsonObj == null || jsonObj.isEmpty())
            return null;

        if (isES50) {
            //using bool-must_not after 5.X.
            BoolJson boolJson = new BoolJson();
            BoolJson filterWrapper = new BoolJson().filter(jsonObj);
            boolJson.mustNot(filterWrapper);
            return boolJson;
        } else {
            //using filter-not after 5.X.
            return new NotJson(jsonObj);
        }
    }

    public static List<JsonObj> add(List<JsonObj> list, JsonObj... jsonObjs) {
        for (JsonObj jsonObj : jsonObjs) {
            if (jsonObj.isEmpty())
                continue;
            list.add(jsonObj);
        }
        return list;
    }
}