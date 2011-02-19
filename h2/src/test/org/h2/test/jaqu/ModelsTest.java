/*
 * Copyright 2004-2011 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: James Moger
 */
package org.h2.test.jaqu;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.h2.jaqu.Db;
import org.h2.jaqu.DbInspector;
import org.h2.jaqu.DbUpgrader;
import org.h2.jaqu.DbVersion;
import org.h2.jaqu.Table.JQDatabase;
import org.h2.jaqu.Validation;
import org.h2.test.TestBase;
import org.h2.test.jaqu.SupportedTypes.SupportedTypes2;

public class ModelsTest extends TestBase {

    /**
     * This object represents a database (actually a connection to the database).
     */
//## Java 1.5 begin ##
    Db db;
//## Java 1.5 end ##

    /**
     * This method is called when executing this application from the command
     * line.
     *
     * @param args the command line parameters
     */
    public static void main(String... args) {
        new ModelsTest().test();
    }

    public void test() {
//## Java 1.5 begin ##
        db = Db.open("jdbc:h2:mem:", "sa", "sa");
        db.insertAll(Product.getList());
        db.insertAll(ProductAnnotationOnly.getList());
        db.insertAll(ProductMixedAnnotation.getList());
        testValidateModels();
        testSupportedTypes();
        testModelGeneration();
        testDatabaseUpgrade();
        testTableUpgrade();
        db.close();
//## Java 1.5 end ##
    }
    
    private void testValidateModels() {
        boolean verbose = false;
        DbInspector inspector = new DbInspector(db);
        validateModel(inspector, new Product(), verbose);
        validateModel(inspector, new ProductAnnotationOnly(), verbose);
        validateModel(inspector, new ProductMixedAnnotation(), verbose);
    }
    
    private void validateModel(DbInspector inspector, Object o, boolean verbose) {        
        List<Validation> remarks = inspector.validateModel(o, false);
        if (verbose && remarks.size() > 0) {
            System.out.println("Validation Remarks for " + o.getClass().getName());
            System.out.println("=============================================");
            for (Validation remark:remarks)
                System.out.println(remark);
            System.out.println();
        }
        for (Validation remark:remarks)
            assertFalse(remark.toString(), remark.isError());
    }
    
    private void testSupportedTypes() {
        List<SupportedTypes> original = SupportedTypes.createList();
        db.insertAll(original);
        List<SupportedTypes> retrieved = db.from(SupportedTypes.SAMPLE).select();
        assertEquals(original.size(), retrieved.size());
        for (int i = 0; i < original.size(); i++) {
            SupportedTypes o = original.get(i);
            SupportedTypes r = retrieved.get(i);
            assertTrue(o.equivalentTo(r));
        }
    }
    
    private void testModelGeneration() {        
        DbInspector inspector = new DbInspector(db);
        List<String> models = inspector.generateModel(null, "SupportedTypes",
                "org.h2.test.jaqu", true, true);
        assertEquals(1, models.size());
        // a poor test, but a start
        assertEquals(1364, models.get(0).length());
    }
    
    private void testDatabaseUpgrade() {
        Db db = Db.open("jdbc:h2:mem:", "sa", "sa");

        // Insert a Database version record
        db.insert(new DbVersion(1));
        
        TestDbUpgrader dbUpgrader = new TestDbUpgrader();
        db.setDbUpgrader(dbUpgrader);

        List<SupportedTypes> original = SupportedTypes.createList();
        db.insertAll(original);
        
        assertEquals(1, dbUpgrader.oldVersion.get());
        assertEquals(2, dbUpgrader.newVersion.get());
        db.close();
    }

    private void testTableUpgrade() {
        Db db = Db.open("jdbc:h2:mem:", "sa", "sa");
        
        // Insert first, this will create version record automatically
        List<SupportedTypes> original = SupportedTypes.createList();
        db.insertAll(original);

        // Reset the dbUpgrader (clears updatecheck cache)
        TestDbUpgrader dbUpgrader = new TestDbUpgrader();
        db.setDbUpgrader(dbUpgrader);
        
        SupportedTypes2 s2 = new SupportedTypes2();

        List<SupportedTypes2> types = db.from(s2).select();
        assertEquals(10, types.size());
        assertEquals(1, dbUpgrader.oldVersion.get());
        assertEquals(2, dbUpgrader.newVersion.get());
        db.close();
    }

    @JQDatabase(version=2)
    private class TestDbUpgrader implements DbUpgrader  {
        final AtomicInteger oldVersion = new AtomicInteger(0);
        final AtomicInteger newVersion = new AtomicInteger(0);

        @Override
        public boolean upgradeTable(Db db, String schema, String table, 
                int fromVersion, int toVersion) {
            // Sample DbUpgrader just claims success on upgrade request
            oldVersion.set(fromVersion);
            newVersion.set(toVersion);
            return true;
        }

        @Override
        public boolean upgradeDatabase(Db db, int fromVersion, int toVersion) {                
            // Sample DbUpgrader just claims success on upgrade request
            oldVersion.set(fromVersion);
            newVersion.set(toVersion);
            return true;
        }            
    };

}
