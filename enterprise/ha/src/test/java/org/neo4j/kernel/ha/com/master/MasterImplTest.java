/**
 * Copyright (c) 2002-2013 "Neo Technology,"
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
package org.neo4j.kernel.ha.com.master;

import java.util.HashMap;
import java.util.Map;

import javax.transaction.SystemException;
import javax.transaction.Transaction;

import org.junit.Test;
import org.neo4j.com.RequestContext;
import org.neo4j.graphdb.TransactionFailureException;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.ha.HaSettings;
import org.neo4j.kernel.impl.util.Monitors;
import org.neo4j.kernel.impl.util.StringLogger;
import org.neo4j.kernel.logging.DevNullLoggingService;
import org.neo4j.kernel.logging.Logging;

import static junit.framework.Assert.fail;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MasterImplTest
{
    private final Monitors monitors = new Monitors( StringLogger.DEV_NULL);

    @Test
    public void givenStartedAndInaccessibleWhenInitializeTxThenThrowException() throws Throwable
    {
        // Given
        MasterImpl.SPI spi = mock( MasterImpl.SPI.class );
        Logging logging = new DevNullLoggingService();
        Map<String, String> params = new HashMap<String, String>();
        params.put( HaSettings.lock_read_timeout.name(), "20s" );
        Config config = new Config( params, HaSettings.class );

        when( spi.isAccessible() ).thenReturn( false );

        MasterImpl instance = new MasterImpl( spi, monitors , logging, config );
        instance.start();

        // When
        try
        {
            instance.initializeTx( new RequestContext( 0, 1, 2, new RequestContext.Tx[0], 1, 0 ) );
            fail();
        }
        catch ( TransactionFailureException e )
        {
            // Ok
        }
    }

    @Test
    public void givenStartedAndAccessibleWhenInitializeTxThenSucceeds() throws Throwable
    {
        // Given
        MasterImpl.SPI spi = mock( MasterImpl.SPI.class );
        Logging logging = new DevNullLoggingService();
        Map<String, String> params = new HashMap<String, String>();
        params.put( HaSettings.lock_read_timeout.name(), "20s" );
        Config config = new Config( params, HaSettings.class );

        when( spi.isAccessible() ).thenReturn( true );
        when( spi.beginTx() ).thenReturn( mock( Transaction.class ) );

        MasterImpl instance = new MasterImpl( spi, monitors, logging, config );
        instance.start();

        // When
        try
        {
            instance.initializeTx( new RequestContext( 0, 1, 2, new RequestContext.Tx[0], 1, 0 ) );
        }
        catch ( Exception e )
        {
            fail( e.getMessage() );
        }

    }

    @Test
    public void failingToStartTxShouldNotLeadToNPE() throws Throwable
    {
        // Given
        MasterImpl.SPI spi = mock( MasterImpl.SPI.class );
        Config config = new Config( new HashMap<String, String>(), HaSettings.class );

        when( spi.isAccessible() ).thenReturn( true );
        when( spi.beginTx() ).thenThrow( new SystemException("Nope") );

        MasterImpl instance = new MasterImpl( spi, monitors, new DevNullLoggingService(), config );
        instance.start();

        // When
        try
        {
            instance.initializeTx( new RequestContext( 0, 1, 2, new RequestContext.Tx[0], 1, 0 ) );
            fail("Should have failed.");
        }
        catch ( Exception e )
        {
            // Then
            assertThat(e.getCause(), instanceOf( SystemException.class ));
            assertThat(e.getCause().getMessage(), equalTo( "Nope" ));
        }

    }
}
