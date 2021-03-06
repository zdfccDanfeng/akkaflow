package com.kent.lineage;

import java.util.ArrayList;
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.hadoop.hive.metastore.api.Partition;
import org.apache.hadoop.hive.ql.hooks.ExecuteWithHookContext;
import org.apache.hadoop.hive.ql.hooks.HookContext;
import org.apache.hadoop.hive.ql.hooks.HookContext.HookType;
import org.apache.hadoop.hive.ql.hooks.LineageInfo;
import org.apache.hadoop.hive.ql.hooks.LineageInfo.BaseColumnInfo;
import org.apache.hadoop.hive.ql.hooks.LineageInfo.Dependency;
import org.apache.hadoop.hive.ql.hooks.LineageInfo.DependencyKey;
import org.apache.hadoop.hive.ql.hooks.ReadEntity;
import org.apache.hadoop.hive.ql.hooks.WriteEntity;
import org.apache.hadoop.hive.ql.session.SessionState;
import org.apache.hadoop.hive.ql.session.SessionState.LogHelper;
import org.apache.hadoop.security.UserGroupInformation;

/**
 * Implementation of a post execute hook that simply prints out its parameters
 * to standard output.
 */
public class MyPostExecuterHook implements ExecuteWithHookContext {

  public class DependencyKeyComp implements
    Comparator<Map.Entry<DependencyKey, Dependency>> {

    @Override
    public int compare(Map.Entry<DependencyKey, Dependency> o1,
        Map.Entry<DependencyKey, Dependency> o2) {
      if (o1 == null && o2 == null) {
        return 0;
      }
      else if (o1 == null && o2 != null) {
        return -1;
      }
      else if (o1 != null && o2 == null) {
        return 1;
      }
      else {
        // Both are non null.
        // First compare the table names.
        int ret = o1.getKey().getDataContainer().getTable().getTableName()
          .compareTo(o2.getKey().getDataContainer().getTable().getTableName());

        if (ret != 0) {
          return ret;
        }

        // The table names match, so check on the partitions
        if (!o1.getKey().getDataContainer().isPartition() &&
            o2.getKey().getDataContainer().isPartition()) {
          return -1;
        }
        else if (o1.getKey().getDataContainer().isPartition() &&
            !o2.getKey().getDataContainer().isPartition()) {
          return 1;
        }

        if (o1.getKey().getDataContainer().isPartition() &&
            o2.getKey().getDataContainer().isPartition()) {
          // Both are partitioned tables.
          ret = o1.getKey().getDataContainer().getPartition().toString()
          .compareTo(o2.getKey().getDataContainer().getPartition().toString());

          if (ret != 0) {
            return ret;
          }
        }

        // The partitons are also the same so check the fieldschema
        return (o1.getKey().getFieldSchema().getName().compareTo(
            o2.getKey().getFieldSchema().getName()));
      }
    }
  }

  @Override
  public void run(HookContext hookContext) throws Exception {
    assert(hookContext.getHookType() == HookType.POST_EXEC_HOOK);
    SessionState ss = SessionState.get();
    Set<ReadEntity> inputs = hookContext.getInputs();
    Set<WriteEntity> outputs = hookContext.getOutputs();
    LineageInfo linfo = hookContext.getLinfo();
    UserGroupInformation ugi = hookContext.getUgi();
    this.run(ss,inputs,outputs,linfo,ugi);
  }

  public void run(SessionState sess, Set<ReadEntity> inputs,
      Set<WriteEntity> outputs, LineageInfo linfo,
      UserGroupInformation ugi) throws Exception {

    LogHelper console = SessionState.getConsole();
System.out.println("1");
    if (console == null) {
      return;
    }

    if (sess != null) {
    	System.out.println("2");
      console.printError("POSTHOOK: query: " + sess.getCmd().trim());
      console.printError("POSTHOOK: type: " + sess.getCommandType());
    }

    MyPostExecuterHook.printEntities(console, inputs, "POSTHOOK: Input: ");
    MyPostExecuterHook.printEntities(console, outputs, "POSTHOOK: Output: ");
System.out.println("3");
    // Also print out the generic lineage information if there is any
System.out.println("linfo="+linfo);
    if (linfo != null) {  
System.out.println("4**");
      LinkedList<Map.Entry<DependencyKey, Dependency>> entry_list =
        new LinkedList<Map.Entry<DependencyKey, Dependency>>(linfo.entrySet());
System.out.println("entry_list.size="+entry_list.size());
      Collections.sort(entry_list, new DependencyKeyComp());
      Iterator<Map.Entry<DependencyKey, Dependency>> iter = entry_list.iterator();
      while(iter.hasNext()) {  
        Map.Entry<DependencyKey, Dependency> it = iter.next();
        Dependency dep = it.getValue();
        DependencyKey depK = it.getKey();
System.out.println("4.1");
        if(dep == null) {
System.out.println("5");
          continue;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("POSTHOOK: Lineage: ");
        if (depK.getDataContainer().isPartition()) {
          Partition part = depK.getDataContainer().getPartition();
          sb.append(part.getTableName());
          sb.append(" PARTITION(");
          int i = 0;
          for (FieldSchema fs : depK.getDataContainer().getTable().getPartitionKeys()) {
            if (i != 0) {
              sb.append(",");
            }
            sb.append(fs.getName() + "=" + part.getValues().get(i++));
          }
          sb.append(")");
        }
        else {
          sb.append(depK.getDataContainer().getTable().getTableName());
        }
        sb.append("." + depK.getFieldSchema().getName() + " " +
            dep.getType() + " ");

        sb.append("[");
        for(BaseColumnInfo col: dep.getBaseCols()) {
          sb.append("("+col.getTabAlias().getTable().getTableName() + ")"
              + col.getTabAlias().getAlias() + "."
              + col.getColumn() + ", ");
        }
        sb.append("]");

        console.printError(sb.toString());
      }
    }
  }
  
  static void printEntities(LogHelper console, Set<?> entities, String prefix) {
	    List<String> strings = new ArrayList<String>();
	    for (Object o : entities) {
	      strings.add(o.toString());
	    }
	    Collections.sort(strings);
	    for (String s : strings) {
	      console.printError(prefix + s);
	    }
	  }
}
