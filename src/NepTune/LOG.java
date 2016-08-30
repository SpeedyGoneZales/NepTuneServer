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


import java.net.URL;
import java.net.URLClassLoader;

import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 *
 * @author Toby Scholz
 * Project: The Open University, TM470, 2016, under supervision of 
 * Charly Lowndes
 * 
 */

/**
 * Writes logs according to the level configured in /etc/nep-tune.properties.
 * debug = 0
 * info = 1
 * warning = 2 (default)
 * error = 3
 * critical = 4
 * Everything >= logLevel set will be written to the logs.
 * 
 */
public class LOG {

    public LOG() {
    }
    
    enum LogLevel {
        debug,
        info,
        warning,
        error,
        critical
    }
    
    static DateFormat logDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ENGLISH);
    static ConfigReaderSingleton properties = ConfigReaderSingleton.getInstance();
    private static final int logLevel = LOG.properties.getLogLevel();
     
    /**
     * The application cannot continue when a critical occurs (e.g. samba
     * connection fails).
     * It will write the log to the log file, close the database connection,
     * and exit.
     * @param string Message to be written to the log
     */
    public static void CRITICAL(String string) {
        if ( LOG.logLevel <= LOG.LogLevel.critical.ordinal() ) {
            System.out.println(logDate.format(NOW()) + "  <<<critical>>>  " +  new Exception().getStackTrace()[1].getClassName() + ": " + string.toUpperCase());
            MongoConnectorSingleton db = MongoConnectorSingleton.getInstance();
            db.close(); // just in case
            System.out.println(logDate.format(NOW()) + "  <<<critical>>>  === Shutting down application!" );
            System.exit(-1); // automatically executes ShutDown Hook
        }
    }
      
    /**
     * The application's normal modus operandi is impacted when an error
     * occurs, but it will continue to run (for example, a read error 
     * from a particular file). 
     * @param string Message to be written to the log
     */
    public static void ERROR(String string) {
        if ( LOG.logLevel <= LOG.LogLevel.error.ordinal() ) {
            System.out.println(logDate.format(NOW()) + "  <<<error>>>  " +  new Exception().getStackTrace()[1].getClassName() + ": " + string.toUpperCase());
        }
    }
    
     /**
      * A warning indicates a circumstance, which is undesired, but expected
      * during the normal operation of the application (for example,
      * the memory used exceeds pre-defined threshold).
     * @param string Message to be written to the log
     */
    public static void WARNING(String string) {
        if ( LOG.logLevel <= LOG.LogLevel.warning.ordinal() ) {
            System.out.println(logDate.format(NOW()) + "  <<<warning>>>  " + string);
        }
    }
    
    /**
     * Logs ordinary events during normal running of the application
     * (e.g. successful connection to the database)
     * @param string Message to be written to the log
     */
    public static void INFO(String string) {
        if ( LOG.logLevel <= LOG.LogLevel.info.ordinal() ) {
            System.out.println(logDate.format(NOW()) + "  <<<info>>>  " + string);
        }
    }
    
    /**
     * Logs information that can be useful for troubleshooting, but is 
     * normally undesired (e.g. a full representation of the database).
     * It will also prepend the calling method to the log entry.
     * Doing this is computationally expensive, and may impact smooth operation.
     * @param string Message to be written to the log
     */
    public static void DEBUG(String string) {
        if ( LOG.logLevel <= LOG.LogLevel.debug.ordinal() ) {
            System.out.println(logDate.format(NOW()) + "  <<<debug>>>  " + new Exception().getStackTrace()[1].getClassName() + ": " + string);
        }
    }
    
    /**
     * Used to troubleshoot classpath issues only.
     * @param string Message to be written to the log
     */
    public static void CLASSPATH(String string) {
        System.out.println(logDate.format(NOW()) + "  <<<classpath>>>  " + printClasspath());
    }
    
    
    /**
     * Returns the current date and time in a standardised format.
     * @return Date object, including time accurate to ms.
     */
    public static Date NOW()
    {
       	Date date = new Date();     
        return date;
    }
    
    
    
    /**
     * queries all classpaths that have been supplied as part of the 
     * configuration, according to Oracle (2016c).
     * 
     * @return The classpath as a string.
     */
    private static String printClasspath() {
        
        StringBuilder sb = new StringBuilder();
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        URL[] urls = ((URLClassLoader)cl).getURLs();

        for(URL url: urls){
        	sb.append(url.getFile());
                sb.append(System.getProperty("line.separator"));
        }
        String s = sb.toString();
        return s;
    }
    
}
