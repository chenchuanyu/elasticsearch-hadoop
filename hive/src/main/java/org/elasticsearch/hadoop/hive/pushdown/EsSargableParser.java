/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.hadoop.hive.pushdown;

import org.apache.hadoop.hive.ql.udf.UDFRegExp;

import java.util.*;

/**
 * Es Sargable Parser class
 */
public class EsSargableParser implements SargableParser {

    /**
     * All sargable operators
     */
    private static Set<String> sargableOp;

    /**
     * Semantic conversion from hive udf to es
     */
    private static Map<String, String> sargableOpUDFClassMapping;

    /**
     * Range operator,such as > , < ,=
     */
    private static Set<String> rangeOp;

    /**
     * Take reverse operator mapping,such as != ---> =
     */
    private static Map<String, String> reverseOps;

    /**
     * Semantic conversion from hive to es
     */
    private static Map<String, String> synonymOps;

    /**
     * Login operator,such as and, or , not
     */
    private static Set<String> logicOp;

    static {
        init();
    }

    private static void init() {
        sargableOp = new HashSet<String>(Arrays.asList(
                "=", "<", ">", "<=", ">=", "between", "is null", "is not null"
        ));

        rangeOp = new HashSet<String>(Arrays.asList(
                "<", ">", "<=", ">=", "between"
        ));

        logicOp = new HashSet<String>(Arrays.asList(
                "and", "or", "not"
        ));

        reverseOps = new HashMap<String, String>();
        reverseOps.put(">", "<");
        reverseOps.put(">=", "<=");
        reverseOps.put("<", ">");
        reverseOps.put("<=", ">=");
        reverseOps.put("=", "!=");
        reverseOps.put("!=", "=");

        synonymOps = new HashMap<String, String>();
        synonymOps.put("==", "=");
        synonymOps.put("<>", "!=");
        synonymOps.put("!", "not");
        synonymOps.put("&&", "and");
        synonymOps.put("||", "or");
        synonymOps.put("rlike", "regex");

        sargableOpUDFClassMapping = new HashMap<String, String>();
        sargableOpUDFClassMapping.put("GenericUDFOPAnd", "and");
        sargableOpUDFClassMapping.put("GenericUDFOPOr", "or");
        sargableOpUDFClassMapping.put("GenericUDFOPNot", "not");
        sargableOpUDFClassMapping.put("GenericUDFOPEqual", "=");
        sargableOpUDFClassMapping.put("GenericUDFOPEqualOrGreaterThan", ">=");
        sargableOpUDFClassMapping.put("GenericUDFOPEqualOrLessThan", "<=");
        sargableOpUDFClassMapping.put("GenericUDFOPGreaterThan", ">");
        sargableOpUDFClassMapping.put("GenericUDFOPLessThan", "<");
        sargableOpUDFClassMapping.put("GenericUDFOPNotEqual", "!=");
        sargableOpUDFClassMapping.put("GenericUDFBetween", "between");
        sargableOpUDFClassMapping.put("GenericUDFOPNull", "is null");
        sargableOpUDFClassMapping.put("GenericUDFOPNotNull", "is not null");
        sargableOpUDFClassMapping.put("GenericUDFIn", "in");
        sargableOpUDFClassMapping.put(UDFRegExp.class.getSimpleName(), "regex");
    }

    public boolean isRangeOp(String op) {
        return rangeOp.contains(op);
    }

    public boolean isSargableOp(String op) {
        return op != null && (sargableOp.contains(op) || sargableOp.contains(sargableOpUDFClassMapping.get(op))) && !isLogicOp(op);
    }

    public boolean isLogicOp(String op) {
        return op != null && (logicOp.contains(op) || logicOp.contains(sargableOpUDFClassMapping.get(op)));
    }

    public String reverseOp(String op) {
        if (op == null) {
            return null;
        }
        String rop = reverseOps.get(op);
        return rop;
    }

    public String udfOp(String op) {
        if (op == null) {
            return null;
        }
        String uOp = sargableOpUDFClassMapping.get(op);
        return uOp;
    }

    public String synonymOp(String op) {
        String sop = synonymOps.get(op);
        if (sop == null) {
            return op.toLowerCase();
        } else {
            return sop;
        }
    }

}
