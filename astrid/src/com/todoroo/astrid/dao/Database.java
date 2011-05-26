/*
 * Copyright (c) 2009, Todoroo Inc
 * All Rights Reserved
 * http://www.todoroo.com
 */
package com.todoroo.astrid.dao;

import com.todoroo.andlib.data.AbstractDatabase;
import com.todoroo.andlib.data.AbstractModel;
import com.todoroo.andlib.data.Property;
import com.todoroo.andlib.data.Table;
import com.todoroo.andlib.service.ContextManager;
import com.todoroo.astrid.data.Metadata;
import com.todoroo.astrid.data.StoreObject;
import com.todoroo.astrid.data.TagData;
import com.todoroo.astrid.data.Task;
import com.todoroo.astrid.data.Update;
import com.todoroo.astrid.provider.Astrid2TaskProvider;
import com.todoroo.astrid.provider.Astrid3ContentProvider;
import com.todoroo.astrid.widget.TasksWidget;

/**
 * Database wrapper
 *
 * @author Tim Su <tim@todoroo.com>
 *
 */
@SuppressWarnings("nls")
public class Database extends AbstractDatabase {

    // --- constants

    /**
     * Database version number. This variable must be updated when database
     * tables are updated, as it determines whether a database needs updating.
     */
    public static final int VERSION = 17;

    /**
     * Database name (must be unique)
     */
    private static final String NAME = "database";

    /**
     * List of table/ If you're adding a new table, add it to this list and
     * also make sure that our SQLite helper does the right thing.
     */
    public static final Table[] TABLES =  new Table[] {
        Task.TABLE,
        Metadata.TABLE,
        StoreObject.TABLE,
        TagData.TABLE,
        Update.TABLE,
    };

    // --- listeners

    public Database() {
        super();
        addListener(new DatabaseUpdateListener() {
            @Override
            public void onDatabaseUpdated() {
                Astrid2TaskProvider.notifyDatabaseModification();
                Astrid3ContentProvider.notifyDatabaseModification();
                TasksWidget.updateWidgets(ContextManager.getContext());
            }
        });
    }

    // --- implementation

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    protected int getVersion() {
        return VERSION;
    }

    @Override
    public Table[] getTables() {
        return TABLES;
    }

    /**
     * Create indices
     */
    @Override
    protected synchronized void onCreateTables() {
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE INDEX IF NOT EXISTS md_tid ON ").
            append(Metadata.TABLE).append('(').
                append(Metadata.TASK.name).
            append(')');
        database.execSQL(sql.toString());
        sql.setLength(0);

        sql.append("CREATE INDEX IF NOT EXISTS md_tkid ON ").
            append(Metadata.TABLE).append('(').
                append(Metadata.TASK.name).append(',').
                append(Metadata.KEY.name).
            append(')');
        database.execSQL(sql.toString());
        sql.setLength(0);

        sql.append("CREATE INDEX IF NOT EXISTS so_id ON ").
            append(StoreObject.TABLE).append('(').
                append(StoreObject.TYPE.name).append(',').
                append(StoreObject.ITEM.name).
            append(')');
        database.execSQL(sql.toString());
        sql.setLength(0);

        sql.append("CREATE INDEX IF NOT EXISTS up_tid ON ").
            append(Update.TABLE).append('(').
                append(Update.TASK.name).
            append(')');
        database.execSQL(sql.toString());
        sql.setLength(0);

        sql.append("CREATE INDEX IF NOT EXISTS up_pid ON ").
            append(Update.TABLE).append('(').
                append(Update.TAG.name).
            append(')');
        database.execSQL(sql.toString());
        sql.setLength(0);
    }

    @Override
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value="SF_SWITCH_FALLTHROUGH")
    protected synchronized boolean onUpgrade(int oldVersion, int newVersion) {
        SqlConstructorVisitor visitor = new SqlConstructorVisitor();
        switch(oldVersion) {
        case 1: {
            database.execSQL("ALTER TABLE " + Task.TABLE.name + " ADD " +
                Task.RECURRENCE.accept(visitor, null));
        }
        case 2: {
            for(Property<?> property : new Property<?>[] { Metadata.VALUE2,
                    Metadata.VALUE3, Metadata.VALUE4, Metadata.VALUE5 })
                database.execSQL("ALTER TABLE " + Metadata.TABLE.name + " ADD " +
                        property.accept(visitor, null));
        }
        case 3: {
            database.execSQL(createTableSql(visitor, StoreObject.TABLE.name, StoreObject.PROPERTIES));

            onCreateTables();
        }
        case 4: {
            database.execSQL("ALTER TABLE " + Task.TABLE.name + " ADD " +
                Task.DETAILS.accept(visitor, null));
        }
        case 5: {
            database.execSQL("ALTER TABLE " + Task.TABLE.name + " ADD " +
                Task.REMINDER_SNOOZE.accept(visitor, null));
        }
        case 6: {
            database.execSQL("ALTER TABLE " + Task.TABLE.name + " ADD " +
                Task.DETAILS_DATE.accept(visitor, null));
        }
        case 7: {
            database.execSQL("ALTER TABLE " + Metadata.TABLE.name + " ADD " +
                Metadata.CREATION_DATE.accept(visitor, null));
        }
        case 8: {
            // not needed anymore
        }
        case 9: {
            database.execSQL(createTableSql(visitor, Update.TABLE.name, Update.PROPERTIES));
            onCreateTables();

            for(Property<?> property : new Property<?>[] { Task.REMOTE_ID,
                    Task.USER_ID, Task.USER, Task.COMMENT_COUNT })
                database.execSQL("ALTER TABLE " + Task.TABLE.name + " ADD " +
                        property.accept(visitor, null));
        }
        case 10: {
            database.execSQL("ALTER TABLE " + Task.TABLE.name + " ADD " +
                    Task.SHARED_WITH.accept(visitor, null));
        }
        case 11: {
            database.execSQL(createTableSql(visitor, TagData.TABLE.name, TagData.PROPERTIES));
        }
        case 12: {
            database.execSQL("ALTER TABLE " + Update.TABLE.name + " ADD " +
                    Update.TAG.accept(visitor, null));
        }
        case 13: {
            database.execSQL("ALTER TABLE " + TagData.TABLE.name + " ADD " +
                    TagData.MEMBERS.accept(visitor, null));
            database.execSQL("ALTER TABLE " + TagData.TABLE.name + " ADD " +
                    TagData.MEMBER_COUNT.accept(visitor, null));
        }
        case 14: {
            database.execSQL("ALTER TABLE " + TagData.TABLE.name + " ADD " +
                    TagData.TASK_COUNT.accept(visitor, null));
        }
        case 15: {
            database.execSQL("ALTER TABLE " + Task.TABLE.name + " ADD " +
                    Task.LAST_SYNC.accept(visitor, null));
        }
        case 16: {
            database.execSQL("ALTER TABLE " + Task.TABLE.name + " ADD " +
                    Task.CREATOR_ID.accept(visitor, null));
        }

        return true;
        }

        return false;
    }

    /**
     * Create table generation SQL
     * @param sql
     * @param tableName
     * @param properties
     * @return
     */
    public String createTableSql(SqlConstructorVisitor visitor,
            String tableName, Property<?>[] properties) {
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append('(').
            append(AbstractModel.ID_PROPERTY).append(" INTEGER PRIMARY KEY AUTOINCREMENT");
        for(Property<?> property : properties) {
            if(AbstractModel.ID_PROPERTY.name.equals(property.name))
                continue;
            sql.append(',').append(property.accept(visitor, null));
        }
        sql.append(')');
        return sql.toString();
    }

}

