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
package org.elasticsearch.hadoop.hive;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hive.ql.metadata.HiveStoragePredicateHandler;
import org.apache.hadoop.hive.ql.plan.ExprNodeDesc;
import org.apache.hadoop.hive.ql.plan.ExprNodeGenericFuncDesc;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFOPAnd;
import org.apache.hadoop.hive.serde2.Deserializer;
import org.apache.hadoop.mapred.JobConf;
import org.elasticsearch.hadoop.cfg.HadoopSettingsManager;
import org.elasticsearch.hadoop.cfg.Settings;
import org.elasticsearch.hadoop.hive.pushdown.EsSargableParser;
import org.elasticsearch.hadoop.hive.pushdown.HiveTreeBuilder;
import org.elasticsearch.hadoop.hive.pushdown.Pair;
import org.elasticsearch.hadoop.hive.pushdown.SargableParser;
import org.elasticsearch.hadoop.hive.pushdown.node.Node;
import org.elasticsearch.hadoop.hive.pushdown.node.OpNode;
import org.elasticsearch.hadoop.hive.pushdown.parse.EsQueryParser;
import org.elasticsearch.hadoop.hive.pushdown.parse.EsTreeParser;
import org.elasticsearch.hadoop.hive.pushdown.parse.query.JsonObj;
import org.elasticsearch.hadoop.rest.query.MatchAllQueryBuilder;
import org.elasticsearch.hadoop.rest.query.QueryBuilder;
import org.elasticsearch.hadoop.rest.query.QueryUtils;
import org.elasticsearch.hadoop.util.FieldAlias;
import org.elasticsearch.hadoop.util.SettingsUtils;

import java.util.ArrayList;
import java.util.List;

import static org.elasticsearch.hadoop.cfg.ConfigurationOptions.ES_QUERY;


/**
 * EsStoragePredicateHandler is an entrance class for implementing pushdown optimization.
 */
public class EsStoragePredicateHandler implements HiveStoragePredicateHandler {

    private static Log log = LogFactory.getLog(EsStoragePredicateHandler.class);

    private static EsStoragePredicateHandler _instance;

    private EsStoragePredicateHandler() {
    }

    public static EsStoragePredicateHandler getInstance() {
        if (_instance == null) {
            synchronized (EsStoragePredicateHandler.class) {
                if (_instance == null) {
                    _instance = new EsStoragePredicateHandler();
                }
            }
        }
        return _instance;
    }

    private Pair<Node, JsonObj> optimizePushdown(JobConf jobConf, Deserializer deserializer, ExprNodeDesc exprNodeDesc) {
        if (exprNodeDesc == null) {
            return new Pair<Node, JsonObj>(null, null);
        }

        // load settings
        Settings settings = HadoopSettingsManager.loadFrom(jobConf);
        boolean isES50 = SettingsUtils.isEs50(settings);
        log.info("[PushDown][isES50] : " + isES50);

        SargableParser sargableParser = EsSargableParser.getInstance();
        if (sargableParser == null) {
            return new Pair<Node, JsonObj>(null, null);
        }

        //get hive to es field alias mapping
        FieldAlias fieldAlias = HiveUtils.alias(settings);

        //create a hive tree builder for converting ExprNodeDesc to OpNode.
        HiveTreeBuilder hiveTreeBuilder = new HiveTreeBuilder(fieldAlias, sargableParser);

        //create a es tree parser for converting OpNode to JsonObj.
        EsTreeParser parser = new EsTreeParser(sargableParser, isES50);

        String preFilterQuery = settings.getQuery();
        // if exists es.query prop, then it is a necessary condition to add to the pushdown optimization plan
        if (StringUtils.isNotEmpty(preFilterQuery)) {
            QueryBuilder rawQuery = QueryUtils.parseQuery(settings);

            if (rawQuery != null && !(rawQuery instanceof MatchAllQueryBuilder)) {
                log.info("[PushDown][Pre Filter " + ES_QUERY + "] : " + preFilterQuery);
                JsonObj preFilterJson = new EsQueryParser(rawQuery, isES50).parse();
                //add a pre filter condition.
                parser.setPreFilterJson(preFilterJson);
            }
        }

        //build a operator node tree
        OpNode root = hiveTreeBuilder.build(exprNodeDesc);

        if (root != null) {
            //parser the operator node tree and get a json format elasticsearch-query.
            JsonObj esQuery = parser.parse(root);
            if (esQuery != null) {
                log.info("[PushDown][Final Filter " + ES_QUERY + "] : " + esQuery.toQuery());
            }
            return new Pair<Node, JsonObj>(root, esQuery);
        }

        return new Pair<Node, JsonObj>(null, null);
    }

    private ExprNodeGenericFuncDesc translate2ExprNodeGenericFuncDesc(ExprNodeDesc desc) {
        if (desc instanceof ExprNodeGenericFuncDesc) {
            return (ExprNodeGenericFuncDesc) desc;
        }
        else {
            return null;
        }
    }

    private String serializePPD(JsonObj obj) {
        if (obj != null) {
            return obj.toString();
        }
        else {
            return null;
        }
    }

    @Override
    public DecomposedPredicate decomposePredicate(JobConf jobConf, Deserializer deserializer, ExprNodeDesc exprNodeDesc) {
        if (exprNodeDesc == null) {
            return null;
        }
        Pair<Node, JsonObj> pair = this.optimizePushdown(jobConf, deserializer, exprNodeDesc);

        OpNode root = (OpNode) pair.getFirst();

        if (root == null || root.getChildren() == null || root.getChildren().isEmpty() || root.isScanAllTable()) {
            return null;
        }

        ExprNodeGenericFuncDesc residual = null;
        if (!root.isAllOptimizable()) {
            residual = findResidual(root);
        }

        DecomposedPredicate decomposedPredicate = new DecomposedPredicate();
        decomposedPredicate.pushedPredicate = translate2ExprNodeGenericFuncDesc(exprNodeDesc);
        decomposedPredicate.residualPredicate = residual;
        decomposedPredicate.pushedPredicateObject = serializePPD(pair.getSecond());

        if (decomposedPredicate.pushedPredicate == null) {
            return null;
        } else {
            return decomposedPredicate;
        }
    }

    /**
     * recursively traverses all nodes
     *
     * @param node
     * @return
     */
    private ExprNodeGenericFuncDesc findResidual(OpNode node) {
        if (node.getExprNode().getGenericUDF().getClass().equals(GenericUDFOPAnd.class)) {
            List<ExprNodeDesc> childrenExprs = new ArrayList<ExprNodeDesc>();
            List<Node> childrenNodes = new ArrayList<Node>();

            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                for (Node child : node.getChildren()) {
                    if (child instanceof OpNode) {
                        if (!((OpNode) child).isAllOptimizable()) {
                            childrenExprs.add(child.getExprNode());
                            childrenNodes.add(child);
                        }
                    }
                }
            }

            if (childrenExprs.isEmpty()) {
                return null;
            } else if (childrenExprs.size() == 2) {
                return node.getExprNode();
            } else {
                return findResidual((OpNode) childrenNodes.get(0));
            }
        } else {
            return node.getExprNode();
        }
    }

}
