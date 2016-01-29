/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.symmetric.db.mssql;

import java.util.HashMap;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.AbstractTriggerTemplate;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.util.FormatUtils;

public class MsSqlTriggerTemplate extends AbstractTriggerTemplate {

    public MsSqlTriggerTemplate(ISymmetricDialect symmetricDialect) {
        super(symmetricDialect);

        boolean castToNVARCHAR = symmetricDialect.getParameterService().is(ParameterConstants.MSSQL_USE_NTYPES_FOR_SYNC);
        
        String triggerExecuteAs = symmetricDialect.getParameterService().getString(ParameterConstants.MSSQL_TRIGGER_EXECUTE_AS, "self");
                
        // @formatter:off
        emptyColumnTemplate = "''" ;
        stringColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' + replace(replace(convert("+
        (castToNVARCHAR ? "n" : "")
        +"varchar($(columnSize)),$(tableAlias).\"$(columnName)\") $(masterCollation),'\\','\\\\'),'\"','\\\"') + '\"' end" ;
        geometryColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' + replace(replace(convert("+(castToNVARCHAR ? "n" : "")+"varchar(max),$(tableAlias).\"$(columnName)\".STAsText()) $(masterCollation),'\\','\\\\'),'\"','\\\"') + '\"' end" ;
        geographyColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' + replace(replace(convert("+(castToNVARCHAR ? "n" : "")+"varchar(max),$(tableAlias).\"$(columnName)\".STAsText()) $(masterCollation),'\\','\\\\'),'\"','\\\"') + '\"' end" ;
        numberColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else ('\"' + convert(varchar(40), $(tableAlias).\"$(columnName)\",2) + '\"') end" ;
        datetimeColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else ('\"' + convert(varchar,$(tableAlias).\"$(columnName)\",121) + '\"') end" ;
        clobColumnTemplate = "case when $(origTableAlias).\"$(columnName)\" is null then '' else '\"' + replace(replace(cast($(origTableAlias).\"$(columnName)\" as "+(castToNVARCHAR ? "n" : "")+"varchar(max)),'\\','\\\\'),'\"','\\\"') + '\"' end" ;
        blobColumnTemplate = "case when $(origTableAlias).\"$(columnName)\" is null then '' else '\"' + replace(replace($(defaultCatalog)dbo.sym_base64_encode(CONVERT(VARBINARY(max), $(origTableAlias).\"$(columnName)\")),'\\','\\\\'),'\"','\\\"') + '\"' end" ;
        binaryColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' + replace(replace($(defaultCatalog)dbo.sym_base64_encode(CONVERT(VARBINARY(max), $(tableAlias).\"$(columnName)\")),'\\','\\\\'),'\"','\\\"') + '\"' end" ;
        booleanColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' when $(tableAlias).\"$(columnName)\" = 1 then '\"1\"' else '\"0\"' end" ;
        triggerConcatCharacter = "+" ;
        newTriggerValue = "inserted" ;
        oldTriggerValue = "deleted" ;
        oldColumnPrefix = "" ;
        newColumnPrefix = "" ;

        sqlTemplates = new HashMap<String,String>();

        sqlTemplates.put("insertTriggerTemplate" ,
"create trigger $(triggerName) on $(schemaName)$(tableName) with execute as "+triggerExecuteAs+" after insert as                                                                                                \n" +
"   begin                                                                                                                                                                  \n" +
"     declare @NCT int \n" +
"     set @NCT = @@OPTIONS & 512 \n" +
"     set nocount on                                                                                                                                                       \n" +
"     declare @TransactionId varchar(1000)                                                                                                                                 \n" +
"     if (@@TRANCOUNT > 0) begin                                                                                                                                           \n" +
"       select @TransactionId = convert(VARCHAR(1000),transaction_id) from sys.dm_exec_requests where session_id=@@SPID and open_transaction_count > 0                     \n" +
"     end                                                                                                                                                                  \n" +
"     if ($(syncOnIncomingBatchCondition)) begin                                                                                                                           \n" +
"         insert into $(defaultCatalog)$(defaultSchema)$(prefixName)_data \n" +
"			(table_name, event_type, trigger_hist_id, row_data, channel_id, transaction_id, source_node_id, external_data, create_time) \n" +
"          select '$(targetTableName)','I', $(triggerHistoryId), $(columns), \n" +
"                  $(channelExpression), $(txIdExpression), $(defaultCatalog)dbo.sym_node_disabled(), $(externalSelect), current_timestamp \n" +
"       $(if:containsBlobClobColumns)                                                                                                                                      \n" +
"          from inserted inner join $(schemaName)$(tableName) $(origTableAlias) on $(tableNewPrimaryKeyJoin) where $(syncOnInsertCondition)\n" +
"       $(else:containsBlobClobColumns)                                                                                                                                    \n" +
"          from inserted where $(syncOnInsertCondition)                                                                                   \n" +
"       $(end:containsBlobClobColumns)                                                                                                                                     \n" +
"     end                                                                                                                                                                  \n" +
"     $(custom_on_insert_text)                                                                                                                                             \n" +
"     if (@NCT = 0) set nocount off                                                                                                                                        \n" +
"   end                                                                                                                                                                    \n" +
"---- go");

        sqlTemplates.put("updateTriggerTemplate" ,
"create trigger $(triggerName) on $(schemaName)$(tableName) with execute as "+triggerExecuteAs+" after update as                                                                                                \n" +
"   begin                                                                                                                                                                  \n" +
"     declare @NCT int \n" +
"     set @NCT = @@OPTIONS & 512 \n" +
"     set nocount on                                                                                                                                                       \n" +
"     declare @TransactionId varchar(1000)                                                                                                                                 \n" +
"     if (@@TRANCOUNT > 0) begin                                                                                                                                           \n" +
"       select @TransactionId = convert(VARCHAR(1000),transaction_id) from sys.dm_exec_requests where session_id=@@SPID and open_transaction_count > 0                     \n" +
"     end                                                                                                                                                                  \n" +
"     if ($(syncOnIncomingBatchCondition)) begin                                                                                                                           \n" +
"         insert into $(defaultCatalog)$(defaultSchema)$(prefixName)_data (table_name, event_type, trigger_hist_id, row_data, pk_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time) \n" +
"             select '$(targetTableName)','U', $(triggerHistoryId), $(columns), $(oldKeys), $(oldColumns), $(channelExpression), "+
"               $(txIdExpression), $(defaultCatalog)dbo.sym_node_disabled(), $(externalSelect), current_timestamp\n" +
"       $(if:containsBlobClobColumns)                                                                                                                                      \n" +
"          from inserted inner join $(schemaName)$(tableName) $(origTableAlias) on $(tableNewPrimaryKeyJoin) inner join deleted on $(oldNewPrimaryKeyJoin) where $(syncOnUpdateCondition)\n" +
"       $(else:containsBlobClobColumns)                                                                                                                                    \n" +
"          from inserted inner join deleted on $(oldNewPrimaryKeyJoin) where $(syncOnUpdateCondition)                                    \n" +
"       $(end:containsBlobClobColumns)                                                                                                                                     \n" +
"       end                                                                                                                                                                \n" +
"       $(custom_on_update_text)                                                                                                                                             \n" +
"     if (@NCT = 0) set nocount off                                                                                                                                        \n" +
"   end                                                                                                                                                                    \n" +
"---- go");

        sqlTemplates.put("updateHandleKeyUpdatesTriggerTemplate" ,
"create trigger $(triggerName) on $(schemaName)$(tableName) with execute as "+triggerExecuteAs+" after update as                                                                                                                             \n" +
"   begin                                                                                                                                                                  \n" +
"     declare @NCT int \n" +
"     set @NCT = @@OPTIONS & 512 \n" +
"     set nocount on                                                                                                                                                       \n" +
"     declare @TransactionId varchar(1000)                                                                                                                                 \n" +
"                                                                                                                                                                          \n" +
"     if (@@TRANCOUNT > 0) begin                                                                                                                                           \n" +
"       select @TransactionId = convert(VARCHAR(1000),transaction_id) from sys.dm_exec_requests where session_id=@@SPID and open_transaction_count > 0                                            \n" +
"     end                                                                                                                                                                  \n" +
"     if ($(syncOnIncomingBatchCondition)) begin                                                                                                                           \n" +
"         insert into $(defaultCatalog)$(defaultSchema)$(prefixName)_data (table_name, event_type, trigger_hist_id, row_data, pk_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time) \n" +
"             select '$(targetTableName)','U', $(triggerHistoryId), $(columns), $(oldKeys), $(oldColumns), $(channelExpression), "+
"               $(txIdExpression), $(defaultCatalog)dbo.sym_node_disabled(), $(externalSelect), current_timestamp\n" +
"       $(if:containsBlobClobColumns)                                                                                                                                      \n" +
"          from (select $(nonBlobColumns), row_number() over (order by (select 1)) as __row_num from inserted) inserted inner join $(schemaName)$(tableName) $(origTableAlias) on $(tableNewPrimaryKeyJoin) inner join (select $(nonBlobColumns), row_number() over (order by (select 1)) as __row_num from deleted)deleted on (inserted.__row_num = deleted.__row_num) where $(syncOnUpdateCondition)\n" +
"       $(else:containsBlobClobColumns)                                                                                                                                    \n" +
"          from (select *, row_number() over (order by (select 1)) as __row_num from inserted) inserted inner join (select *, row_number() over (order by (select 1)) as __row_num from deleted) deleted on (inserted.__row_num = deleted.__row_num) where $(syncOnUpdateCondition)                                    \n" +
"       $(end:containsBlobClobColumns)                                                                                                                                     \n" +
"       end                                                                                                                                                                \n" +
"       $(custom_on_update_text)                                                                                                                                             \n" +
"     if (@NCT = 0) set nocount off                                                                                                                                        \n" +
"   end                                                                                                                                                                    \n" +
"---- go");

        sqlTemplates.put("deleteTriggerTemplate" ,
"create trigger $(triggerName) on $(schemaName)$(tableName) with execute as "+triggerExecuteAs+" after delete as                                                                                                                             \n" +
"  begin                                                                                                                                                                  \n" +
"    declare @NCT int \n" +
"    set @NCT = @@OPTIONS & 512 \n" +
"    set nocount on                                                                                                                                                       \n" +
"    declare @TransactionId varchar(1000)                                                                                                                                 \n" +
"    if (@@TRANCOUNT > 0) begin                                                                                                                                           \n" +
"       select @TransactionId = convert(VARCHAR(1000),transaction_id)    from sys.dm_exec_requests where session_id=@@SPID and open_transaction_count > 0                                           \n" +
"    end                                                                                                                                                                  \n" +
"    if ($(syncOnIncomingBatchCondition)) begin                                                                                                                           \n" +
"        insert into $(defaultCatalog)$(defaultSchema)$(prefixName)_data (table_name, event_type, trigger_hist_id, pk_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time) \n" +
"        select '$(targetTableName)','D', $(triggerHistoryId), $(oldKeys), $(oldColumns), $(channelExpression), \n" +
"              $(txIdExpression), $(defaultCatalog)dbo.sym_node_disabled(), $(externalSelect), current_timestamp\n" +
"        from deleted where $(syncOnDeleteCondition)                                                                      \n" +
"    end                                                                                                                                                                  \n" +
"    $(custom_on_delete_text)                                                                                                                                              \n" +
"    if (@NCT = 0) set nocount off                                                                                                                                         \n" +
"   end                                                                                                                                                                    \n" +
"---- go");

        sqlTemplates.put("initialLoadSqlTemplate" ,
"select $(columns) from $(schemaName)$(tableName) t where $(whereClause) " );

    }

    @Override
    protected String replaceTemplateVariables(DataEventType dml, Trigger trigger,
            TriggerHistory history, Channel channel, String tablePrefix, Table originalTable, Table table,
            String defaultCatalog, String defaultSchema, String ddl) {
        ddl =  super.replaceTemplateVariables(dml, trigger, history, channel, tablePrefix, originalTable, table,
                defaultCatalog, defaultSchema, ddl);
        Column[] columns = table.getPrimaryKeyColumns();
        ddl = FormatUtils.replace("declareOldKeyVariables",
                buildKeyVariablesDeclare(columns, "old"), ddl);
        ddl = FormatUtils.replace("declareNewKeyVariables",
                buildKeyVariablesDeclare(columns, "new"), ddl);
        
        ddl = FormatUtils.replace("nonBlobColumns", buildNonLobColumnsString(table), ddl);
        return ddl;
    }
    
    private String buildNonLobColumnsString(Table table){
    	StringBuilder builder = new StringBuilder();
    	
    	for(Column column : table.getColumns()){
    		boolean isLob = symmetricDialect.getPlatform().isLob(column.getMappedTypeCode());
    		if(isLob){
    			continue;
    		}
    		if(builder.length() > 0){
    			builder.append(",");
    		}
    		builder.append('"');
    		builder.append(column.getName());
    		builder.append('"');
    	}
    	
    	return builder.toString();
    }

    @Override
    protected boolean requiresEmptyLobTemplateForDeletes() {
        return true;
    }
}
