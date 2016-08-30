/*
 * Copyright (C) 2016 Toby Scholz 2016
 *
 * This program is free software: you can redistribute it and/or modify
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
package NepTune;

import java.net.*;

/**
 * Listen for connection requests from clients, and passes them on 
 * to ClientConnector for further processing
 */
public class ConnectionHandlerSingleton implements Runnable {
    
    private static ConnectionHandlerSingleton instance = null;
    ConfigReaderSingleton properties = ConfigReaderSingleton.getInstance();
    int _port;
    
    public static ConnectionHandlerSingleton getInstance()
    {
      if(instance==null)
      {
         instance = new ConnectionHandlerSingleton();
      }
      return instance;
    }
    
    ConnectionHandlerSingleton()
    {
        try
        {
            _port = Integer.parseInt(properties.getProperty("httpServerPort"));
        }
        catch(Exception e)
        {
            LOG.WARNING("Could not get port from config" + e.toString());
            _port = 7701;
        }
    }
    

    @Override
    public void run()
    {
        ServerSocket socket = null;
        try
        {
            socket = new ServerSocket(_port);
            while (true) 
            {
                LOG.INFO("Waiting for connections on port " + _port);
                try
                {
                    Socket sock = socket.accept();
                    ClientConnector connector = new ClientConnector( sock );
                    Thread thread = new Thread(connector);
                    LOG.INFO("New client connection");
                    thread.start();
                }
                catch(Exception e)
                {
                    LOG.ERROR("Failed to create connection " + e.toString());
                }
            }
        }
        catch (Exception e)
        {
            LOG.ERROR(e.toString());
        }
        finally
        {
            try
            {
                socket.close();
            }
            catch(Exception e)
            {
                LOG.ERROR(e.toString());
            }
        }

    }
}
