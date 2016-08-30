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

/**
 *
 * @author Toby Scholz
 * Project: The OU, TM470, 2016, under supervision of 
 * Charly Lowndes
 * 
 */


public class NepTune {

    /**
     * @param args the command line arguments
     */
    
    static String configFilePath = "/etc/nep-tune.properties";
    
    public static void main(String[] args) {

        System.out.println("Starting application"); // We don't know where the log file is yet. To console.
        ConfigReaderSingleton crs;
        for (int ii = 0; ii < args.length; ii++)
        {
            if (args[ii].equals("-c") && args[ii+1] != null)
            {
                configFilePath = args[ii+1];
            }
        }
        crs = ConfigReaderSingleton.getInstance(); // Need to instantiate Singleton with config file
        System.out.println("Reading config file from " + configFilePath);
        LOG.INFO("Connecting to database");
        MongoConnectorSingleton localdb = MongoConnectorSingleton.getInstance();
        localdb.createMongoConnection();
        DataStoreSingleton ds = DataStoreSingleton.getInstance();
        DataParser dp = new DataParser();
        ConnectionHandlerSingleton ch = ConnectionHandlerSingleton.getInstance();
       
        //Things to do when kill signal is received
        Runtime.getRuntime().addShutdownHook(new Thread() 
        {
            @Override
            public void run() 
            {
                try 
                {
                    Thread.sleep(0x1f4);
                    LOG.INFO("Shutting down ...");
                    Maintenance maint = new Maintenance();
                    maint.clearTempDirectory(1); //Clear temp dir
                    localdb.close(); // Close db conncetion
                } 
                catch (InterruptedException e) 
                {
                    LOG.ERROR(e.toString());
                }
            }
        }); 
        

                
        try
        {
            /**
             * running in --init mode
             * Clears the database, and re-creates the index from scratch
             */
            if ( args[0] != null && args[0].equals("--init"))
            {
                localdb.clearCollection();
                dp.storeData();
                localdb.close();
            }
            /**
             * running in --daemon mode.
             * Reads the index from the database, and makes it 
             * available to the client.
             */
            else if ( args[0] != null && args[0].equals("--daemon"))
            {
                dp.readDb();
                Thread maint = new Thread(new Maintenance());
                maint.setPriority(Thread.MIN_PRIORITY);
                maint.start();
                //ds.printDocs(); // for debugging only
                ch.run();
            }
        }
        catch(Exception e)
        {
            System.out.println("Error running application: " + e.toString());
            System.out.println(System.lineSeparator() + "Usage: " + System.lineSeparator()
                    + "Use --init to clear the database, and re-create the "
                    + "index from scratch." + System.lineSeparator()
                    + "The program will exit upon completion" + System.lineSeparator()
                    + "Use --daemon to read data from the database, and " + System.lineSeparator()
                    + "keep running to offer clients the ability to connect" + System.lineSeparator()
                    + "--daemon also has a maintenance utility running, which" + System.lineSeparator()
                    + "monitors for file changes and updates the database in" + System.lineSeparator()
                    + "intervals specified in the config file" + System.lineSeparator()
                    + "Use -c to specify a config file path (e.g. -c /etc/neptune.properties)" + System.lineSeparator()
            );
        }
        finally
        {
            //System.exit(0);
        }
    }
    static void createConfigReaderSingleton()
    {
        ConfigReaderSingleton.createInstance(configFilePath);

    }
}
