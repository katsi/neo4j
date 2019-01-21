/*
 * Copyright (c) 2002-2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.graphdb;

import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import org.neo4j.collection.PrimitiveLongResourceIterator;
import org.neo4j.index.internal.gbptree.RecoveryCleanupWorkCollector;
import org.neo4j.io.layout.DatabaseLayout;
import org.neo4j.io.pagecache.IOLimiter;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.api.labelscan.LabelScanReader;
import org.neo4j.kernel.api.labelscan.LabelScanStore;
import org.neo4j.kernel.api.labelscan.LabelScanWriter;
import org.neo4j.kernel.impl.core.ThreadToStatementContextBridge;
import org.neo4j.kernel.impl.index.labelscan.NativeLabelScanStoreTest;
import org.neo4j.storageengine.api.NodeLabelUpdate;
import org.neo4j.test.rule.DbmsRule;
import org.neo4j.test.rule.EmbeddedDbmsRule;
import org.neo4j.test.rule.RandomRule;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.neo4j.collection.PrimitiveLongCollections.closingAsArray;

public class NativeLabelScanStoreStartupIT
{
    private static final Label LABEL = Label.label( "testLabel" );

    @Rule
    public final DbmsRule dbRule = new EmbeddedDbmsRule();
    @Rule
    public final RandomRule random = new RandomRule();

    private int labelId;

    @Test
    public void scanStoreStartWithoutExistentIndex() throws Throwable
    {
        LabelScanStore labelScanStore = getLabelScanStore();
        RecoveryCleanupWorkCollector workCollector = getGroupingRecoveryCleanupWorkCollector();
        labelScanStore.shutdown();
        workCollector.shutdown();

        deleteLabelScanStoreFiles( dbRule.databaseLayout() );

        workCollector.init();
        labelScanStore.init();
        workCollector.start();
        labelScanStore.start();

        checkLabelScanStoreAccessible( labelScanStore );
    }

    @Test
    public void scanStoreRecreateCorruptedIndexOnStartup() throws Throwable
    {
        LabelScanStore labelScanStore = getLabelScanStore();
        RecoveryCleanupWorkCollector workCollector = getGroupingRecoveryCleanupWorkCollector();

        createTestNode();
        long[] labels = readNodesForLabel( labelScanStore );
        assertEquals( "Label scan store see 1 label for node", 1, labels.length );
        labelScanStore.force( IOLimiter.UNLIMITED );
        labelScanStore.shutdown();
        workCollector.shutdown();

        corruptLabelScanStoreFiles( dbRule.databaseLayout() );

        workCollector.init();
        labelScanStore.init();
        workCollector.start();
        labelScanStore.start();

        long[] rebuildLabels = readNodesForLabel( labelScanStore );
        assertArrayEquals( "Store should rebuild corrupted index", labels, rebuildLabels );
    }

    private LabelScanStore getLabelScanStore()
    {
        return getDependency( LabelScanStore.class );
    }

    private RecoveryCleanupWorkCollector getGroupingRecoveryCleanupWorkCollector()
    {
        return dbRule.getDependencyResolver().resolveDependency( RecoveryCleanupWorkCollector.class );
    }

    private <T> T getDependency( Class<T> clazz )
    {
        return dbRule.getDependencyResolver().resolveDependency( clazz );
    }

    private long[] readNodesForLabel( LabelScanStore labelScanStore )
    {
        return closingAsArray( labelScanStore.newReader().nodesWithLabel( labelId ) );
    }

    private Node createTestNode()
    {
        Node node;
        try ( Transaction transaction = dbRule.beginTx() )
        {
            node = dbRule.createNode( LABEL);
             KernelTransaction ktx = dbRule.getDependencyResolver()
                    .resolveDependency( ThreadToStatementContextBridge.class )
                    .getKernelTransactionBoundToThisThread( true );
                labelId = ktx.tokenRead().nodeLabel( LABEL.name() );
            transaction.success();
        }
        return node;
    }

    private void scrambleFile( File file ) throws IOException
    {
        NativeLabelScanStoreTest.scrambleFile( random.random(), file );
    }

    private static File storeFile( DatabaseLayout databaseLayout )
    {
        return databaseLayout.labelScanStore();
    }

    private void corruptLabelScanStoreFiles( DatabaseLayout databaseLayout ) throws IOException
    {
        scrambleFile( storeFile( databaseLayout ) );
    }

    private static void deleteLabelScanStoreFiles( DatabaseLayout databaseLayout )
    {
        assertTrue( storeFile( databaseLayout ).delete() );
    }

    private static void checkLabelScanStoreAccessible( LabelScanStore labelScanStore ) throws IOException
    {
        int labelId = 1;
        try ( LabelScanWriter labelScanWriter = labelScanStore.newWriter() )
        {
            labelScanWriter.write( NodeLabelUpdate.labelChanges( 1, new long[]{}, new long[]{labelId} ) );
        }
        LabelScanReader labelScanReader = labelScanStore.newReader();
        try ( PrimitiveLongResourceIterator iterator = labelScanReader.nodesWithLabel( labelId ) )
        {
            assertEquals( 1, iterator.next() );
        }
    }
}