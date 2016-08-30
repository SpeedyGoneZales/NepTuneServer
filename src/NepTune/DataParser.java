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

import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import org.bson.Document;
import com.mongodb.Block;
import com.mongodb.client.FindIterable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

/**
 * The Data Parser class inspects file passed to it for metadata, and 
 * writes the metadata to the database. 
 * It should only be called when the program runs in 
 * '--init' or mode, or from the Maintenance class.
 * 
 */

public class DataParser {
    
    static ConfigReaderSingleton properties = ConfigReaderSingleton.getInstance();
    Set<String> pathSet = new HashSet<>();
    DataStoreSingleton dataStore = DataStoreSingleton.getInstance();
    private final MongoConnectorSingleton db = MongoConnectorSingleton.getInstance();
    private List<Document> docList = new ArrayList<>();
    
    
    SambaConnector samba = new SambaConnector(
                properties.getProperty("sambaUser"), 
                properties.getProperty("sambaPassword"), 
                properties.getProperty("sambaServer"),
                properties.getProperty("sambaPath"));
    
    /**
     * Takes a single Smb File, parses its metadata, and returns a document.
     * Add logic for custom file types here
     * @param filePath: File path to file on samba share 
     *      (e.g. smb://192.168.1.2/Collection/MyFile.txt)
     * @return The parsed document
     */
    public Document parseSingleDoc(String filePath)
    {
        Document doc = new Document();
        Map fileDetails = splitPath(filePath);   

        // Check file extension for custom file type,
        if (false /* add custom file type here */ )
        {

        }

        else
        {
            MetaDataParser_tikaGeneric parser = new MetaDataParser_tikaGeneric(); // This uses Apache Tika
            parser.decodeFile(filePath, doc);
            try
            {
                doc = addFileDetails(fileDetails, doc, filePath);
            }
            catch(Exception e)
            {
                LOG.ERROR("Error writing details for file " + filePath);
            }
        }
        LOG.INFO("Just added to list: " + doc.toString() + System.lineSeparator());
        return doc;
    }
    
    /**
     * Retrieves the the list of files from the specified samba share, 
     * and writes it to the DataStoreSingleton object
     * 
     */
    void storeData() {
        for (String ii : samba.populatePathSet() )
        {
            docList.add(parseSingleDoc(ii));
        } 
        writeToDb();
        LOG.DEBUG("Just added to database were: " + docList.size() + " documents");
    }
    
    
    
    /**
     * Add details like file path
     * @param file Map of file details
     * @param doc Document to be added to store
     * @return 
     */
    private Document addFileDetails(Map fileDetails, Document doc, String filePath) throws SmbException
    {
        FileHasher fh = new FileHasher();
        SmbFile file = null;
        try
        {
            file = new SmbFile(filePath);
        }
        catch(Exception e)
        {
            LOG.ERROR("Error accessing file " + filePath);
        }
        if (file != null)
        {
            
            doc.append("fileName", fileDetails.get("name"));
            doc.append("filePath", fileDetails.get("path"));
            doc.append("fileType", fileDetails.get("type"));
            doc.append("hash", fh.generateHash(file));
        }
        else
        {
            LOG.ERROR("Error writing details for file " + filePath);
        }
        return doc;
    }
    
    
    /**
     * Inspects the path passed to it (e.g. smb://myshare/myFile.type) and
     * extracts the directory, file name, and type.
     * @param path The full path of the file to inspect
     * @return The Map containing directory, file name, and file type
     */
    public Map<String, String> splitPath(String path)
    {
        Map<String, String> fileDetails = new HashMap<>();
        
        fileDetails.put("path", path.substring(0, path.lastIndexOf('/') +1));                           // file path
        fileDetails.put("name", path.substring(path.lastIndexOf('/') + 1, (path.lastIndexOf('.') )) );  // file name
        fileDetails.put("type", path.substring(path.lastIndexOf('.') +1 ));                             // file type
        
        return fileDetails;        
    }
    
    /**
     * As opposed to storeData(), this method retrieves data previously stored
     * in the database, and writes it to the DataStoreSingleton.
     * It should only be used when the program is run in '--daemon' mode.
     */
    public void readDb()
    {
        FindIterable<Document> iterable = db.readCollection("files");           
        iterable.forEach(new Block<Document>() 
        {
            @Override
            public void apply(final Document document) 
            {
                dataStore.addItem(document.get("_id").toString(), document);
            }
        }
        );
        LOG.INFO(dataStore.getSize() + " documents added to dataStore");
    }
    
    /**
     * Transfers data from the DataStoreSingleton to the database.
     * Should only be used in '--init', or '--update' mode.
     */
    
    public void writeToDb()
    {
        db.addRecords(docList);
    }
} // end class
