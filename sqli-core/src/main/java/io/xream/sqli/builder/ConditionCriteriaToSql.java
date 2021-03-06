/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.xream.sqli.builder;

import io.xream.sqli.exception.ParsingException;
import io.xream.sqli.filter.BaseTypeFilter;
import io.xream.sqli.parser.BeanElement;
import io.xream.sqli.parser.Parsed;
import io.xream.sqli.parser.Parser;
import io.xream.sqli.support.TimestampSupport;
import io.xream.sqli.util.BeanUtil;
import io.xream.sqli.util.SqliStringUtil;

import java.util.Iterator;
import java.util.List;

/**
 * @Author Sim
 */
public interface ConditionCriteriaToSql extends KeyMapper{

    default void buildConditionSql(StringBuilder sb, List<BuildingBlock> buildingBlockList) {
        if (buildingBlockList == null || buildingBlockList.isEmpty())
            return;
        for (BuildingBlock buildingBlock : buildingBlockList) {

            if (buildingBlock.getPredicate() == PredicateAndOtherScript.SUB) {

                if (buildingBlock.getSubList().isEmpty())
                    continue;
                buildingBlock.getSubList().get(0).setConjunction(ConjunctionAndOtherScript.NONE);
                sb.append(buildingBlock.getConjunction().sql());
                sb.append(SqlScript.SPACE).append(SqlScript.LEFT_PARENTTHESIS).append(SqlScript.SPACE);
                buildConditionSql(sb, buildingBlock.getSubList());
                sb.append(SqlScript.SPACE).append(SqlScript.RIGHT_PARENTTHESIS);
                continue;
            }

            sb.append(buildingBlock.getConjunction().sql()).append(buildingBlock.getKey()).append(buildingBlock.getPredicate().sql());
            if (buildingBlock.getValue() != null) {
                if (buildingBlock.getPredicate() == PredicateAndOtherScript.IN || buildingBlock.getPredicate() == PredicateAndOtherScript.NOT_IN) {
                    List<Object> inList = (List<Object>) buildingBlock.getValue();
                    Object v = inList.get(0);
                    Class<?> vType = v.getClass();
                    buildIn(sb, vType, inList);
                } else if (!(buildingBlock.getPredicate() == PredicateAndOtherScript.IS_NULL
                        || buildingBlock.getPredicate() == PredicateAndOtherScript.IS_NOT_NULL
                        || buildingBlock.getPredicate() == PredicateAndOtherScript.X)) {
                    sb.append(SqlScript.PLACE_HOLDER).append(SqlScript.SPACE);
                }
            }
        }

    }


    static void buildIn(StringBuilder sb, Class clz, List<? extends Object> inList) {

        sb.append(SqlScript.LEFT_PARENTTHESIS).append(SqlScript.SPACE);//"( "

        int length = inList.size();
        if (clz == String.class) {

            for (int j = 0; j < length; j++) {
                Object value = inList.get(j);
                if (value == null || SqliStringUtil.isNullOrEmpty(value.toString()))
                    continue;
                value = filter(value.toString());
                sb.append(SqlScript.SINGLE_QUOTES).append(value).append(SqlScript.SINGLE_QUOTES);//'string'
                if (j < length - 1) {
                    sb.append(SqlScript.COMMA);
                }
            }

        } else if (BeanUtil.isEnum(clz)) {
            for (int j = 0; j < length; j++) {
                Object value = inList.get(j);
                if (value == null)
                    continue;
                String ev = null;
                if (value instanceof String){
                    ev = (String) value;
                }else {
                    ev = ((Enum) value).name();
                }
                sb.append(SqlScript.SINGLE_QUOTES).append(ev).append(SqlScript.SINGLE_QUOTES);//'string'
                if (j < length - 1) {
                    sb.append(SqlScript.COMMA);
                }
            }
        } else {
            for (int j = 0; j < length; j++) {
                Object value = inList.get(j);
                if (value == null)
                    continue;
                sb.append(value);
                if (j < length - 1) {
                    sb.append(SqlScript.COMMA);
                }
            }
        }

        sb.append(SqlScript.SPACE).append(SqlScript.RIGHT_PARENTTHESIS);
    }

    static String filter(String sql) {
        sql = sql.replace("drop", SqlScript.SPACE)
                .replace(";", SqlScript.SPACE);// 手动拼接SQL,
        return sql;
    }

    interface Filter {

        default void filter(List<BuildingBlock> buildingBlockList, CriteriaCondition criteria) {

            if (buildingBlockList == null || buildingBlockList.isEmpty())
                return;

            Iterator<BuildingBlock> ite = buildingBlockList.iterator();
            while (ite.hasNext()) {
                BuildingBlock buildingBlock = ite.next();
                PredicateAndOtherScript p = buildingBlock.getPredicate();
                String key = buildingBlock.getKey();
                if (p == PredicateAndOtherScript.SUB){
                    filter(buildingBlock.getSubList(),criteria);
                    if (buildingBlock.getSubList().isEmpty()) {
                        ite.remove();
                    }
                }else if (p == PredicateAndOtherScript.EQ
                        || p == PredicateAndOtherScript.NE
                        || p == PredicateAndOtherScript.GT
                        || p == PredicateAndOtherScript.GTE
                        || p == PredicateAndOtherScript.LT
                        || p == PredicateAndOtherScript.LTE) {

                    if (key.contains(".")){
                        String[] arr = key.split("\\.");
                        String alia = arr[0];
                        String clzName = criteria.getAliaMap().get(alia);
                        if (clzName == null)
                            clzName = alia;
                        Parsed parsed = Parser.get(clzName);
                        if (BaseTypeFilter.isBaseType_0(arr[1], buildingBlock.getValue(),parsed)){
                            ite.remove();
                        }else{
                            BeanElement be = parsed.getElement(arr[1]);
                            if (be == null){
                                throw new ParsingException("property of " + parsed.getClz() +" not exists: " + arr[1]);
                            }
                            TimestampSupport.testNumberValueToDate(be.clz, buildingBlock);
                            if (buildingBlock.getValue() == null)
                                ite.remove();
                        }
                    }else{
                        Parsed parsed = criteria.getParsed();
                        if (BaseTypeFilter.isBaseType_0(key, buildingBlock.getValue(),parsed)){
                            ite.remove();
                        }else{
                            BeanElement be = parsed.getElement(key);
                            if (be == null){
                                throw new ParsingException("property of " + parsed.getClz() +" not exists: " + key);
                            }
                            TimestampSupport.testNumberValueToDate(be.clz, buildingBlock);
                            if (buildingBlock.getValue() == null)
                                ite.remove();
                        }
                    }
                }else if (p == PredicateAndOtherScript.IN
                        || p == PredicateAndOtherScript.NOT_IN) {

                    List valueList = (List) buildingBlock.getValue();
                    if (valueList.size() > 1)
                        continue;

                    if (key.contains(".")){
                        if (BaseTypeFilter.isBaseType_0(key,valueList.get(0),criteria)){
                            ite.remove();
                        }
                    }else{
                        Parsed parsed = criteria.getParsed();
                        if (BaseTypeFilter.isBaseType_0(key,valueList.get(0),parsed)){
                            ite.remove();
                        }
                    }
                }
                List<BuildingBlock> subList = buildingBlock.getSubList();
                if (subList == null || subList.isEmpty())
                    continue;
                filter(subList,criteria);
            }
        }

    }

    interface Pre {
        default void pre(List<Object> valueList, List<BuildingBlock> buildingBlockList) {
            for (BuildingBlock buildingBlock : buildingBlockList) {
                if (buildingBlock.getPredicate() == PredicateAndOtherScript.SUB){
                    pre(valueList, buildingBlock.getSubList());
                    continue;
                }else if (buildingBlock.getPredicate() == PredicateAndOtherScript.X) {
                    Object value = buildingBlock.getValue();
                    if (value == null)
                        continue;
                    if (value instanceof Object[]) {
                        for (Object v : (Object[])value){
                            add(valueList,v);
                        }
                    }
                }else if (!(buildingBlock.getPredicate() == PredicateAndOtherScript.IN
                        || buildingBlock.getPredicate() == PredicateAndOtherScript.NOT_IN
                        || buildingBlock.getPredicate() == PredicateAndOtherScript.IS_NULL
                        || buildingBlock.getPredicate() == PredicateAndOtherScript.IS_NOT_NULL)) {
                    Object v = buildingBlock.getValue();
                    add(valueList, v);
                }
//                 else if (buildingBlock.getPredicate() == PredicateAndOtherScript.IN
//                        || buildingBlock.getPredicate() == PredicateAndOtherScript.NOT_IN
//                        || buildingBlock.getPredicate() == PredicateAndOtherScript.IS_NULL
//                        || buildingBlock.getPredicate() == PredicateAndOtherScript.IS_NOT_NULL) {
//                    //....
//                }
                // NO JSON OBJECT CONDITION
            }
        }

        static void add(List<Object> valueList, Object value){
            if (BeanUtil.isEnum(value.getClass())) {
                try {
                    valueList.add(((Enum) value).name());
                } catch (Exception e) {
                }
            } else {
                valueList.add(value);
            }
        }
    }

}
