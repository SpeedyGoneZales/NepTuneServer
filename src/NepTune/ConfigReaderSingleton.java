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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Properties;

/**
 * The ConfigReaderSingleton utility class reads properties from 
 * /etc/nep-tune.config (or file specified with -c option)
 * into static variables in this class,
 * which can be requested package-wide
 */
public class ConfigReaderSingleton {
    
    final String fileName; //name of the file to read (e.g. /etc/nep-tune.properties)
    final Properties properties; 
    final int logLevel; // defines the behaviour of the LOG class
    File file; 
    BufferedReader reader = null;
    String tempDirectory = "/tmp/neptune/";
    
    static ConfigReaderSingleton instance = null;
    
    /**
    * Fetches the Singleton instance of ConfigReaderSingleton, or 
    * creates one if needed. 
    */
    static ConfigReaderSingleton getInstance()
    {
      if(instance==null)
      {
          NepTune.createConfigReaderSingleton();
      }
      return instance;
    }
    

    /**
    * Only Main should use this method
    */
    static ConfigReaderSingleton createInstance(String configFilePath)
    {
      if(instance==null)
      {
         instance = new ConfigReaderSingleton(configFilePath);
      }
      return instance;
    }
    
    
    ConfigReaderSingleton(String configFilePath)
    {
        
        fileName = configFilePath;
        try 
        {
            reader = new BufferedReader(new FileReader(new File(fileName)));
        } catch (FileNotFoundException ex) 
        {
            LOG.CRITICAL("Config file not found: " + fileName + ": " + ex.toString());
        }
        properties = new Properties();

        try
        {
            properties.load(reader);
        }
        catch(Exception e)
        {
            LOG.CRITICAL("Config file not found in " + fileName + " " + e.toString());
        }
        finally
        {
            try 
            {
                reader.close();
            }
            catch(Exception e)
            {
                LOG.ERROR("Trouble with config file: " + e.toString());
            }
        }
        logLevel = setLogLevel();
        tempDirectory = setTempDirectory();
    }

    /**
    * Returns a String with the property recorded in the config file 
    * (/etc/nep-tune.properties by default).
    * 
    * @param name The name (key) of the property to be fetched
    * @return the value of the property with above name
    */
    public String getProperty(String name)
    {
        String property = null;
        try 
        {
            property = properties.getProperty(name);
        } catch (Exception e) 
        {
            LOG.ERROR("Error getting property " + name + ": " + e.toString());
        }
        return property;
    }
    
    
    /**
     * Reads the logLevel property, and returns it as a string, so it can 
     * be used to compare it to an enum
     * @return Log level as int
     */
    private int setLogLevel()
    {
        switch (properties.getProperty("logLevel")) {
            case "debug":
                return 0;
            case "info":
                return 1;
            case "error":
                return 3;
            case "critical":
                return 4;
            default:
                return 2;
        }
    }
    
    /**
     * @return The log level configured in /etc/properties as an int.
     * debug = 0
     * info = 1
     * warning = 2 (default)
     * error = 3
     * critical = 4
     * Everything >= logLevel set will be written to the logs.
     */
    public int getLogLevel()
    {
        return setLogLevel();
    }
    
    private String setTempDirectory()
    {
        if ( properties.getProperty("tempDirectory") != null && !properties.getProperty("tempDirectory").isEmpty() )
        {
            tempDirectory = properties.getProperty("tempDirectory");
        }
        else
        {
            String temp = System.getProperty("java.io.tmpDir");
            //TODO: Fix Java to return proper temp directory.
            //tempDirectory = temp + "/neptune"; // above returns null for some reason. Java...
            File dir = new File(tempDirectory);
            if ( !dir.exists() )
            {
                if ( !dir.mkdirs() )
                {
                    LOG.CRITICAL("Could not create temp directory");
                }
            }
        }       
        return tempDirectory;
    }
    
    /**
     * @return  Canonical path to temp directory
     */
    public String getTempDirectory()
    {
        return tempDirectory;
    }
}