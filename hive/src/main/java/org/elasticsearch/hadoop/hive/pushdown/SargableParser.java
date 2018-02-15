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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.apache.hadoop.hive.ql.udf.UDFRegExp;
import org.elasticsearch.hadoop.cfg.Settings;

import java.util.*;

/**
 * Sargable Parser class
 */
public class SargableParser {

    /**
     * All sargable operators
     */
    public Set<String> sargableOp = new HashSet<String>(Arrays.asList(
            "=", "<", ">", "<=", ">=", "between", "is null", "is not null"
    ));

    /**
     * Semantic conversion from hive udf to es
     */
    public BiMap<String, String> sargableOpUDFClassMapping = HashBiMap.create();

    public Set<String> rangeOp = new HashSet<String>(Arrays.asList(
            "<", ">", "<=", ">=", "between"
    ));
    public Map<String, String> reverseOps = new HashMap<String, String>();

    /**
     * Semantic conversion from hive to es
     */
    public Map<String, String> synonymOps = new HashMap<String, String>();

    public Set<String> logicOp = new HashSet<String>(Arrays.asList(
            "and", "or", "not"
    ));

    protected SargableParser() {
        init();
    }

    public void init() {
        reverseOps.put(">", "<");
        reverseOps.put(">=", "<=");
        reverseOps.put("<", ">");
        reverseOps.put("<=", ">=");
        reverseOps.put("=", "!=");
        reverseOps.put("!=", "=");

        synonymOps.put("==", "=");
        synonymOps.put("<>", "!=");
        synonymOps.put("!", "not");
        synonymOps.put("&&", "and");
        synonymOps.put("||", "or");
        synonymOps.put("rlike", "regex");

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
        if (op == null) return null;
        String rop = reverseOps.get(op);
        return rop;
    }

    public String cleanSynonymOp(String op) {
        String sop = synonymOps.get(op);
        if (sop == null)
            return op.toLowerCase();
        else
            return sop;
    }
}