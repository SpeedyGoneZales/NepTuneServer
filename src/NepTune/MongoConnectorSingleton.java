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

import com.mongodb.MongoClient;
import com.mongodb.client.MongoDatabase;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import org.bson.Document;
import com.mongodb.client.FindIterable;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 * Instantiates a connection to the MongoDB database used to 
 * store the file metadata. 
 * https://docs.mongodb.com/getting-started/java/
 * 
 */

public class MongoConnectorSingleton {
    
    MongoClient mongoClient;
    MongoDatabase db;
    
    private static MongoConnectorSingleton instance = null;
  
    public static MongoConnectorSingleton getInstance()
    {
      if(instance==null)
      {
         instance = new MongoConnectorSingleton();
      }
      return instance;
    }
    
    
    /**
     * Opens a connection to the local MongoDB database.
     */
    void createMongoConnection() 
    {
        try 
        {
            mongoClient = new MongoClient( "127.0.0.1" ); 
            db = mongoClient.getDatabase("neptune");
            LOG.INFO("Successfully connected to databse: " + db.getName());
        }
        catch (Exception e)
        {                  
            LOG.CRITICAL("Connecttion failed " + e.toString());
        }
    }
    
    
    /**
     * Iterates of the available collections in the specified database.
     * @return 
     */
    Set<String> listCollections() 
    {
        Set<String> collections = new HashSet<>();
        LOG.DEBUG("Collections are: "); 
        for (String collection : db.listCollectionNames() ) {
            
            LOG.INFO(collection + System.lineSeparator());
            collections.add(collection);
        }
        return collections;
    }
    
    /**
     * Fetches the document collection from the database.
     * @param collectionName Collection to query
     * @return The collection
     */
    public FindIterable<Document> readCollection(String collectionName)
    {
        LOG.DEBUG("There are " + db.getCollection(collectionName).count() + " documents in collection " + collectionName);
        return db.getCollection(collectionName).find();
    }
    
    public Document getDocument(String collectionName, String fileName, String filePath, String fileType)
    {
        FindIterable docs = 
          db.getCollection(collectionName).find(and (eq("fileName", fileName), eq("filePath", filePath), eq("fileType", fileType)) );
        return (Document) docs.first(); //Assuming here that the above result returns a single entry
    }
    
    public void addRecord(Document doc)
    {
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH);
        try
        {
            db.getCollection("files").insertOne(doc);
        }
        catch(Exception e)
        {
            LOG.CRITICAL("Mongo exception while inserting documents " + e.toString());
        }
    }
    
    /**
     * Adds a list of records to the database. 
     * Should only be used in '--init' mode, or by the Maintenance class.
     * @param listOfDocuments The list of Documents to add to the database.
     */
    public void addRecords(List<Document> listOfDocuments)
    {   
        // unique ID _id is automatically created.
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH);
        
        try
        {
            db.getCollection("files").insertMany(listOfDocuments);
        }
        catch(Exception e)
        {
            LOG.CRITICAL("Mongo exception while inserting documents " + e.toString());
        }
        
    }
    
    /**
     * Deletes the collection from the database, and re-creates it (empty).
     * Should be used in '--init' mode only.
     */
    public void clearCollection()
    {
        db.getCollection("files").drop();
        LOG.INFO("Collection \"files\" dropped");
        if ( !listCollections().contains("files"))
        {
            db.createCollection("files");
            LOG.INFO("New, empty collection \"files\" created");
        }
        else
        {
            LOG.ERROR("Error clearing collection \"files\"");
        }
    }
    
    /**
     * Removes a single entry from the db
     * @param collectionName: always "files" for NepTune
     * @param fileName
     * @param filePath
     * @param fileType 
     */
    void removeEntry(String collectionName, String fileName, String filePath, String fileType)
    {
        db.getCollection("files").deleteOne(this.getDocument(collectionName, fileName, filePath, fileType));
    }
    
    /**
     * Closes the connection to the database.
     */
    public void close() 
    {
        mongoClient.close();
    }
}
