/*
 * Copyright (c) 2002-2016 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.coreedge.raft.replication.token;

import org.neo4j.coreedge.raft.replication.Replicator;
import org.neo4j.kernel.impl.store.id.IdGeneratorFactory;
import org.neo4j.kernel.impl.store.id.IdType;
import org.neo4j.kernel.api.txstate.TransactionState;
import org.neo4j.kernel.impl.core.PropertyKeyTokenHolder;
import org.neo4j.kernel.impl.store.record.PropertyKeyTokenRecord;
import org.neo4j.kernel.impl.util.Dependencies;
import org.neo4j.logging.LogProvider;
import org.neo4j.storageengine.api.Token;

public class ReplicatedPropertyKeyTokenHolder extends ReplicatedTokenHolder<Token,PropertyKeyTokenRecord> implements PropertyKeyTokenHolder
{
    public ReplicatedPropertyKeyTokenHolder( Replicator replicator, IdGeneratorFactory idGeneratorFactory,
                                             Dependencies dependencies, long timeoutMillis, LogProvider logProvider )
    {
        super( replicator, idGeneratorFactory, IdType.PROPERTY_KEY_TOKEN,
                dependencies, new Token.Factory(), TokenType.PROPERTY, timeoutMillis, logProvider );
    }

    @Override
    protected String tokenType()
    {
        return "PropertyKey";
    }

    @Override
    protected void createToken( TransactionState txState, String tokenName, int tokenId )
    {
        txState.propertyKeyDoCreateForName( tokenName, tokenId );
    }

}
