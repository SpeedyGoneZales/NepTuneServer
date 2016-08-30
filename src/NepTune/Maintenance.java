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

import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import java.util.ArrayList;
import java.util.Date;
import org.bson.Document;
import static NepTune.DataParser.properties;

/**
 * The Maintenance class has two purposes:
 * 1) It checks if the files available in the samba share match those in 
 * the index, and adds / removes as appropriate.
 * 2) It clears out the Neptune temp directory.
 */
public class Maintenance implements Runnable {
    
    /**
     * The possible cases:
     * 1) File is deleted from SMB, still exists in datastore
     * 2) File was added to SMB, does not yet exist in datastore
     * 3) Existing file was modified
     */
    
    SambaConnector samba = new SambaConnector(
            properties.getProperty("sambaUser"), 
            properties.getProperty("sambaPassword"), 
            properties.getProperty("sambaServer"),
            properties.getProperty("sambaPath"));
    
    DataStoreSingleton ds = DataStoreSingleton.getInstance();
    MongoConnectorSingleton mongo = MongoConnectorSingleton.getInstance();
    
    /**
     * Case 1). If removed from file share, also remove from live datastore,
     * and the database
     */
    void checkIfDBEntryNoLongerExistsOnSamba()
    {
        Map<String,Document> existingEntries = ds.getMap();
        List<String> docsToRemove = new ArrayList<>();
        for ( Map.Entry<String, Document> entry : existingEntries.entrySet() )
        {
            String file = (String) entry.getValue().get("fileName");
            String path = (String) entry.getValue().get("filePath");
            String type = (String) entry.getValue().get("fileType");
            String filePath = path + file + "." + type;
            try {
                if (!samba.getFile(filePath).exists())
                {
                    mongo.removeEntry("files", file, path, type);
                    docsToRemove.add(entry.getKey()); 
                }
            } 
            catch (SmbException ex) 
            {
                LOG.ERROR("Exception while trying to access " + path + " (" + entry.getKey() + ")");
            }
        }
        removeItemsFromDataStore(docsToRemove);
    }
    
    /**
     * Helper method to above, remove from live data store.
     * @param docs 
     */
    void removeItemsFromDataStore(List<String> docs)
    {
        for( String entry : docs)
        {
            ds.removeItem(entry);
            LOG.INFO("Maintenance removed id " + entry + " from document store");
        }
    }
    
    /**
     * Cases 2) and 3).
     * If a new file was added to the samba share, also add it to the database 
     * and live data store.
     * If an existing item was modified, remove it from the database and the 
     * live datastore, and re-introduce as a new item.
     * @throws NoSuchAlgorithmException
     * @throws SmbException 
     */
    void addEntryIfWasAddedToSambaOrIfExistingEntryMatchesFile() throws NoSuchAlgorithmException, SmbException 
    {
        Map<String,Document> existingEntries = ds.getMap();
        for (String ii : samba.populatePathSet() )
        {
            boolean found = false;
            String entryToRemove = null;
            String path = null;
            String file = null;
            String type = null;
            for ( Map.Entry<String, Document> entry : existingEntries.entrySet() )
            {
                path = (String) entry.getValue().get("filePath");
                file = (String) entry.getValue().get("fileName");
                type = (String) entry.getValue().get("fileType");
                String filePath = path + file + "." + type;
                if (ii.equals(filePath)) // we found the file in the db also in the datastore. Has it been modified?
                {
                    try 
                    {
                        found = compareFiles(entry.getValue().get("hash").toString(), ii); // if found is false, it has been modified.
                    }
                    catch (Exception e)
                    {
                        LOG.ERROR("Exception comparing file " + e.toString());
                        found = false;
                    }
                    entryToRemove = entry.getKey();
                    break;
                }
            }
            if (!found)
                /* Because mongodb creates the unique id of an entry, it is
                necessary to commit the new entry to db first, and then query the 
                db to get the object id in order to add it to the live data store.
                */
            {
                if (entryToRemove != null)
                // entry already exists, but has been modified.    
                {
                    ds.removeItem(entryToRemove); // remove from live data store
                    mongo.removeEntry("files", file, path, type); // remove from db
                }
                DataParser dp = new DataParser();
                Map<String, String> fileDetails = dp.splitPath(ii);
                Document doc = dp.parseSingleDoc(ii); // parse the new document
                mongo.addRecord(doc); // add it to db
                doc = mongo.getDocument("files", fileDetails.get("name"), fileDetails.get("path"), fileDetails.get("type")); // retrieve it from db
                String id = doc.get("_id").toString(); 
                ds.addItem(id, doc); // add it tolive data store
                LOG.INFO("Maintenance added document " + doc.toString());
            }
        }
    }
    
    /**
     * Compares two files based on a hash generated from its length and
     * last modified time.
     * 
     * @param docHash Hash of existing entry
     * @param filePath Path to file for hashing
     * @return True if both hashes match
     * @throws SmbException 
     */
    private boolean compareFiles(String docHash, String filePath) throws SmbException
    {
        FileHasher fh = new FileHasher();
        String fileHash = null;
        SmbFile file = null;
        try
        {
            file = new SmbFile(filePath);
        }
        catch(Exception e)
        {
            LOG.ERROR("Could not access file " + filePath);
            return false;
        }
        fileHash = fh.generateHash(file);
        return docHash.equals(fileHash);
    }
    
    
    /**
     * HLS creates stream file in the temp directory. We want to clear these
     * every now and then, as else, we will be running out of disk space.
     * Four hours seems a good time.
     */
    void clearTempDirectory(int olderThan)
    {
        long date = new Date().getTime();
        File tempFolder = new File(properties.getTempDirectory());
        File[] files = tempFolder.listFiles();
        
        for (File file : files)
        {
            if ((date - file.lastModified() > olderThan )) // older than four hours
                    {
                        file.delete();
                    }
        }
    }

    
    /**
     * Run all of the above methods in sequence, according to maintenanceInterval
     * specified in the config.
     */
    @Override
    public void run() {
        while(true)
        {
            LOG.INFO("Starting Maintenance");

            try
            {
                addEntryIfWasAddedToSambaOrIfExistingEntryMatchesFile();
            }
            catch(Exception e)
            {
                LOG.ERROR("Error during file maintenance");
            }
            checkIfDBEntryNoLongerExistsOnSamba(); // Must come after addEntry... 
            clearTempDirectory((14400)*1000); // older than four hours
            LOG.INFO("Maintenance completed");
            try 
            {
                Thread.sleep(Integer.parseUnsignedInt(properties.getProperty("maintenanceInterval")) * 60 * 1000 );
            } catch (InterruptedException ex) 
            {
                LOG.ERROR("Error doing nothing in particular");
            }
        }
    }
    
} // end class
