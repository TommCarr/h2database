/*
 * Copyright 2004-2007 H2 Group. Licensed under the H2 License, Version 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.table;

import java.sql.SQLException;
import org.h2.command.Prepared;
import org.h2.command.dml.Query;
import org.h2.constant.ErrorCode;
import org.h2.engine.Session;
import org.h2.expression.Expression;
import org.h2.index.Index;
import org.h2.index.IndexType;
import org.h2.index.ViewIndex;
import org.h2.message.Message;
import org.h2.result.Row;
import org.h2.schema.Schema;
import org.h2.util.ObjectArray;
import org.h2.util.StringUtils;
import org.h2.value.Value;

public class TableView extends Table {

    private String querySQL;
    private ObjectArray tables;
    private final String[] columnNames;
    private Query viewQuery;
    private ViewIndex index;
    private boolean recursive;
    private SQLException createException; 

    public TableView(Schema schema, int id, String name, String querySQL, ObjectArray params, String[] columnNames, Session session, boolean recursive) throws SQLException {
        super(schema, id, name, false);
        this.querySQL = querySQL;
        this.columnNames = columnNames;
        this.recursive = recursive;
        index = new ViewIndex(this, querySQL, params, recursive);
        initColumnsAndTables(session);
    }

    private void initColumnsAndTables(Session session) throws SQLException {
        Column[] cols;
        removeViewFromTables();
        try {
            Prepared p = session.prepare(querySQL);
            if(!(p instanceof Query)) {
                throw Message.getSyntaxError(querySQL, 0);
            }
            Query query = (Query)p;
            tables = new ObjectArray(query.getTables());
            ObjectArray expressions = query.getExpressions();
            ObjectArray list = new ObjectArray();
            for(int i=0; i<query.getColumnCount(); i++) {
                Expression expr = (Expression) expressions.get(i);
                String name = null;
                if(columnNames != null && columnNames.length > i) {
                    name = columnNames[i];
                }
                if(name == null) {
                    name = expr.getAlias();
                }
                int type = expr.getType();
                long precision = expr.getPrecision();
                int scale = expr.getScale();
                Column col = new Column(name, type, precision, scale);
                col.setTable(this, i);
                list.add(col);
            }
            cols = new Column[list.size()];
            list.toArray(cols);
            createException = null;
            if(getId() != 0) {
                addViewToTables();
            }
            viewQuery = query;
        } catch(SQLException e) {
            createException = e;
            // if it can't be compiled, then it's a 'zero column table'
            // this avoids problems when creating the view when opening the database
            tables = new ObjectArray();
            cols = new Column[0];
            if(recursive && columnNames != null) {
                cols = new Column[columnNames.length];
                for(int i=0; i<columnNames.length; i++) {
                    cols[i] = new Column(columnNames[i], Value.STRING, 255, 0);
                }
                index.setRecursive(true);
                recursive = true;
                createException = null;
            }
            
        }
        setColumns(cols);
    }
    
    public boolean getInvalid() {
        return createException != null;
    }

    public PlanItem getBestPlanItem(Session session, int[] masks) throws SQLException {
        PlanItem item = new PlanItem();
        item.cost = index.getCost(session, masks);
        Index i2 = new ViewIndex(this, index, session, masks);
        item.setIndex(i2);
        return item;
    }
    
    public String getDropSQL() {
        return "DROP VIEW IF EXISTS " + getSQL();
    }

    public String getCreateSQL() {
        StringBuffer buff = new StringBuffer();
        buff.append("CREATE FORCE VIEW ");
        buff.append(getSQL());
        if(comment != null) {
            buff.append(" COMMENT ");
            buff.append(StringUtils.quoteStringSQL(comment));
        }
        if(columns.length>0) {
            buff.append('(');
            for(int i=0; i<columns.length; i++) {
                if(i>0) {
                    buff.append(", ");
                }
                buff.append(columns[i].getSQL());
            }
            buff.append(")");
        }
        buff.append(" AS\n");
        buff.append(querySQL);
        return buff.toString();
    }

    public void checkRename() throws SQLException {
    }

    public void lock(Session session, boolean exclusive) throws SQLException {
        // exclusive lock means: the view will be dropped
    }

    public void close(Session session) throws SQLException {
    }

    public void unlock(Session s) {
    }
    
    public boolean isLockedExclusively() {
        return false;
    }    

    public void removeIndex(String indexName) throws SQLException {
        throw Message.getUnsupportedException();
    }

    public Index addIndex(Session session, String indexName, int indexId, Column[] cols, IndexType indexType, int headPos, String comment) throws SQLException {
        throw Message.getUnsupportedException();
    }

    public void removeRow(Session session, Row row) throws SQLException {
        throw Message.getUnsupportedException();
    }

    public void addRow(Session session, Row row) throws SQLException {
        throw Message.getUnsupportedException();
    }

    public void checkSupportAlter() throws SQLException {
        // TODO view: alter what? rename is ok
        throw Message.getUnsupportedException();
    }
    
    public void truncate(Session session) throws SQLException {
        throw Message.getUnsupportedException();
    }
    
    public long getRowCount(Session session) {
        throw Message.getInternalError();
    }

    public boolean canGetRowCount() {
        // TODO view: could get the row count, but not that easy
        return false;
    }

    public boolean canDrop() {
        return true;
    }

    public String getTableType() {
        return Table.VIEW;
    }
    
    public void removeChildrenAndResources(Session session) throws SQLException {
        removeViewFromTables();
        super.removeChildrenAndResources(session);
        querySQL = null;
        index = null;
        invalidate();
    }
    
    public Index getScanIndex(Session session) throws SQLException {
        if(createException != null) {
            String msg = createException.getMessage();
            throw Message.getSQLException(ErrorCode.VIEW_IS_INVALID_2, new String[]{getSQL(), msg}, createException);
        }
        PlanItem item = getBestPlanItem(session, null);
        return item.getIndex();
    }
    
    public ObjectArray getIndexes() {
        return null;
    }

    public ObjectArray getTables() {
        return tables;
    }

    public void recompile(Session session) throws SQLException {
        for(int i=0; i<tables.size(); i++) {
            Table t = (Table)tables.get(i);
            t.removeView(this);
        }
        tables.clear();
        initColumnsAndTables(session);
    }

    public long getMaxDataModificationId() {
        if(createException != null) {
            throw Message.getInternalError();
        }
        if(viewQuery == null) {
            return Long.MAX_VALUE;
        }
        return viewQuery.getMaxDataModificationId();
    }

    public Index getUniqueIndex() {
        return null;
    }
    
    private void removeViewFromTables() {
        if(tables != null) {
            for(int i=0; i<tables.size(); i++) {
                Table t = (Table)tables.get(i);
                t.removeView(this);
            }
            tables.clear();
        }
    }

    private void addViewToTables() {
        for(int i=0; i<tables.size(); i++) {
            Table t = (Table)tables.get(i);
            t.addView(this);
        }
    }
    
}
